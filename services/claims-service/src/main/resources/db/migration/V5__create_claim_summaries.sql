CREATE TABLE claim_summaries (
    claim_id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    missing_information JSONB NOT NULL,
    recommended_human_review_queue VARCHAR(50) NOT NULL,
    safety_flags JSONB NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_claim_summaries_claim
        FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT chk_claim_summary_text
        CHECK (LENGTH(BTRIM(summary)) > 0),
    CONSTRAINT chk_claim_summary_missing_information
        CHECK (jsonb_typeof(missing_information) = 'array'),
    CONSTRAINT chk_claim_summary_review_queue
        CHECK (recommended_human_review_queue IN (
            'STANDARD_REVIEW', 'COMPLEX_REVIEW', 'SPECIALIST_REVIEW'
        )),
    CONSTRAINT chk_claim_summary_safety_flags
        CHECK (jsonb_typeof(safety_flags) = 'array')
);

CREATE INDEX idx_claim_summaries_received_at
    ON claim_summaries (received_at DESC);
