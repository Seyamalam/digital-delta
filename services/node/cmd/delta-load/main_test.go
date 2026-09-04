package main

import (
	"context"
	"net"
	"path/filepath"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/mesh"
	"google.golang.org/grpc"
)

func TestRunMaintainsAcknowledgedConcurrentStreams(t *testing.T) {
	store, err := mesh.OpenStore(mesh.StoreOptions{
		Path:   filepath.Join(t.TempDir(), "mesh.db"),
		NodeID: "load-test-node",
	})
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	server := grpc.NewServer()
	deltav1.RegisterNodeMeshServiceServer(server, mesh.NewService(store))
	go server.Serve(listener)
	defer server.Stop()

	err = run(context.Background(), configuration{
		target:      listener.Addr().String(),
		connections: 64,
		timeout:     10 * time.Second,
		hold:        10 * time.Millisecond,
		ramp:        20 * time.Millisecond,
	})
	if err != nil {
		t.Fatal(err)
	}
}

func TestPercentileUsesSortedObservedLatency(t *testing.T) {
	values := []time.Duration{9 * time.Millisecond, time.Millisecond, 5 * time.Millisecond}
	if got := percentile(values, 0.50); got != 5*time.Millisecond {
		t.Fatalf("p50 = %s", got)
	}
	if got := percentile(values, 1); got != 9*time.Millisecond {
		t.Fatalf("max = %s", got)
	}
}
