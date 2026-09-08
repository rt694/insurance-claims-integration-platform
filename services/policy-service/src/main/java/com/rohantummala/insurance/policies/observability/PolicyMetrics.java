package com.rohantummala.insurance.policies.observability;

import com.rohantummala.insurance.policies.application.PolicyValidationDecision;
import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PolicyMetrics {

  private static final String VALIDATIONS = "insurance.policy.validations";

  private final MeterRegistry registry;

  public PolicyMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(PolicyClaimType claimType, PolicyValidationDecision decision) {
    registry
        .counter(
            VALIDATIONS,
            "claim_type",
            claimType.name().toLowerCase(Locale.ROOT),
            "outcome",
            decision.valid() ? "accepted" : "rejected",
            "reason",
            decision.code().toLowerCase(Locale.ROOT))
        .increment();
  }
}
