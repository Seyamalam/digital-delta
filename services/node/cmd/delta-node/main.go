package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/mesh"
	"google.golang.org/grpc"
)

type config struct {
	listenAddress string
	dataPath      string
	nodeID        string
}

func main() {
	configuration := parseFlags()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	if err := run(ctx, configuration); err != nil {
		slog.Error("delta node stopped", "error", err)
		os.Exit(1)
	}
}

func parseFlags() config {
	var configuration config
	flag.StringVar(&configuration.listenAddress, "listen", "127.0.0.1:7070", "gRPC listen address")
	flag.StringVar(&configuration.dataPath, "data", "data/mesh.db", "durable mesh store path")
	flag.StringVar(&configuration.nodeID, "node", "command-sylhet", "stable node identifier")
	flag.Parse()
	return configuration
}

func run(ctx context.Context, configuration config) error {
	if configuration.nodeID == "" {
		return errors.New("node id is required")
	}
	if err := os.MkdirAll(filepath.Dir(configuration.dataPath), 0o750); err != nil {
		return fmt.Errorf("create data directory: %w", err)
	}

	store, err := mesh.OpenStore(mesh.StoreOptions{Path: configuration.dataPath, NodeID: configuration.nodeID})
	if err != nil {
		return err
	}
	defer store.Close()

	listener, err := net.Listen("tcp", configuration.listenAddress)
	if err != nil {
		return fmt.Errorf("listen: %w", err)
	}
	defer listener.Close()

	server := grpc.NewServer(
		grpc.MaxRecvMsgSize(2*1024*1024),
		grpc.MaxSendMsgSize(2*1024*1024),
	)
	deltav1.RegisterNodeMeshServiceServer(server, mesh.NewService(store))

	serveError := make(chan error, 1)
	go func() {
		slog.Info("delta node ready", "node_id", configuration.nodeID, "listen", listener.Addr().String())
		serveError <- server.Serve(listener)
	}()

	select {
	case err := <-serveError:
		return err
	case <-ctx.Done():
		gracefulDone := make(chan struct{})
		go func() {
			server.GracefulStop()
			close(gracefulDone)
		}()
		select {
		case <-gracefulDone:
		case <-time.After(5 * time.Second):
			server.Stop()
		}
		return nil
	}
}
