ALTER TABLE claims
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE claim_status_history (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_claim_status_history_claim
        FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT chk_claim_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN (
            'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'DENIED', 'CANCELLED', 'CLOSED'
        )),
    CONSTRAINT chk_claim_history_new_status
        CHECK (new_status IN (
            'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'DENIED', 'CANCELLED', 'CLOSED'
        )),
    CONSTRAINT chk_claim_history_status_changed
        CHECK (previous_status IS NULL OR previous_status <> new_status)
);

CREATE INDEX idx_claim_status_history_claim_time
    ON claim_status_history (claim_id, changed_at ASC, id ASC);

INSERT INTO claim_status_history (id, claim_id, previous_status, new_status, changed_at)
SELECT gen_random_uuid(), id, NULL, status, created_at
FROM claims;
