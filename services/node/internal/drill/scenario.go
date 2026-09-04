package drill

import (
	"crypto/sha256"
	"fmt"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
)

// Scenario returns a repeatable disaster exercise. All environmental and
// vehicle observations are explicitly marked simulated at the event and body
// levels so they cannot be mistaken for live measurements.
func Scenario(seed string, start time.Time) []*deltav1.DomainEvent {
	builders := []func() *deltav1.DomainEvent{
		func() *deltav1.DomainEvent {
			result := event(seed, start, 0, "request")
			result.Body = &deltav1.DomainEvent_ReliefRequestCreated{
				ReliefRequestCreated: &deltav1.ReliefRequestCreated{
					RequestId: "request-p0-haor-01", RequesterNodeId: "clinic-n7", OriginNodeId: "N1", DestinationNodeId: "N7",
					Cargo:           []*deltav1.CargoItem{{CargoId: "blood-cooler-01", ItemCode: "blood-cooler", Quantity: 1, UnitCode: "cooler", Priority: deltav1.PriorityClass_PRIORITY_CLASS_P0}},
					CreatedAtUnixMs: start.UnixMilli(),
				},
			}
			return result
		},
		func() *deltav1.DomainEvent {
			result := event(seed, start, 1, "risk")
			result.Body = &deltav1.DomainEvent_EdgeRiskPredicted{EdgeRiskPredicted: &deltav1.EdgeRiskPredicted{
				EdgeId: "E3", Probability: 0.973, Threshold: 0.65, ModelVersion: "route-decay-v1", RainfallMmPerHour: 82, ElevationMeters: 3, SoilSaturation: 0.92, SimulatedInputs: true,
			}}
			return result
		},
		func() *deltav1.DomainEvent {
			result := event(seed, start, 2, "edge-failed")
			result.Body = &deltav1.DomainEvent_EdgeStatusChanged{EdgeStatusChanged: &deltav1.EdgeStatusChanged{
				EdgeId: "E3", Failed: true, ReasonCode: "SIMULATED_FLASH_FLOOD", Simulated: true,
			}}
			return result
		},
		func() *deltav1.DomainEvent {
			result := event(seed, start, 3, "route")
			result.Body = &deltav1.DomainEvent_RoutePlanned{RoutePlanned: &deltav1.RoutePlanned{
				MissionId: "mission-p0-01", VehicleId: "boat-02", Mode: deltav1.TransportMode_TRANSPORT_MODE_WATERWAY, EdgeIds: []string{"E6", "E7"}, EtaMinutes: 171, RiskAdjusted: true, ExplanationCode: "ROAD_RISK_REROUTE",
			}}
			return result
		},
		func() *deltav1.DomainEvent {
			result := event(seed, start, 4, "sla")
			result.Body = &deltav1.DomainEvent_SlaBreachPredicted{SlaBreachPredicted: &deltav1.SlaBreachPredicted{
				MissionId: "mission-p0-01", Priority: deltav1.PriorityClass_PRIORITY_CLASS_P0, BaselineEtaMinutes: 92, SlowedEtaMinutes: 120, SlaMinutes: 120, PolicyVersion: "triage-v1",
			}}
			return result
		},
		func() *deltav1.DomainEvent {
			result := event(seed, start, 5, "rendezvous")
			result.Body = &deltav1.DomainEvent_RendezvousPlanned{RendezvousPlanned: &deltav1.RendezvousPlanned{
				MissionId: "mission-p0-01", BoatVehicleId: "boat-02", DroneVehicleId: "drone-07", CandidateId: "R3", LatitudeDegrees: 25.0200, LongitudeDegrees: 91.7000, BoatEtaMinutes: 88, DroneEtaMinutes: 12, DeliveryEtaMinutes: 118, ProjectedDroneBatteryPercent: 43, ReserveBatteryPercent: 20, ObjectiveCode: "MINIMIZE_TOTAL_TIME", Simulated: true,
			}}
			return result
		},
		func() *deltav1.DomainEvent {
			result := event(seed, start, 6, "vehicle")
			result.Body = &deltav1.DomainEvent_VehicleStateChanged{VehicleStateChanged: &deltav1.VehicleStateChanged{
				VehicleId: "boat-02", Mode: deltav1.TransportMode_TRANSPORT_MODE_WATERWAY, StateCode: "DELAYED_18_MIN", NodeId: "R3", LatitudeDegrees: 25.0200, LongitudeDegrees: 91.7000, BatteryPercent: 58, Simulated: true,
			}}
			return result
		},
	}

	events := make([]*deltav1.DomainEvent, 0, len(builders))
	for _, build := range builders {
		events = append(events, build())
	}
	return events
}

func event(seed string, start time.Time, offset int, kind string) *deltav1.DomainEvent {
	digest := sha256.Sum256([]byte(seed + "\x00" + kind))
	return &deltav1.DomainEvent{
		EventId:          fmt.Sprintf("drill-%s-%x", kind, digest[:6]),
		SchemaVersion:    1,
		ActorIdentityId:  "simulator-local-drill",
		OccurredAtUnixMs: start.Add(time.Duration(offset) * time.Second).UnixMilli(),
		Simulated:        true,
		ScenarioSeed:     seed,
	}
}
