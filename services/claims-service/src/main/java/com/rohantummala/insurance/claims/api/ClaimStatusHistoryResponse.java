package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import java.time.Instant;
import java.util.UUID;

public record ClaimStatusHistoryResponse(
    UUID id, UUID claimId, ClaimStatus previousStatus, ClaimStatus newStatus, Instant changedAt) {

  static ClaimStatusHistoryResponse from(ClaimStatusChange statusChange) {
    return new ClaimStatusHistoryResponse(
        statusChange.id(),
        statusChange.claimId(),
        statusChange.previousStatus(),
        statusChange.newStatus(),
        statusChange.changedAt());
  }
}
