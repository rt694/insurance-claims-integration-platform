package com.rohantummala.insurance.claims.application.query;

import com.rohantummala.insurance.claims.application.exception.InvalidClaimQueryException;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;

public record ClaimQuery(int page, int size, ClaimStatus status, ClaimType claimType) {

  public static final int MAX_PAGE_SIZE = 100;

  public ClaimQuery {
    if (page < 0) {
      throw new InvalidClaimQueryException("page must be greater than or equal to 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidClaimQueryException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
  }
}
