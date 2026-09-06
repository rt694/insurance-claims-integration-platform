package com.rohantummala.insurance.claims.application.exception;

public class PolicyServiceUnavailableException extends RuntimeException {

  public PolicyServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public PolicyServiceUnavailableException(String message) {
    super(message);
  }
}
