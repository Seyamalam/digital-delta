package observer_test

import (
	"bufio"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/Seyamalam/digital-delta/services/node/internal/observer"
)

func TestSSEResumesAfterLastEventAndExposesOnlyPresentationFields(t *testing.T) {
	store, err := observer.OpenStore(filepath.Join(t.TempDir(), "observer.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	hub := observer.NewHub(store)
	if _, err := hub.Publish("field-n4", routeEvent("event-1")); err != nil {
		t.Fatal(err)
	}
	if _, err := hub.Publish("field-n4", routeEvent("event-2")); err != nil {
		t.Fatal(err)
	}

	server := httptest.NewServer(observer.NewHTTPHandler(hub, []string{"http://127.0.0.1:5173"}))
	defer server.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, server.URL+"/observer/events", nil)
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Origin", "http://127.0.0.1:5173")
	request.Header.Set("Last-Event-ID", "1")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", response.StatusCode)
	}
	if got := response.Header.Get("Access-Control-Allow-Origin"); got != "http://127.0.0.1:5173" {
		t.Fatalf("allow origin = %q", got)
	}
	if got := response.Header.Get("Content-Type"); !strings.HasPrefix(got, "text/event-stream") {
		t.Fatalf("content type = %q", got)
	}

	scanner := bufio.NewScanner(response.Body)
	lines := make([]string, 0, 3)
	for scanner.Scan() {
		if scanner.Text() == "" {
			break
		}
		lines = append(lines, scanner.Text())
	}
	if len(lines) != 3 || lines[0] != "id: 2" || lines[1] != "event: observation" {
		t.Fatalf("unexpected SSE frame: %q", lines)
	}
	var event map[string]any
	if err := json.Unmarshal([]byte(strings.TrimPrefix(lines[2], "data: ")), &event); err != nil {
		t.Fatal(err)
	}
	if event["eventId"] != "event-2" || event["kind"] != "routePlanned" {
		t.Fatalf("unexpected presentation event: %#v", event)
	}
	encoded := lines[2]
	for _, forbidden := range []string{"encryptedPayload", "ciphertext", "wrappedAes256Key", "senderSignature"} {
		if strings.Contains(encoded, forbidden) {
			t.Fatalf("SSE data exposed forbidden field %q: %s", forbidden, encoded)
		}
	}
}
