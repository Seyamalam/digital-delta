package observer

import (
	"context"
	"log/slog"
	"sync"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type Hub struct {
	store   *Store
	mu      sync.Mutex
	updated chan struct{}
}

func NewHub(store *Store) *Hub {
	return &Hub{store: store, updated: make(chan struct{})}
}

func (h *Hub) Publish(sourceNodeID string, event *deltav1.DomainEvent) (*deltav1.ObserveResponse, error) {
	h.mu.Lock()
	defer h.mu.Unlock()
	response, created, err := h.store.Append(sourceNodeID, event)
	if err != nil {
		return nil, err
	}
	if created {
		close(h.updated)
		h.updated = make(chan struct{})
	}
	return response, nil
}

func (h *Hub) replayAndWatch(afterSequence uint64) ([]*deltav1.ObserveResponse, <-chan struct{}, error) {
	h.mu.Lock()
	defer h.mu.Unlock()
	responses, err := h.store.Replay(afterSequence)
	return responses, h.updated, err
}

type Service struct {
	deltav1.UnimplementedObserverServiceServer
	hub *Hub
}

func NewService(hub *Hub) *Service {
	return &Service{hub: hub}
}

func (s *Service) Publish(_ context.Context, request *deltav1.ObserverServicePublishRequest) (*deltav1.ObserverServicePublishResponse, error) {
	response, err := s.hub.Publish(request.GetSourceNodeId(), request.GetEvent())
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}
	event := request.GetEvent()
	slog.Info(
		"observer event published",
		"event_id", event.GetEventId(),
		"node_id", request.GetSourceNodeId(),
		"mission_id", missionID(event),
		"correlation_id", event.GetEventId(),
		"sequence", response.GetSequence(),
		"simulated", event.GetSimulated(),
	)
	return &deltav1.ObserverServicePublishResponse{Sequence: response.GetSequence()}, nil
}

func (s *Service) Observe(request *deltav1.ObserveRequest, stream grpc.ServerStreamingServer[deltav1.ObserveResponse]) error {
	if request.GetObserverId() == "" {
		return status.Error(codes.InvalidArgument, "observer id is required")
	}
	afterSequence := request.GetAfterSequence()
	for {
		responses, updated, err := s.hub.replayAndWatch(afterSequence)
		if err != nil {
			return status.Error(codes.Internal, err.Error())
		}
		for _, response := range responses {
			if err := stream.Send(response); err != nil {
				return err
			}
			afterSequence = response.GetSequence()
		}
		select {
		case <-stream.Context().Done():
			return stream.Context().Err()
		case <-updated:
		}
	}
}

var _ deltav1.ObserverServiceServer = (*Service)(nil)

func missionID(event *deltav1.DomainEvent) string {
	if event == nil {
		return ""
	}
	switch body := event.GetBody().(type) {
	case *deltav1.DomainEvent_RoutePlanned:
		return body.RoutePlanned.GetMissionId()
	case *deltav1.DomainEvent_MissionFieldUpdated:
		return body.MissionFieldUpdated.GetMissionId()
	case *deltav1.DomainEvent_ConflictRaised:
		return body.ConflictRaised.GetMissionId()
	case *deltav1.DomainEvent_SlaBreachPredicted:
		return body.SlaBreachPredicted.GetMissionId()
	case *deltav1.DomainEvent_PreemptionConfirmed:
		return body.PreemptionConfirmed.GetMissionId()
	case *deltav1.DomainEvent_RendezvousPlanned:
		return body.RendezvousPlanned.GetMissionId()
	default:
		return ""
	}
}
