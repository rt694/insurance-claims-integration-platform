package com.rohantummala.insurance.claims.domain.model;

import com.rohantummala.insurance.claims.domain.exception.InvalidClaimStatusTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Claim(
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

  public Claim {
    Objects.requireNonNull(id, "id must not be null");
    requireText(externalReference, "externalReference");
    requireText(policyNumber, "policyNumber");
    requireText(claimantName, "claimantName");
    Objects.requireNonNull(claimType, "claimType must not be null");
    Objects.requireNonNull(incidentDate, "incidentDate must not be null");
    requireText(description, "description");
    Objects.requireNonNull(estimatedLoss, "estimatedLoss must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");

    if (estimatedLoss.signum() < 0) {
      throw new IllegalArgumentException("estimatedLoss must not be negative");
    }
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not be before createdAt");
    }
  }

  public static Claim submitted(
      UUID id,
      String externalReference,
      String policyNumber,
      String claimantName,
      ClaimType claimType,
      LocalDate incidentDate,
      String description,
      BigDecimal estimatedLoss,
      Instant submittedAt) {
    return new Claim(
        id,
        externalReference,
        policyNumber,
        claimantName,
        claimType,
        incidentDate,
        description,
        estimatedLoss,
        ClaimStatus.SUBMITTED,
        submittedAt,
        submittedAt);
  }

  public Claim transitionTo(ClaimStatus requestedStatus, Instant transitionedAt) {
    Objects.requireNonNull(requestedStatus, "requestedStatus must not be null");
    Objects.requireNonNull(transitionedAt, "transitionedAt must not be null");

    if (!status.canTransitionTo(requestedStatus)) {
      throw new InvalidClaimStatusTransitionException(status, requestedStatus);
    }
    if (transitionedAt.isBefore(updatedAt)) {
      throw new IllegalArgumentException("transitionedAt must not be before updatedAt");
    }

    return new Claim(
        id,
        externalReference,
        policyNumber,
        claimantName,
        claimType,
        incidentDate,
        description,
        estimatedLoss,
        requestedStatus,
        createdAt,
        transitionedAt);
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
