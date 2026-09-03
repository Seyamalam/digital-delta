package mesh

import (
	"context"
	"net"
	"path/filepath"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"
)

func TestSynchronizeStreamsDurableAcknowledgements(t *testing.T) {
	now := time.UnixMilli(1_800_000_000_000)
	store, err := OpenStore(StoreOptions{
		Path:   filepath.Join(t.TempDir(), "mesh.db"),
		NodeID: "relay-sylhet",
		Now:    func() time.Time { return now },
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })

	listener := bufconn.Listen(1024 * 1024)
	server := grpc.NewServer()
	deltav1.RegisterNodeMeshServiceServer(server, NewService(store))
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(server.Stop)

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	connection, err := grpc.NewClient(
		"passthrough:///bufnet",
		grpc.WithContextDialer(func(context.Context, string) (net.Conn, error) { return listener.Dial() }),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = connection.Close() })

	stream, err := deltav1.NewNodeMeshServiceClient(connection).Synchronize(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if err := stream.Send(&deltav1.SynchronizeRequest{Envelope: validEnvelope(now)}); err != nil {
		t.Fatal(err)
	}
	response, err := stream.Recv()
	if err != nil {
		t.Fatal(err)
	}

	ack := response.GetAcknowledgement()
	if ack.GetNodeId() != "relay-sylhet" {
		t.Fatalf("node id = %q", ack.GetNodeId())
	}
	if ack.GetStatus() != deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_DURABLY_STORED {
		t.Fatalf("status = %v", ack.GetStatus())
	}
}
