package mesh

import (
	"errors"
	"io"
	"log/slog"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"google.golang.org/grpc"
)

type Service struct {
	deltav1.UnimplementedNodeMeshServiceServer
	store *Store
}

func NewService(store *Store) *Service {
	return &Service{store: store}
}

func (s *Service) Synchronize(stream grpc.BidiStreamingServer[deltav1.SynchronizeRequest, deltav1.SynchronizeResponse]) error {
	for {
		request, err := stream.Recv()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}

		acknowledgement, err := s.store.Accept(request.GetEnvelope())
		if err != nil {
			return err
		}
		envelope := request.GetEnvelope()
		slog.Info(
			"mesh envelope processed",
			"event_id", "encrypted",
			"node_id", acknowledgement.GetNodeId(),
			"mission_id", "encrypted",
			"correlation_id", envelope.GetCorrelationId(),
			"message_id", envelope.GetMessageId(),
			"sender_node_id", envelope.GetSenderNodeId(),
			"recipient_node_id", envelope.GetRecipientNodeId(),
			"status", acknowledgement.GetStatus().String(),
			"reason_code", acknowledgement.GetReasonCode(),
			"simulated", envelope.GetSimulated(),
		)
		if err := stream.Send(&deltav1.SynchronizeResponse{Acknowledgement: acknowledgement}); err != nil {
			return err
		}
	}
}
