CREATE TABLE IF NOT EXISTS headquarters_observations (
  event_id TEXT PRIMARY KEY NOT NULL,
  sequence INTEGER NOT NULL CHECK (sequence > 0),
  source_node_id TEXT NOT NULL,
  event_kind TEXT NOT NULL,
  occurred_at_unix_ms INTEGER NOT NULL CHECK (occurred_at_unix_ms > 0),
  simulated INTEGER NOT NULL CHECK (simulated IN (0, 1)),
  summary TEXT NOT NULL,
  received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
) STRICT;

CREATE INDEX IF NOT EXISTS idx_headquarters_observations_sequence
  ON headquarters_observations(sequence DESC);
