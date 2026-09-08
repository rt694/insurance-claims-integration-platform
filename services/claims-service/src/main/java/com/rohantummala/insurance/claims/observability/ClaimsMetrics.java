package com.rohantummala.insurance.claims.observability;

import com.rohantummala.insurance.claims.application.service.OutboxPublicationResult;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ClaimsMetrics {

  private static final String SUBMISSIONS = "insurance.claims.submissions";
  private static final String POLICY_VALIDATION = "insurance.claims.policy.validation";
  private static final String OUTBOX_PUBLICATIONS = "insurance.claims.outbox.publications";
  private static final String SUMMARY_RESULTS = "insurance.claims.summary.results";

  private final MeterRegistry registry;

  public ClaimsMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public Timer.Sample startPolicyValidation() {
    return Timer.start(registry);
  }

  public void recordPolicyValidation(Timer.Sample sample, String outcome) {
    sample.stop(
        Timer.builder(POLICY_VALIDATION)
            .description("Time spent validating a claim with the policy service")
            .tag("outcome", outcome)
            .register(registry));
  }

  public void recordSubmission(ClaimType claimType, String outcome) {
    registry
        .counter(
            SUBMISSIONS,
            "claim_type",
            claimType.name().toLowerCase(Locale.ROOT),
            "outcome",
            outcome)
        .increment();
  }

  public void recordOutboxBatch(OutboxPublicationResult result) {
    increment(OUTBOX_PUBLICATIONS, "published", result.published());
    increment(OUTBOX_PUBLICATIONS, "failed", result.failed());
  }

  public void recordSummaryResult(String outcome) {
    increment(SUMMARY_RESULTS, outcome, 1);
  }

  private void increment(String name, String outcome, int amount) {
    if (amount > 0) {
      registry.counter(name, "outcome", outcome).increment(amount);
    }
  }
}
