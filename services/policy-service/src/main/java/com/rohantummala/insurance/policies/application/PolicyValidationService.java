package com.rohantummala.insurance.policies.application;

import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import com.rohantummala.insurance.policies.domain.SyntheticPolicy;
import com.rohantummala.insurance.policies.infrastructure.SyntheticPolicyCatalog;
import com.rohantummala.insurance.policies.observability.PolicyMetrics;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class PolicyValidationService {

  private final SyntheticPolicyCatalog policyCatalog;
  private final PolicyMetrics metrics;

  public PolicyValidationService(SyntheticPolicyCatalog policyCatalog, PolicyMetrics metrics) {
    this.policyCatalog = policyCatalog;
    this.metrics = metrics;
  }

  public PolicyValidationDecision validate(
      String policyNumber, PolicyClaimType claimType, LocalDate incidentDate) {
    SyntheticPolicy policy = policyCatalog.findByPolicyNumber(policyNumber).orElse(null);
    if (policy == null) {
      return recorded(
          claimType, PolicyValidationDecision.rejected("POLICY_NOT_FOUND", "Policy was not found"));
    }
    if (!policy.active()) {
      return recorded(
          claimType, PolicyValidationDecision.rejected("POLICY_INACTIVE", "Policy is inactive"));
    }
    if (!policy.coversDate(incidentDate)) {
      return recorded(
          claimType,
          PolicyValidationDecision.rejected(
              "INCIDENT_OUTSIDE_COVERAGE", "Incident date is outside the policy period"));
    }
    if (!policy.coversType(claimType)) {
      return recorded(
          claimType,
          PolicyValidationDecision.rejected(
              "CLAIM_TYPE_NOT_COVERED", "Claim type is not covered by the policy"));
    }
    return recorded(claimType, PolicyValidationDecision.accepted());
  }

  private PolicyValidationDecision recorded(
      PolicyClaimType claimType, PolicyValidationDecision decision) {
    metrics.record(claimType, decision);
    return decision;
  }
}
