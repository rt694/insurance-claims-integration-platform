package com.rohantummala.insurance.claims.domain.exception;

import com.rohantummala.insurance.claims.domain.model.ClaimStatus;

public final class InvalidClaimStatusTransitionException extends RuntimeException {

  private final ClaimStatus currentStatus;
  private final ClaimStatus requestedStatus;

  public InvalidClaimStatusTransitionException(
      ClaimStatus currentStatus, ClaimStatus requestedStatus) {
    super("Cannot transition claim from %s to %s".formatted(currentStatus, requestedStatus));
    this.currentStatus = currentStatus;
    this.requestedStatus = requestedStatus;
  }

  public ClaimStatus getCurrentStatus() {
    return currentStatus;
  }

  public ClaimStatus getRequestedStatus() {
    return requestedStatus;
  }
}
