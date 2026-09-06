package com.rohantummala.insurance.claims.infrastructure.policy;

import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("in-memory")
public class InMemoryPolicyValidationAdapter implements PolicyValidationPort {

  @Override
  public PolicyValidationResult validate(PolicyValidationRequest request) {
    return new PolicyValidationResult(true, "TEST_POLICY_VALID", "Test policy accepted");
  }
}
