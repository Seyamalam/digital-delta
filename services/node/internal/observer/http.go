package observer

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
)

type HTTPHandler struct {
	hub            *Hub
	allowedOrigins map[string]struct{}
}

func NewHTTPHandler(hub *Hub, allowedOrigins []string) http.Handler {
	handler := &HTTPHandler{hub: hub, allowedOrigins: make(map[string]struct{}, len(allowedOrigins))}
	for _, origin := range allowedOrigins {
		handler.allowedOrigins[origin] = struct{}{}
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /observer/events", handler.events)
	return mux
}

func (h *HTTPHandler) events(writer http.ResponseWriter, request *http.Request) {
	if !h.allowOrigin(writer, request) {
		http.Error(writer, "origin not allowed", http.StatusForbidden)
		return
	}
	afterSequence, err := requestedSequence(request)
	if err != nil {
		http.Error(writer, "invalid event cursor", http.StatusBadRequest)
		return
	}
	flusher, ok := writer.(http.Flusher)
	if !ok {
		http.Error(writer, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	writer.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	writer.Header().Set("Cache-Control", "no-cache")
	writer.Header().Set("Connection", "keep-alive")
	writer.WriteHeader(http.StatusOK)
	flusher.Flush()

	for {
		responses, updated, err := h.hub.replayAndWatch(afterSequence)
		if err != nil {
			return
		}
		for _, response := range responses {
			encoded, err := json.Marshal(toPresentationEvent(response))
			kind := "observation"
			if err != nil || validateEvent(response.Event) != nil {
				// Retain the original record for investigation; expose no bad payload.
				// A named rejection advances the cursor so subsequent records replay.
				kind = "observation-rejected"
				encoded = []byte(`{"reason":"INVALID_RETAINED_RECORD"}`)
			}
			if _, err := fmt.Fprintf(writer, "id: %d\nevent: %s\ndata: %s\n\n", response.GetSequence(), kind, encoded); err != nil {
				return
			}
			flusher.Flush()
			afterSequence = response.GetSequence()
		}
		select {
		case <-request.Context().Done():
			return
		case <-updated:
		}
	}
}

func (h *HTTPHandler) allowOrigin(writer http.ResponseWriter, request *http.Request) bool {
	origin := request.Header.Get("Origin")
	if origin == "" {
		return true
	}
	if _, ok := h.allowedOrigins[origin]; !ok {
		return false
	}
	writer.Header().Set("Access-Control-Allow-Origin", origin)
	writer.Header().Set("Vary", "Origin")
	return true
}

func requestedSequence(request *http.Request) (uint64, error) {
	cursor := request.Header.Get("Last-Event-ID")
	if cursor == "" {
		cursor = request.URL.Query().Get("after")
	}
	if cursor == "" {
		return 0, nil
	}
	return strconv.ParseUint(cursor, 10, 64)
}

type presentationEvent struct {
	Sequence     uint64         `json:"sequence,omitempty"`
	SourceNodeID string         `json:"sourceNodeId"`
	EventID      string         `json:"eventId"`
	Kind         string         `json:"kind"`
	OccurredAt   int64          `json:"occurredAtUnixMs"`
	Simulated    bool           `json:"simulated"`
	ScenarioSeed string         `json:"scenarioSeed,omitempty"`
	Presentation map[string]any `json:"presentation,omitempty"`
}

// PublicObservationJSON is an allowlisted presentation adapter for the Hono
// observer. It deliberately excludes cargo contents, signatures and ciphertext.
// Sequence numbers are assigned by D1, not this publisher.
func PublicObservationJSON(source string, event *deltav1.DomainEvent) ([]byte, error) {
	if event == nil {
		return nil, fmt.Errorf("event required")
	}
	if err := validateEvent(event); err != nil {
		return nil, err
	}
	return json.Marshal(toPresentationEvent(&deltav1.ObserveResponse{SourceNodeId: source, Event: event}))
}

func toPresentationEvent(response *deltav1.ObserveResponse) presentationEvent {
	event := response.GetEvent()
	presentation := presentationEvent{
		Sequence:     response.GetSequence(),
		SourceNodeID: response.GetSourceNodeId(),
		EventID:      event.GetEventId(),
		Kind:         "domainEvent",
		OccurredAt:   event.GetOccurredAtUnixMs(),
		Simulated:    event.GetSimulated(),
		ScenarioSeed: event.GetScenarioSeed(),
	}
	if route := event.GetRoutePlanned(); route != nil {
		presentation.Kind = "routePlanned"
		presentation.Presentation = map[string]any{
			"missionId":       route.GetMissionId(),
			"vehicleId":       route.GetVehicleId(),
			"mode":            route.GetMode().String(),
			"edgeIds":         route.GetEdgeIds(),
			"etaMinutes":      route.GetEtaMinutes(),
			"riskAdjusted":    route.GetRiskAdjusted(),
			"explanationCode": route.GetExplanationCode(),
		}
	} else if request := event.GetReliefRequestCreated(); request != nil {
		presentation.Kind = "reliefRequestCreated"
		presentation.Presentation = map[string]any{
			"requestId":         request.GetRequestId(),
			"requesterNodeId":   request.GetRequesterNodeId(),
			"originNodeId":      request.GetOriginNodeId(),
			"destinationNodeId": request.GetDestinationNodeId(),
			"cargoCount":        len(request.GetCargo()),
		}
	} else if risk := event.GetEdgeRiskPredicted(); risk != nil {
		presentation.Kind = "edgeRiskPredicted"
		presentation.Presentation = map[string]any{
			"edgeId":          risk.GetEdgeId(),
			"probability":     risk.GetProbability(),
			"threshold":       risk.GetThreshold(),
			"modelVersion":    risk.GetModelVersion(),
			"simulatedInputs": risk.GetSimulatedInputs(),
		}
	} else if edge := event.GetEdgeStatusChanged(); edge != nil {
		presentation.Kind = "edgeStatusChanged"
		presentation.Presentation = map[string]any{
			"edgeId":     edge.GetEdgeId(),
			"failed":     edge.GetFailed(),
			"reasonCode": edge.GetReasonCode(),
			"simulated":  edge.GetSimulated(),
		}
	} else if sla := event.GetSlaBreachPredicted(); sla != nil {
		presentation.Kind = "slaBreachPredicted"
		presentation.Presentation = map[string]any{
			"missionId":          sla.GetMissionId(),
			"priority":           sla.GetPriority().String(),
			"baselineEtaMinutes": sla.GetBaselineEtaMinutes(),
			"slowedEtaMinutes":   sla.GetSlowedEtaMinutes(),
			"slaMinutes":         sla.GetSlaMinutes(),
			"policyVersion":      sla.GetPolicyVersion(),
		}
	} else if rendezvous := event.GetRendezvousPlanned(); rendezvous != nil {
		presentation.Kind = "rendezvousPlanned"
		presentation.Presentation = map[string]any{
			"missionId":                    rendezvous.GetMissionId(),
			"boatVehicleId":                rendezvous.GetBoatVehicleId(),
			"droneVehicleId":               rendezvous.GetDroneVehicleId(),
			"candidateId":                  rendezvous.GetCandidateId(),
			"latitudeDegrees":              rendezvous.GetLatitudeDegrees(),
			"longitudeDegrees":             rendezvous.GetLongitudeDegrees(),
			"boatEtaMinutes":               rendezvous.GetBoatEtaMinutes(),
			"droneEtaMinutes":              rendezvous.GetDroneEtaMinutes(),
			"deliveryEtaMinutes":           rendezvous.GetDeliveryEtaMinutes(),
			"projectedDroneBatteryPercent": rendezvous.GetProjectedDroneBatteryPercent(),
			"reserveBatteryPercent":        rendezvous.GetReserveBatteryPercent(),
			"objectiveCode":                rendezvous.GetObjectiveCode(),
			"simulated":                    rendezvous.GetSimulated(),
		}
	} else if vehicle := event.GetVehicleStateChanged(); vehicle != nil {
		presentation.Kind = "vehicleStateChanged"
		presentation.Presentation = map[string]any{
			"vehicleId":        vehicle.GetVehicleId(),
			"mode":             vehicle.GetMode().String(),
			"stateCode":        vehicle.GetStateCode(),
			"nodeId":           vehicle.GetNodeId(),
			"latitudeDegrees":  vehicle.GetLatitudeDegrees(),
			"longitudeDegrees": vehicle.GetLongitudeDegrees(),
			"batteryPercent":   vehicle.GetBatteryPercent(),
			"simulated":        vehicle.GetSimulated(),
		}
	}
	return presentation
}
