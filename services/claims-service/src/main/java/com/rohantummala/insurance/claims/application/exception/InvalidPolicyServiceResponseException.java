package com.rohantummala.insurance.claims.application.exception;

public class InvalidPolicyServiceResponseException extends RuntimeException {

  public InvalidPolicyServiceResponseException(String message) {
    super(message);
  }

  public InvalidPolicyServiceResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
