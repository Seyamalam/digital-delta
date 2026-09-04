package observer

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/drill"
)

func TestPresentationMapperAllowListsEveryDrillEvent(t *testing.T) {
	wantKinds := []string{
		"reliefRequestCreated",
		"edgeRiskPredicted",
		"edgeStatusChanged",
		"routePlanned",
		"slaBreachPredicted",
		"rendezvousPlanned",
		"vehicleStateChanged",
	}
	for index, event := range drill.Scenario("observer-test", time.Unix(0, 0)) {
		presentation := toPresentationEvent(&deltav1.ObserveResponse{Sequence: uint64(index + 1), SourceNodeId: "simulator", Event: event})
		if presentation.Kind != wantKinds[index] {
			t.Fatalf("event %d kind = %q, want %q", index, presentation.Kind, wantKinds[index])
		}
		if len(presentation.Presentation) == 0 {
			t.Fatalf("event %d has no allow-listed presentation", index)
		}
		encoded, err := json.Marshal(presentation)
		if err != nil {
			t.Fatal(err)
		}
		for _, forbidden := range []string{"ciphertext", "wrappedAes256Key", "senderSignature", "payloadBytes"} {
			if strings.Contains(string(encoded), forbidden) {
				t.Fatalf("event %d exposed %q: %s", index, forbidden, encoded)
			}
		}
	}
}
