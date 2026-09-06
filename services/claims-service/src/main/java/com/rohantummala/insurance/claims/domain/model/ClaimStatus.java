package com.rohantummala.insurance.claims.domain.model;

import java.util.Objects;

public enum ClaimStatus {
  SUBMITTED,
  UNDER_REVIEW,
  APPROVED,
  DENIED,
  CANCELLED,
  CLOSED;

  public boolean canTransitionTo(ClaimStatus requestedStatus) {
    Objects.requireNonNull(requestedStatus, "requestedStatus must not be null");

    return switch (this) {
      case SUBMITTED -> requestedStatus == UNDER_REVIEW || requestedStatus == CANCELLED;
      case UNDER_REVIEW ->
          requestedStatus == APPROVED || requestedStatus == DENIED || requestedStatus == CANCELLED;
      case APPROVED, DENIED -> requestedStatus == CLOSED;
      case CANCELLED, CLOSED -> false;
    };
  }
}
