package observer

import (
	"bufio"
	"context"
	"math"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	bolt "go.etcd.io/bbolt"
	"google.golang.org/protobuf/proto"
)

func TestInvalidNumbersRejectedBeforePersistence(t *testing.T) {
	store, err := OpenStore(filepath.Join(t.TempDir(), "events.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	for _, value := range []float32{float32(math.NaN()), float32(math.Inf(1)), -0.1, 1.1} {
		event := &deltav1.DomainEvent{EventId: "bad", Body: &deltav1.DomainEvent_EdgeRiskPredicted{EdgeRiskPredicted: &deltav1.EdgeRiskPredicted{Probability: value}}}
		if _, _, err := store.Append("N4", event); err == nil {
			t.Fatalf("accepted invalid probability %v", value)
		}
	}
	event := &deltav1.DomainEvent{EventId: "bad-coordinates", Body: &deltav1.DomainEvent_RendezvousPlanned{RendezvousPlanned: &deltav1.RendezvousPlanned{LatitudeDegrees: 91}}}
	if _, _, err := store.Append("N4", event); err == nil {
		t.Fatal("accepted invalid coordinate")
	}
	rows, _ := store.Replay(0)
	if len(rows) != 0 {
		t.Fatal("invalid record persisted")
	}
}

func TestRetainedInvalidRecordIsVisibleAndCannotBlockReplayAfterReopen(t *testing.T) {
	path := filepath.Join(t.TempDir(), "events.db")
	store, err := OpenStore(path)
	if err != nil {
		t.Fatal(err)
	}
	bad := &deltav1.ObserveResponse{Sequence: 1, SourceNodeId: "N4", Event: &deltav1.DomainEvent{EventId: "old-bad", Body: &deltav1.DomainEvent_EdgeRiskPredicted{EdgeRiskPredicted: &deltav1.EdgeRiskPredicted{Probability: float32(math.NaN())}}}}
	encoded, _ := proto.Marshal(bad)
	// Reproduce an old database written before admission validation existed.
	if err := store.db.Update(func(tx *bolt.Tx) error {
		if err := tx.Bucket(eventsBucket).Put(encodeSequence(1), encoded); err != nil {
			return err
		}
		return tx.Bucket(metaBucket).Put(sequenceKey, encodeSequence(1))
	}); err != nil {
		t.Fatal(err)
	}
	if _, _, err := store.Append("N4", &deltav1.DomainEvent{EventId: "valid-after-bad"}); err != nil {
		t.Fatal(err)
	}
	store.Close()
	store, err = OpenStore(path)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	server := httptest.NewServer(NewHTTPHandler(NewHub(store), nil))
	defer server.Close()
	for _, cursor := range []string{"0", "1"} {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		request, _ := http.NewRequestWithContext(ctx, "GET", server.URL+"/observer/events?after="+cursor, nil)
		response, err := http.DefaultClient.Do(request)
		if err != nil {
			cancel()
			t.Fatal(err)
		}
		scanner := bufio.NewScanner(response.Body)
		var lines []string
		for scanner.Scan() {
			lines = append(lines, scanner.Text())
			if strings.Contains(scanner.Text(), "valid-after-bad") {
				break
			}
		}
		response.Body.Close()
		cancel()
		text := strings.Join(lines, "\n")
		if !strings.Contains(text, "valid-after-bad") {
			t.Fatalf("valid event blocked: %s", text)
		}
		if cursor == "0" && !strings.Contains(text, "observation-rejected") {
			t.Fatal("missing visible rejection")
		}
	}
}
