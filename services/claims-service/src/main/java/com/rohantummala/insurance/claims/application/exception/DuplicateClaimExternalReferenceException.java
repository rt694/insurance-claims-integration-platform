package com.rohantummala.insurance.claims.application.exception;

public final class DuplicateClaimExternalReferenceException extends RuntimeException {

  private final String externalReference;

  public DuplicateClaimExternalReferenceException(String externalReference) {
    super("A claim with external reference %s already exists".formatted(externalReference));
    this.externalReference = externalReference;
  }

  public String getExternalReference() {
    return externalReference;
  }
}
