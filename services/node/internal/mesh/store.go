package mesh

import (
	"errors"
	"fmt"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	bolt "go.etcd.io/bbolt"
	"google.golang.org/protobuf/proto"
)

var messagesBucket = []byte("mesh_messages_v1")

type StoreOptions struct {
	Path   string
	NodeID string
	Now    func() time.Time
}

type Store struct {
	db     *bolt.DB
	nodeID string
	now    func() time.Time
}

func OpenStore(options StoreOptions) (*Store, error) {
	if options.Path == "" {
		return nil, errors.New("mesh store path is required")
	}
	if options.NodeID == "" {
		return nil, errors.New("node id is required")
	}
	if options.Now == nil {
		options.Now = time.Now
	}

	db, err := bolt.Open(options.Path, 0o600, &bolt.Options{Timeout: time.Second})
	if err != nil {
		return nil, fmt.Errorf("open mesh store: %w", err)
	}
	if err := db.Update(func(tx *bolt.Tx) error {
		_, createErr := tx.CreateBucketIfNotExists(messagesBucket)
		return createErr
	}); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("initialize mesh store: %w", err)
	}

	return &Store{db: db, nodeID: options.NodeID, now: options.Now}, nil
}

func (s *Store) Accept(envelope *deltav1.Envelope) (*deltav1.Acknowledgement, error) {
	recordedAt := s.now()
	if reason := rejectionReason(envelope, recordedAt); reason != "" {
		return s.acknowledgement(envelope.GetMessageId(), deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_REJECTED, reason, recordedAt), nil
	}

	encoded, err := proto.Marshal(envelope)
	if err != nil {
		return nil, fmt.Errorf("marshal envelope: %w", err)
	}

	duplicate := false
	if err := s.db.Update(func(tx *bolt.Tx) error {
		bucket := tx.Bucket(messagesBucket)
		key := []byte(envelope.GetMessageId())
		if bucket.Get(key) != nil {
			duplicate = true
			return nil
		}
		return bucket.Put(key, encoded)
	}); err != nil {
		return nil, fmt.Errorf("persist envelope: %w", err)
	}
	if duplicate {
		return s.acknowledgement(envelope.GetMessageId(), deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_REJECTED, "DUPLICATE", recordedAt), nil
	}

	return s.acknowledgement(envelope.GetMessageId(), deltav1.AcknowledgementStatus_ACKNOWLEDGEMENT_STATUS_DURABLY_STORED, "", recordedAt), nil
}

func (s *Store) Get(messageID string) (*deltav1.Envelope, bool, error) {
	var encoded []byte
	if err := s.db.View(func(tx *bolt.Tx) error {
		value := tx.Bucket(messagesBucket).Get([]byte(messageID))
		if value != nil {
			encoded = append(encoded, value...)
		}
		return nil
	}); err != nil {
		return nil, false, fmt.Errorf("read envelope: %w", err)
	}
	if encoded == nil {
		return nil, false, nil
	}

	envelope := new(deltav1.Envelope)
	if err := proto.Unmarshal(encoded, envelope); err != nil {
		return nil, false, fmt.Errorf("decode envelope: %w", err)
	}
	return envelope, true, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) acknowledgement(messageID string, status deltav1.AcknowledgementStatus, reason string, recordedAt time.Time) *deltav1.Acknowledgement {
	return &deltav1.Acknowledgement{
		MessageId:        messageID,
		NodeId:           s.nodeID,
		Status:           status,
		ReasonCode:       reason,
		RecordedAtUnixMs: recordedAt.UnixMilli(),
	}
}

func rejectionReason(envelope *deltav1.Envelope, now time.Time) string {
	if envelope == nil || envelope.GetMessageId() == "" {
		return "INVALID_MESSAGE_ID"
	}
	if envelope.GetSchemaVersion() != 1 {
		return "UNSUPPORTED_SCHEMA"
	}
	if envelope.GetExpiresAtUnixMs() <= now.UnixMilli() {
		return "EXPIRED"
	}
	if envelope.GetHopLimit() == 0 || envelope.GetHopCount() >= envelope.GetHopLimit() {
		return "HOP_LIMIT_REACHED"
	}
	if len(envelope.GetPayloadSha256()) != 32 {
		return "INVALID_PAYLOAD_HASH"
	}
	return ""
}
