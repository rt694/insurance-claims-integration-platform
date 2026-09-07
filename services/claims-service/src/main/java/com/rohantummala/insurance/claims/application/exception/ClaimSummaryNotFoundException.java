package com.rohantummala.insurance.claims.application.exception;

import java.util.UUID;

public class ClaimSummaryNotFoundException extends RuntimeException {

  public ClaimSummaryNotFoundException(UUID claimId) {
    super("A summary is not yet available for claim " + claimId);
  }
}
