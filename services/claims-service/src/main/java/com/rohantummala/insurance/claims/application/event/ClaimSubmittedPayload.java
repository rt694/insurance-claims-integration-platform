package com.rohantummala.insurance.claims.application.event;

import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimSubmittedPayload(
    UUID claimId,
    ClaimType claimType,
    LocalDate incidentDate,
    String description,
    BigDecimal estimatedLoss,
    ClaimStatus status,
    Instant submittedAt) {}
