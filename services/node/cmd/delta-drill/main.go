// delta-drill publishes a deterministic, explicitly simulated exercise over
// the same Protobuf ObserverService used by field nodes.
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/drill"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "delta-drill:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	flags := flag.NewFlagSet("delta-drill", flag.ContinueOnError)
	observerAddress := flags.String("observer", "127.0.0.1:7070", "local ObserverService gRPC address")
	sourceNodeID := flags.String("source", "simulator-local-drill", "source node identifier")
	seed := flags.String("seed", "fair-pass-01", "stable scenario seed")
	interval := flags.Duration("interval", 700*time.Millisecond, "delay between events")
	startValue := flags.String("start", "2026-04-12T08:00:00Z", "scenario start in RFC3339")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *sourceNodeID == "" || *seed == "" {
		return fmt.Errorf("source and seed are required")
	}
	start, err := time.Parse(time.RFC3339, *startValue)
	if err != nil {
		return fmt.Errorf("parse start: %w", err)
	}
	connection, err := grpc.NewClient(*observerAddress, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return fmt.Errorf("create observer connection: %w", err)
	}
	defer connection.Close()
	client := deltav1.NewObserverServiceClient(connection)

	for index, event := range drill.Scenario(*seed, start) {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		response, publishErr := client.Publish(ctx, &deltav1.ObserverServicePublishRequest{
			SourceNodeId: *sourceNodeID,
			Event:        event,
		})
		cancel()
		if publishErr != nil {
			return fmt.Errorf("publish %s: %w", event.GetEventId(), publishErr)
		}
		fmt.Printf("sequence=%d event=%s simulated=true\n", response.GetSequence(), event.GetEventId())
		if index < 6 && *interval > 0 {
			time.Sleep(*interval)
		}
	}
	return nil
}
