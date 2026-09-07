package com.rohantummala.insurance.claims.application.exception;

public class InvalidClaimSummaryEventException extends RuntimeException {

  public InvalidClaimSummaryEventException(String message) {
    super(message);
  }

  public InvalidClaimSummaryEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
