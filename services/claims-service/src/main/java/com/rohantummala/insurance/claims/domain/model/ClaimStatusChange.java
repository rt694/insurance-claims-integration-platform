package com.rohantummala.insurance.claims.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimStatusChange(
    UUID id, UUID claimId, ClaimStatus previousStatus, ClaimStatus newStatus, Instant changedAt) {

  public ClaimStatusChange {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(claimId, "claimId must not be null");
    Objects.requireNonNull(newStatus, "newStatus must not be null");
    Objects.requireNonNull(changedAt, "changedAt must not be null");

    if (previousStatus == newStatus) {
      throw new IllegalArgumentException("previousStatus and newStatus must be different");
    }
  }

  public static ClaimStatusChange initial(UUID id, Claim claim) {
    return new ClaimStatusChange(id, claim.id(), null, claim.status(), claim.createdAt());
  }

  public static ClaimStatusChange transition(
      UUID id, Claim claim, ClaimStatus previousStatus, Instant changedAt) {
    return new ClaimStatusChange(id, claim.id(), previousStatus, claim.status(), changedAt);
  }
}
