-- The former anonymous archive is preserved, not promoted to a trusted event log.
CREATE TABLE IF NOT EXISTS observer_events (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id TEXT NOT NULL UNIQUE,
  source_node_id TEXT NOT NULL,
  event_json TEXT NOT NULL,
  received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
