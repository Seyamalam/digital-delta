package observer_test

import (
	"context"
	"net"
	"path/filepath"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/observer"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"
)

func TestObserveReplaysOnlyEventsAfterRequestedSequence(t *testing.T) {
	store, err := observer.OpenStore(filepath.Join(t.TempDir(), "observer.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	hub := observer.NewHub(store)
	for _, eventID := range []string{"event-1", "event-2"} {
		if _, err := hub.Publish("field-n4", routeEvent(eventID)); err != nil {
			t.Fatal(err)
		}
	}

	listener := bufconn.Listen(1024 * 1024)
	server := grpc.NewServer()
	deltav1.RegisterObserverServiceServer(server, observer.NewService(hub))
	go func() { _ = server.Serve(listener) }()
	defer server.Stop()

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	connection, err := grpc.NewClient(
		"passthrough:///observer",
		grpc.WithContextDialer(func(context.Context, string) (net.Conn, error) { return listener.Dial() }),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()

	stream, err := deltav1.NewObserverServiceClient(connection).Observe(ctx, &deltav1.ObserveRequest{
		ObserverId:    "projector-a",
		AfterSequence: 1,
	})
	if err != nil {
		t.Fatal(err)
	}
	response, err := stream.Recv()
	if err != nil {
		t.Fatal(err)
	}
	if response.GetSequence() != 2 {
		t.Fatalf("sequence = %d, want 2", response.GetSequence())
	}
	if response.GetEvent().GetEventId() != "event-2" {
		t.Fatalf("event id = %q, want event-2", response.GetEvent().GetEventId())
	}
	if response.GetSourceNodeId() != "field-n4" {
		t.Fatalf("source node = %q, want field-n4", response.GetSourceNodeId())
	}
}

func TestPublishObservationMakesEventAvailableToObservers(t *testing.T) {
	store, err := observer.OpenStore(filepath.Join(t.TempDir(), "observer.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	hub := observer.NewHub(store)

	listener := bufconn.Listen(1024 * 1024)
	server := grpc.NewServer()
	deltav1.RegisterObserverServiceServer(server, observer.NewService(hub))
	go func() { _ = server.Serve(listener) }()
	defer server.Stop()

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	connection, err := grpc.NewClient(
		"passthrough:///observer",
		grpc.WithContextDialer(func(context.Context, string) (net.Conn, error) { return listener.Dial() }),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()
	client := deltav1.NewObserverServiceClient(connection)

	request := &deltav1.ObserverServicePublishRequest{
		SourceNodeId: "field-n4",
		Event:        routeEvent("event-live-1"),
	}
	published, err := client.Publish(ctx, request)
	if err != nil {
		t.Fatal(err)
	}
	if published.GetSequence() != 1 {
		t.Fatalf("published sequence = %d, want 1", published.GetSequence())
	}
	duplicate, err := client.Publish(ctx, request)
	if err != nil {
		t.Fatal(err)
	}
	if duplicate.GetSequence() != 1 {
		t.Fatalf("duplicate sequence = %d, want original sequence 1", duplicate.GetSequence())
	}
	stream, err := client.Observe(ctx, &deltav1.ObserveRequest{ObserverId: "projector-a"})
	if err != nil {
		t.Fatal(err)
	}
	received, err := stream.Recv()
	if err != nil {
		t.Fatal(err)
	}
	if received.GetEvent().GetEventId() != "event-live-1" {
		t.Fatalf("event id = %q, want event-live-1", received.GetEvent().GetEventId())
	}
}

func routeEvent(eventID string) *deltav1.DomainEvent {
	return &deltav1.DomainEvent{
		EventId:          eventID,
		SchemaVersion:    1,
		ActorIdentityId:  "coordinator-sylhet",
		OccurredAtUnixMs: 1_774_000_000_000,
		Simulated:        true,
		ScenarioSeed:     "20260412",
		Body: &deltav1.DomainEvent_RoutePlanned{RoutePlanned: &deltav1.RoutePlanned{
			MissionId:       "mission-p0-01",
			VehicleId:       "boat-02",
			Mode:            deltav1.TransportMode_TRANSPORT_MODE_WATERWAY,
			EdgeIds:         []string{"E6", "E7"},
			EtaMinutes:      200,
			ExplanationCode: "ROAD_FAILED",
		}},
	}
}
