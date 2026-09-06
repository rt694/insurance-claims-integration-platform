package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;

public interface PolicyValidationPort {

  PolicyValidationResult validate(PolicyValidationRequest request);
}
