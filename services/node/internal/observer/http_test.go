package observer_test

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
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

func TestFieldEventsContinueWhileDashboardIsDisconnectedAndReplayInOrder(t *testing.T) {
	store, err := observer.OpenStore(filepath.Join(t.TempDir(), "observer.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	hub := observer.NewHub(store)
	server := httptest.NewServer(observer.NewHTTPHandler(hub, nil))
	defer server.Close()

	if _, err := hub.Publish("field-n4", routeEvent("event-before-disconnect")); err != nil {
		t.Fatal(err)
	}
	firstResponse, firstCancel := openEventStream(t, server.URL+"/observer/events")
	if got := readEventFrame(t, firstResponse).eventID; got != "event-before-disconnect" {
		t.Fatalf("event before disconnect = %q", got)
	}
	firstCancel()
	_ = firstResponse.Body.Close()

	for _, eventID := range []string{"event-while-disconnected-1", "event-while-disconnected-2"} {
		if _, err := hub.Publish("field-n4", routeEvent(eventID)); err != nil {
			t.Fatalf("field publish with no dashboard for %s: %v", eventID, err)
		}
	}

	reconnected, cancelReconnect := openEventStream(t, server.URL+"/observer/events?after=1")
	defer cancelReconnect()
	defer reconnected.Body.Close()
	for index, want := range []string{"event-while-disconnected-1", "event-while-disconnected-2"} {
		frame := readEventFrame(t, reconnected)
		if frame.sequence != index+2 || frame.eventID != want {
			t.Fatalf("replayed frame %d = sequence %d event %q", index, frame.sequence, frame.eventID)
		}
	}
}

type eventFrame struct {
	sequence int
	eventID  string
}

func openEventStream(t *testing.T, url string) (*http.Response, context.CancelFunc) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		cancel()
		t.Fatal(err)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		cancel()
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusOK {
		cancel()
		_ = response.Body.Close()
		t.Fatalf("status = %d, want 200", response.StatusCode)
	}
	return response, cancel
}

func readEventFrame(t *testing.T, response *http.Response) eventFrame {
	t.Helper()
	scanner := bufio.NewScanner(response.Body)
	lines := make([]string, 0, 3)
	for scanner.Scan() {
		if scanner.Text() == "" {
			break
		}
		lines = append(lines, scanner.Text())
	}
	if err := scanner.Err(); err != nil {
		t.Fatal(err)
	}
	if len(lines) != 3 {
		t.Fatalf("SSE frame = %q", lines)
	}
	var sequence int
	if _, err := fmt.Sscanf(lines[0], "id: %d", &sequence); err != nil {
		t.Fatalf("parse SSE sequence %q: %v", lines[0], err)
	}
	var event map[string]any
	if err := json.Unmarshal([]byte(strings.TrimPrefix(lines[2], "data: ")), &event); err != nil {
		t.Fatal(err)
	}
	eventID, _ := event["eventId"].(string)
	return eventFrame{sequence: sequence, eventID: eventID}
}
