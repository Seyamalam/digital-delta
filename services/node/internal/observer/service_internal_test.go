package observer

import (
	"testing"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
)

func TestMissionIDExtractsOnlyAvailablePresentationIdentifiers(t *testing.T) {
	route := &deltav1.DomainEvent{Body: &deltav1.DomainEvent_RoutePlanned{RoutePlanned: &deltav1.RoutePlanned{MissionId: "mission-p0-01"}}}
	if got := missionID(route); got != "mission-p0-01" {
		t.Fatalf("mission id = %q", got)
	}

	custody := &deltav1.DomainEvent{Body: &deltav1.DomainEvent_CustodyTransfer{CustodyTransfer: &deltav1.CustodyTransfer{DeliveryId: "delivery-1"}}}
	if got := missionID(custody); got != "" {
		t.Fatalf("custody event without mission field exposed %q", got)
	}
}
