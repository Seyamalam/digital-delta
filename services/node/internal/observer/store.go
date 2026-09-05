package observer

import (
	"encoding/binary"
	"errors"
	"fmt"
	"math"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	bolt "go.etcd.io/bbolt"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
)

var (
	eventsBucket   = []byte("observer_events_v1")
	metaBucket     = []byte("observer_meta_v1")
	eventIDsBucket = []byte("observer_event_ids_v1")
	sequenceKey    = []byte("last_sequence")
)

type Store struct {
	db *bolt.DB
}

func OpenStore(path string) (*Store, error) {
	if path == "" {
		return nil, errors.New("observer store path is required")
	}
	db, err := bolt.Open(path, 0o600, &bolt.Options{Timeout: time.Second})
	if err != nil {
		return nil, fmt.Errorf("open observer store: %w", err)
	}
	if err := db.Update(func(tx *bolt.Tx) error {
		if _, err := tx.CreateBucketIfNotExists(eventsBucket); err != nil {
			return err
		}
		if _, err := tx.CreateBucketIfNotExists(eventIDsBucket); err != nil {
			return err
		}
		_, err := tx.CreateBucketIfNotExists(metaBucket)
		return err
	}); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("initialize observer store: %w", err)
	}
	return &Store{db: db}, nil
}

func (s *Store) Append(sourceNodeID string, event *deltav1.DomainEvent) (*deltav1.ObserveResponse, bool, error) {
	if sourceNodeID == "" {
		return nil, false, errors.New("source node id is required")
	}
	if event == nil || event.GetEventId() == "" {
		return nil, false, errors.New("event id is required")
	}
	if err := validateEvent(event); err != nil {
		return nil, false, err
	}

	var response *deltav1.ObserveResponse
	created := false
	err := s.db.Update(func(tx *bolt.Tx) error {
		identity := []byte(sourceNodeID + "\x00" + event.GetEventId())
		if existingSequence := tx.Bucket(eventIDsBucket).Get(identity); existingSequence != nil {
			encoded := tx.Bucket(eventsBucket).Get(existingSequence)
			response = new(deltav1.ObserveResponse)
			if err := proto.Unmarshal(encoded, response); err != nil {
				return err
			}
			if !proto.Equal(response.Event, event) {
				return errors.New("event id collision")
			}
			return nil
		}
		meta := tx.Bucket(metaBucket)
		sequence := decodeSequence(meta.Get(sequenceKey)) + 1
		response = &deltav1.ObserveResponse{
			Sequence:     sequence,
			SourceNodeId: sourceNodeID,
			Event:        proto.Clone(event).(*deltav1.DomainEvent),
		}
		encoded, err := proto.Marshal(response)
		if err != nil {
			return err
		}
		key := encodeSequence(sequence)
		if err := tx.Bucket(eventsBucket).Put(key, encoded); err != nil {
			return err
		}
		if err := tx.Bucket(eventIDsBucket).Put(identity, key); err != nil {
			return err
		}
		created = true
		return meta.Put(sequenceKey, key)
	})
	if err != nil {
		return nil, false, fmt.Errorf("append observer event: %w", err)
	}
	return response, created, nil
}

// Inspect every floating-point field, including ones not exposed by the public
// adapter. Invalid history must never enter the durable stream again.
func validateEvent(event *deltav1.DomainEvent) error {
	var invalid bool
	var walk func(protoreflect.Message)
	walk = func(message protoreflect.Message) {
		message.Range(func(field protoreflect.FieldDescriptor, value protoreflect.Value) bool {
			check := func(v protoreflect.Value) {
				switch field.Kind() {
				case protoreflect.FloatKind, protoreflect.DoubleKind:
					n := v.Float()
					invalid = invalid || math.IsNaN(n) || math.IsInf(n, 0)
					switch field.Name() {
					case "probability", "threshold", "soil_saturation":
						invalid = invalid || n < 0 || n > 1
					case "latitude_degrees":
						invalid = invalid || n < -90 || n > 90
					case "longitude_degrees":
						invalid = invalid || n < -180 || n > 180
					case "rainfall_mm_per_hour":
						invalid = invalid || n < 0
					}
				case protoreflect.MessageKind:
					walk(v.Message())
				}
			}
			if field.IsList() {
				for i := 0; i < value.List().Len(); i++ {
					check(value.List().Get(i))
				}
			} else {
				check(value)
			}
			return !invalid
		})
	}
	walk(event.ProtoReflect())
	if invalid {
		return errors.New("invalid observation numeric value")
	}
	return nil
}

func (s *Store) Replay(afterSequence uint64) ([]*deltav1.ObserveResponse, error) {
	responses := make([]*deltav1.ObserveResponse, 0)
	err := s.db.View(func(tx *bolt.Tx) error {
		cursor := tx.Bucket(eventsBucket).Cursor()
		for key, value := cursor.Seek(encodeSequence(afterSequence + 1)); key != nil; key, value = cursor.Next() {
			response := new(deltav1.ObserveResponse)
			if err := proto.Unmarshal(value, response); err != nil {
				return err
			}
			responses = append(responses, response)
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("replay observer events: %w", err)
	}
	return responses, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func encodeSequence(sequence uint64) []byte {
	encoded := make([]byte, 8)
	binary.BigEndian.PutUint64(encoded, sequence)
	return encoded
}

func decodeSequence(encoded []byte) uint64 {
	if len(encoded) != 8 {
		return 0
	}
	return binary.BigEndian.Uint64(encoded)
}
