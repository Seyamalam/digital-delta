package mesh

import (
	"bytes"
	"path/filepath"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
)

func TestStoreAcceptsDurablyAndSurvivesReopen(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "mesh.db")
	now := time.UnixMilli(1_800_000_000_000)
	envelope := validEnvelope(now)

	store, err := OpenStore(StoreOptions{Path: dbPath, NodeID: "node-b", Now: func() time.Time { return now }})
	if err != nil {
		t.Fatal(err)
	}

	ack, err := store.Accept(envelope)
	if err != nil {
		t.Fatal(err)
	}
	if got := ack.GetStatus(); got != deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_DURABLY_STORED {
		t.Fatalf("status = %v", got)
	}
	if err := store.Close(); err != nil {
		t.Fatal(err)
	}

	reopened, err := OpenStore(StoreOptions{Path: dbPath, NodeID: "node-b", Now: func() time.Time { return now }})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = reopened.Close() })

	stored, found, err := reopened.Get(envelope.GetMessageId())
	if err != nil {
		t.Fatal(err)
	}
	if !found || !bytes.Equal(stored.GetPayloadSha256(), envelope.GetPayloadSha256()) {
		t.Fatalf("stored envelope was not recovered: found=%v", found)
	}
}

func TestStoreRejectsDuplicateExpiredAndExhaustedMessages(t *testing.T) {
	now := time.UnixMilli(1_800_000_000_000)
	store, err := OpenStore(StoreOptions{
		Path:   filepath.Join(t.TempDir(), "mesh.db"),
		NodeID: "node-b",
		Now:    func() time.Time { return now },
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })

	accepted := validEnvelope(now)
	if _, err := store.Accept(accepted); err != nil {
		t.Fatal(err)
	}

	duplicate, err := store.Accept(accepted)
	if err != nil {
		t.Fatal(err)
	}
	assertRejected(t, duplicate, "DUPLICATE")

	expired := validEnvelope(now)
	expired.MessageId = "expired"
	expired.ExpiresAtUnixMs = now.Add(-time.Second).UnixMilli()
	expiredAck, err := store.Accept(expired)
	if err != nil {
		t.Fatal(err)
	}
	assertRejected(t, expiredAck, "EXPIRED")

	exhausted := validEnvelope(now)
	exhausted.MessageId = "exhausted"
	exhausted.HopCount = exhausted.HopLimit
	exhaustedAck, err := store.Accept(exhausted)
	if err != nil {
		t.Fatal(err)
	}
	assertRejected(t, exhaustedAck, "HOP_LIMIT_REACHED")
}

func validEnvelope(now time.Time) *deltav1.Envelope {
	return &deltav1.Envelope{
		MessageId:       "message-1",
		SchemaVersion:   1,
		SenderNodeId:    "node-a",
		RecipientNodeId: "node-b",
		CreatedAtUnixMs: now.UnixMilli(),
		ExpiresAtUnixMs: now.Add(time.Hour).UnixMilli(),
		HopLimit:        8,
		Priority:        deltav1.PriorityClass_PRIORITY_CLASS_P0,
		PayloadSha256:   bytes.Repeat([]byte{0x42}, 32),
	}
}

func assertRejected(t *testing.T, ack *deltav1.Acknowledgement, reason string) {
	t.Helper()
	if ack.GetStatus() != deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_REJECTED {
		t.Fatalf("status = %v", ack.GetStatus())
	}
	if ack.GetReasonCode() != reason {
		t.Fatalf("reason = %q, want %q", ack.GetReasonCode(), reason)
	}
}
