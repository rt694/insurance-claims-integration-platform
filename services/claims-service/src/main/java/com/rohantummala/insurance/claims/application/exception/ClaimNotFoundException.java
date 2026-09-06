package com.rohantummala.insurance.claims.application.exception;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {

  public ClaimNotFoundException(UUID id) {
    super("Claim %s was not found".formatted(id));
  }
}
