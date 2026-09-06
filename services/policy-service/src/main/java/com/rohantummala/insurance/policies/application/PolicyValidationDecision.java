package com.rohantummala.insurance.policies.application;

public record PolicyValidationDecision(boolean valid, String code, String message) {

  static PolicyValidationDecision accepted() {
    return new PolicyValidationDecision(true, "VALID", "Policy covers this claim");
  }

  static PolicyValidationDecision rejected(String code, String message) {
    return new PolicyValidationDecision(false, code, message);
  }
}
