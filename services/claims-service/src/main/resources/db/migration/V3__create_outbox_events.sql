CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    CONSTRAINT fk_outbox_events_claim
        FOREIGN KEY (aggregate_id) REFERENCES claims (id),
    CONSTRAINT chk_outbox_event_version
        CHECK (event_version > 0),
    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'DEAD')),
    CONSTRAINT chk_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_outbox_published_at
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_outbox_events_ready
    ON outbox_events (available_at ASC, created_at ASC)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at ASC);
