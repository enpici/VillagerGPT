-- Initial persistence schema reference for VillagerLife plugin (JSON-backed repositories)
-- Version: v1
-- Files:
--  - village-state.json
--  - agent-state.json
--  - build-queue-state.json

CREATE TABLE IF NOT EXISTS village_state (
  id TEXT PRIMARY KEY,
  payload_json TEXT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_state (
  villager_uuid TEXT PRIMARY KEY,
  payload_json TEXT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS build_queue_state (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  payload_json TEXT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL
);
