DROP INDEX idx_outbox_events_ready;

CREATE INDEX idx_outbox_events_ready
    ON outbox_events (available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PUBLISHING');
