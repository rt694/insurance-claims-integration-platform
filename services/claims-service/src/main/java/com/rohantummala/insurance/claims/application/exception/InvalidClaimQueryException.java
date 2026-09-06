package com.rohantummala.insurance.claims.application.exception;

public class InvalidClaimQueryException extends RuntimeException {

  public InvalidClaimQueryException(String message) {
    super(message);
  }
}
