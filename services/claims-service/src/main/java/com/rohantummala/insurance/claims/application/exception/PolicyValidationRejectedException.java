package com.rohantummala.insurance.claims.application.exception;

public class PolicyValidationRejectedException extends RuntimeException {

  private final String code;

  public PolicyValidationRejectedException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
