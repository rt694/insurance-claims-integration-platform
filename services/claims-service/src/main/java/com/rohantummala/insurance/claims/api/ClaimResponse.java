package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimResponse(
    UUID id,
    String externalReference,
    String policyNumber,
    String claimantName,
    ClaimType claimType,
    LocalDate incidentDate,
    String description,
    BigDecimal estimatedLoss,
    ClaimStatus status,
    Instant createdAt,
    Instant updatedAt) {

  static ClaimResponse from(Claim claim) {
    return new ClaimResponse(
        claim.id(),
        claim.externalReference(),
        claim.policyNumber(),
        claim.claimantName(),
        claim.claimType(),
        claim.incidentDate(),
        claim.description(),
        claim.estimatedLoss(),
        claim.status(),
        claim.createdAt(),
        claim.updatedAt());
  }
}
