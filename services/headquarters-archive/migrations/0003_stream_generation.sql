CREATE TABLE observer_stream (
  singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
  generation TEXT NOT NULL
);
INSERT INTO observer_stream (singleton, generation) VALUES (1, lower(hex(randomblob(16))));
