package mesh

import (
	"errors"
	"io"

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
		if err := stream.Send(&deltav1.SynchronizeResponse{Acknowledgement: acknowledgement}); err != nil {
			return err
		}
	}
}
