CREATE TABLE webhook_event (
    id VARCHAR(64) PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE student ADD COLUMN datagsm_synced_at TIMESTAMPTZ;
