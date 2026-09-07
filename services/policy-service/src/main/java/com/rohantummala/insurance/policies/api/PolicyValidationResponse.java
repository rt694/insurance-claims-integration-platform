package com.rohantummala.insurance.policies.api;

import com.rohantummala.insurance.policies.application.PolicyValidationDecision;

public record PolicyValidationResponse(boolean valid, String code, String message) {

  static PolicyValidationResponse from(PolicyValidationDecision decision) {
    return new PolicyValidationResponse(decision.valid(), decision.code(), decision.message());
  }
}
