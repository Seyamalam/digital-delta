// delta-drill converts deterministic Protobuf exercise events into the
// allowlisted Hono presentation API. JSON never enters the field mesh.
package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"time"

	"github.com/Seyamalam/digital-delta/services/node/internal/drill"
	"github.com/Seyamalam/digital-delta/services/node/internal/observer"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "delta-drill:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	flags := flag.NewFlagSet("delta-drill", flag.ContinueOnError)
	observerAddress := flags.String("observer", "http://127.0.0.1:7071/v1/observations", "Hono sanitized presentation endpoint")
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
	endpoint, err := url.Parse(*observerAddress)
	if err != nil || endpoint.User != nil || endpoint.Host == "" || (endpoint.Scheme != "https" && !(endpoint.Scheme == "http" && (endpoint.Hostname() == "127.0.0.1" || endpoint.Hostname() == "localhost" || endpoint.Hostname() == "::1"))) {
		return fmt.Errorf("observer requires HTTPS, except for local loopback development")
	}
	start, err := time.Parse(time.RFC3339, *startValue)
	if err != nil {
		return fmt.Errorf("parse start: %w", err)
	}
	token := os.Getenv("DELTA_OBSERVER_PUBLISHER_TOKEN")
	if len(token) < 32 {
		return fmt.Errorf("DELTA_OBSERVER_PUBLISHER_TOKEN must contain the enrolled source token")
	}
	client := &http.Client{Timeout: 5 * time.Second, CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return http.ErrUseLastResponse }}

	for index, event := range drill.Scenario(*seed, start) {
		body, err := observer.PublicObservationJSON(*sourceNodeID, event)
		if err != nil {
			return err
		}
		request, err := http.NewRequest(http.MethodPost, *observerAddress, bytes.NewReader(body))
		if err != nil {
			return err
		}
		request.Header.Set("Content-Type", "application/json")
		request.Header.Set("Authorization", "Bearer "+token)
		request.Header.Set("X-Source-Node", *sourceNodeID)
		response, err := client.Do(request)
		if err != nil {
			return fmt.Errorf("publish %s: %w", event.GetEventId(), err)
		}
		var receipt struct {
			Sequence uint64 `json:"sequence"`
		}
		decodeErr := json.NewDecoder(io.LimitReader(response.Body, 4096)).Decode(&receipt)
		response.Body.Close()
		if response.StatusCode < 200 || response.StatusCode >= 300 {
			return fmt.Errorf("publish %s: HTTP %d", event.GetEventId(), response.StatusCode)
		}
		if decodeErr != nil {
			return decodeErr
		}
		fmt.Printf("sequence=%d event=%s simulated=true\n", receipt.Sequence, event.GetEventId())
		if index < 6 && *interval > 0 {
			time.Sleep(*interval)
		}
	}
	return nil
}
