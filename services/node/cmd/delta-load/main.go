package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type configuration struct {
	target      string
	connections int
	timeout     time.Duration
	hold        time.Duration
	ramp        time.Duration
}

type connectionResult struct {
	latency time.Duration
	err     error
}

func main() {
	configuration := parseFlags()
	if err := run(context.Background(), configuration); err != nil {
		fmt.Fprintln(os.Stderr, "delta-load:", err)
		os.Exit(1)
	}
}

func parseFlags() configuration {
	var configuration configuration
	flag.StringVar(&configuration.target, "target", "127.0.0.1:7070", "ReducedMeshLoadHarnessService address")
	flag.IntVar(&configuration.connections, "connections", 10_000, "simultaneous gRPC connections")
	flag.DurationVar(&configuration.timeout, "timeout", 2*time.Minute, "whole-run timeout")
	flag.DurationVar(&configuration.hold, "hold", 5*time.Second, "time to retain all acknowledged streams")
	flag.DurationVar(&configuration.ramp, "ramp", 10*time.Second, "connection-start ramp duration")
	flag.Parse()
	return configuration
}

func run(parent context.Context, configuration configuration) error {
	if configuration.target == "" {
		return errors.New("target is required")
	}
	if configuration.connections < 1 || configuration.connections > 50_000 {
		return errors.New("connections must be between 1 and 50000")
	}
	if configuration.timeout <= 0 || configuration.hold < 0 || configuration.ramp < 0 {
		return errors.New("timeout must be positive and hold/ramp cannot be negative")
	}

	ctx, cancel := context.WithTimeout(parent, configuration.timeout)
	defer cancel()
	release := make(chan struct{})
	results := make(chan connectionResult, configuration.connections)
	var workers sync.WaitGroup
	var active atomic.Int64
	start := time.Now()
	rampStep := time.Duration(0)
	if configuration.connections > 1 {
		rampStep = configuration.ramp / time.Duration(configuration.connections-1)
	}

	for index := 0; index < configuration.connections; index++ {
		workers.Add(1)
		go func(index int) {
			defer workers.Done()
			if rampStep > 0 {
				timer := time.NewTimer(time.Duration(index) * rampStep)
				select {
				case <-timer.C:
				case <-ctx.Done():
					timer.Stop()
					results <- connectionResult{err: ctx.Err()}
					return
				}
			}
			connectedAt := time.Now()
			connection, err := grpc.NewClient(
				configuration.target,
				grpc.WithTransportCredentials(insecure.NewCredentials()),
			)
			if err != nil {
				results <- connectionResult{err: err}
				return
			}
			defer connection.Close()
			stream, err := deltav1.NewReducedMeshLoadHarnessServiceClient(connection).Synchronize(ctx)
			if err != nil {
				results <- connectionResult{err: err}
				return
			}
			now := time.Now()
			messageID := fmt.Sprintf("load-%05d-%d", index, start.UnixNano())
			envelope := &deltav1.Envelope{
				MessageId:            messageID,
				SchemaVersion:        1,
				MinimumReaderVersion: 1,
				SenderNodeId:         fmt.Sprintf("sim-node-%05d", index),
				RecipientNodeId:      "load-sink",
				CreatedAtUnixMs:      now.UnixMilli(),
				ExpiresAtUnixMs:      now.Add(configuration.timeout + configuration.hold).UnixMilli(),
				HopLimit:             3,
				Priority:             deltav1.PriorityClass_PRIORITY_CLASS_P2,
				PayloadSha256:        make([]byte, 32),
			}
			if err := stream.Send(&deltav1.ReducedMeshLoadHarnessServiceSynchronizeRequest{Envelope: envelope}); err != nil {
				results <- connectionResult{err: err}
				return
			}
			response, err := stream.Recv()
			if err != nil {
				results <- connectionResult{err: err}
				return
			}
			acknowledgement := response.GetAcknowledgement()
			if acknowledgement.GetMessageId() != messageID ||
				acknowledgement.GetStatus() != deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_DURABLY_STORED {
				results <- connectionResult{err: fmt.Errorf("unexpected acknowledgement for %s", messageID)}
				return
			}
			active.Add(1)
			results <- connectionResult{latency: time.Since(connectedAt)}
			select {
			case <-release:
			case <-ctx.Done():
			}
			active.Add(-1)
			_ = stream.CloseSend()
		}(index)
	}

	latencies := make([]time.Duration, 0, configuration.connections)
	for len(latencies) < configuration.connections {
		select {
		case result := <-results:
			if result.err != nil {
				cancel()
				close(release)
				workers.Wait()
				return result.err
			}
			latencies = append(latencies, result.latency)
		case <-ctx.Done():
			cancel()
			close(release)
			workers.Wait()
			return fmt.Errorf("only %d/%d streams acknowledged: %w", len(latencies), configuration.connections, ctx.Err())
		}
	}
	readyElapsed := time.Since(start)
	if got := active.Load(); got != int64(configuration.connections) {
		close(release)
		workers.Wait()
		return fmt.Errorf("active streams = %d, want %d", got, configuration.connections)
	}

	holdTimer := time.NewTimer(configuration.hold)
	select {
	case <-holdTimer.C:
	case <-ctx.Done():
		close(release)
		workers.Wait()
		return ctx.Err()
	}
	close(release)
	workers.Wait()
	fmt.Printf("connections_requested=%d\n", configuration.connections)
	fmt.Printf("connections_acknowledged=%d\n", len(latencies))
	fmt.Printf("simultaneously_active=%d\n", configuration.connections)
	fmt.Printf("ready_elapsed=%s\n", readyElapsed.Round(time.Millisecond))
	fmt.Printf("acknowledgements_per_second=%.2f\n", float64(configuration.connections)/readyElapsed.Seconds())
	fmt.Printf("ack_latency_p50=%s\n", percentile(latencies, 0.50).Round(time.Millisecond))
	fmt.Printf("ack_latency_p95=%s\n", percentile(latencies, 0.95).Round(time.Millisecond))
	fmt.Printf("ack_latency_max=%s\n", percentile(latencies, 1).Round(time.Millisecond))
	fmt.Printf("hold_duration=%s\n", configuration.hold)
	return nil
}

func percentile(values []time.Duration, quantile float64) time.Duration {
	ordered := append([]time.Duration(nil), values...)
	slices.Sort(ordered)
	position := int(float64(len(ordered)-1) * quantile)
	return ordered[position]
}
