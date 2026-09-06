CREATE TABLE claims (
    id UUID PRIMARY KEY,
    external_reference VARCHAR(100) NOT NULL,
    policy_number VARCHAR(50) NOT NULL,
    claimant_name VARCHAR(200) NOT NULL,
    claim_type VARCHAR(30) NOT NULL,
    incident_date DATE NOT NULL,
    description VARCHAR(4000) NOT NULL,
    estimated_loss NUMERIC(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_claims_type
        CHECK (claim_type IN ('AUTO', 'PROPERTY', 'LIFE', 'DISABILITY')),
    CONSTRAINT chk_claims_status
        CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'DENIED', 'CANCELLED', 'CLOSED')),
    CONSTRAINT chk_claims_estimated_loss
        CHECK (estimated_loss >= 0),
    CONSTRAINT chk_claims_timestamps
        CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_claims_external_reference_ci
    ON claims (UPPER(external_reference));

CREATE INDEX idx_claims_created_at_id
    ON claims (created_at DESC, id ASC);

CREATE INDEX idx_claims_status_created_at
    ON claims (status, created_at DESC);

CREATE INDEX idx_claims_type_created_at
    ON claims (claim_type, created_at DESC);
