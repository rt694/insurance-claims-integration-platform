package com.rohantummala.insurance.policies.application;

import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import com.rohantummala.insurance.policies.domain.SyntheticPolicy;
import com.rohantummala.insurance.policies.infrastructure.SyntheticPolicyCatalog;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class PolicyValidationService {

  private final SyntheticPolicyCatalog policyCatalog;

  public PolicyValidationService(SyntheticPolicyCatalog policyCatalog) {
    this.policyCatalog = policyCatalog;
  }

  public PolicyValidationDecision validate(
      String policyNumber, PolicyClaimType claimType, LocalDate incidentDate) {
    SyntheticPolicy policy = policyCatalog.findByPolicyNumber(policyNumber).orElse(null);
    if (policy == null) {
      return PolicyValidationDecision.rejected("POLICY_NOT_FOUND", "Policy was not found");
    }
    if (!policy.active()) {
      return PolicyValidationDecision.rejected("POLICY_INACTIVE", "Policy is inactive");
    }
    if (!policy.coversDate(incidentDate)) {
      return PolicyValidationDecision.rejected(
          "INCIDENT_OUTSIDE_COVERAGE", "Incident date is outside the policy period");
    }
    if (!policy.coversType(claimType)) {
      return PolicyValidationDecision.rejected(
          "CLAIM_TYPE_NOT_COVERED", "Claim type is not covered by the policy");
    }
    return PolicyValidationDecision.accepted();
  }
}
