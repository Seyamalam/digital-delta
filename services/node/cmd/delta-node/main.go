package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/mesh"
	"github.com/Seyamalam/digital-delta/services/node/internal/observer"
	"google.golang.org/grpc"
)

type config struct {
	listenAddress         string
	dataPath              string
	nodeID                string
	observerListenAddress string
	observerDataPath      string
	dashboardOrigins      string
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
	flag.StringVar(&configuration.observerListenAddress, "observer-listen", "127.0.0.1:7071", "local SSE observer listen address")
	flag.StringVar(&configuration.observerDataPath, "observer-data", "data/observer.db", "durable observer event store path")
	flag.StringVar(&configuration.dashboardOrigins, "dashboard-origins", "http://127.0.0.1:5173,http://localhost:5173", "comma-separated browser origins allowed to observe events")
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
	if err := os.MkdirAll(filepath.Dir(configuration.observerDataPath), 0o750); err != nil {
		return fmt.Errorf("create observer data directory: %w", err)
	}

	store, err := mesh.OpenStore(mesh.StoreOptions{Path: configuration.dataPath, NodeID: configuration.nodeID})
	if err != nil {
		return err
	}
	defer store.Close()
	observerStore, err := observer.OpenStore(configuration.observerDataPath)
	if err != nil {
		return err
	}
	defer observerStore.Close()
	observerHub := observer.NewHub(observerStore)

	listener, err := net.Listen("tcp", configuration.listenAddress)
	if err != nil {
		return fmt.Errorf("listen: %w", err)
	}
	defer listener.Close()
	observerListener, err := net.Listen("tcp", configuration.observerListenAddress)
	if err != nil {
		return fmt.Errorf("listen for browser observers: %w", err)
	}
	defer observerListener.Close()

	server := grpc.NewServer(
		grpc.MaxRecvMsgSize(2*1024*1024),
		grpc.MaxSendMsgSize(2*1024*1024),
	)
	deltav1.RegisterNodeMeshServiceServer(server, mesh.NewService(store))
	deltav1.RegisterObserverServiceServer(server, observer.NewService(observerHub))
	observerHTTP := &http.Server{
		Handler:           observer.NewHTTPHandler(observerHub, splitOrigins(configuration.dashboardOrigins)),
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       90 * time.Second,
	}

	serveError := make(chan error, 2)
	go func() {
		slog.Info("delta node ready", "node_id", configuration.nodeID, "listen", listener.Addr().String())
		serveError <- server.Serve(listener)
	}()
	go func() {
		slog.Info("observer bridge ready", "listen", observerListener.Addr().String())
		serveError <- observerHTTP.Serve(observerListener)
	}()

	var runError error
	select {
	case err := <-serveError:
		if !errors.Is(err, http.ErrServerClosed) {
			runError = err
		}
	case <-ctx.Done():
	}
	shutdownContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = observerHTTP.Shutdown(shutdownContext)
	gracefulDone := make(chan struct{})
	go func() {
		server.GracefulStop()
		close(gracefulDone)
	}()
	select {
	case <-gracefulDone:
	case <-shutdownContext.Done():
		server.Stop()
	}
	return runError
}

func splitOrigins(value string) []string {
	parts := strings.Split(value, ",")
	origins := make([]string, 0, len(parts))
	for _, part := range parts {
		if origin := strings.TrimSpace(part); origin != "" {
			origins = append(origins, origin)
		}
	}
	return origins
}
