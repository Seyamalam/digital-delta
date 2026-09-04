package drill_test

import (
	"testing"
	"time"

	"github.com/Seyamalam/digital-delta/services/node/internal/drill"
)

func TestScenarioIsDeterministicAndEverySyntheticFactIsLabelled(t *testing.T) {
	start := time.Date(2026, time.April, 12, 8, 0, 0, 0, time.UTC)
	first := drill.Scenario("fair-pass-01", start)
	second := drill.Scenario("fair-pass-01", start)

	if len(first) != 7 {
		t.Fatalf("event count = %d, want 7", len(first))
	}
	for index, event := range first {
		if !event.GetSimulated() {
			t.Fatalf("event %d is not marked simulated", index)
		}
		if event.GetScenarioSeed() != "fair-pass-01" {
			t.Fatalf("event %d seed = %q", index, event.GetScenarioSeed())
		}
		if event.GetEventId() != second[index].GetEventId() {
			t.Fatalf("event %d id is not deterministic", index)
		}
	}
	if first[3].GetRoutePlanned() == nil {
		t.Fatal("fourth event must carry the replanned route")
	}
	if first[5].GetRendezvousPlanned() == nil {
		t.Fatal("sixth event must carry the boat/drone rendezvous")
	}
}
