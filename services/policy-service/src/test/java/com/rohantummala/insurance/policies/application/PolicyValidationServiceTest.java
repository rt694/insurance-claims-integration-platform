package com.rohantummala.insurance.policies.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import com.rohantummala.insurance.policies.infrastructure.SyntheticPolicyCatalog;
import com.rohantummala.insurance.policies.observability.PolicyMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyValidationServiceTest {

  private PolicyValidationService service;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service =
        new PolicyValidationService(new SyntheticPolicyCatalog(), new PolicyMetrics(meterRegistry));
  }

  @Test
  void acceptsAnActivePolicyThatCoversDateAndType() {
    PolicyValidationDecision result =
        service.validate("POL-AUTO-1001", PolicyClaimType.AUTO, LocalDate.of(2026, 1, 10));

    assertThat(result.valid()).isTrue();
    assertThat(result.code()).isEqualTo("VALID");
    assertThat(validationCount("auto", "accepted", "valid")).isEqualTo(1);
  }

  @Test
  void rejectsMissingInactiveOutOfPeriodAndWrongTypePolicies() {
    assertDecision("UNKNOWN", PolicyClaimType.AUTO, LocalDate.of(2026, 1, 10), "POLICY_NOT_FOUND");
    assertDecision(
        "POL-INACTIVE-9001", PolicyClaimType.AUTO, LocalDate.of(2026, 1, 10), "POLICY_INACTIVE");
    assertDecision(
        "POL-AUTO-1001",
        PolicyClaimType.AUTO,
        LocalDate.of(2024, 12, 31),
        "INCIDENT_OUTSIDE_COVERAGE");
    assertDecision(
        "POL-AUTO-1001",
        PolicyClaimType.PROPERTY,
        LocalDate.of(2026, 1, 10),
        "CLAIM_TYPE_NOT_COVERED");
  }

  private void assertDecision(
      String number, PolicyClaimType type, LocalDate date, String expectedCode) {
    PolicyValidationDecision decision = service.validate(number, type, date);
    assertThat(decision.valid()).isFalse();
    assertThat(decision.code()).isEqualTo(expectedCode);
    assertThat(validationCount(type.name().toLowerCase(), "rejected", expectedCode.toLowerCase()))
        .isEqualTo(1);
  }

  private double validationCount(String claimType, String outcome, String reason) {
    return meterRegistry
        .get("insurance.policy.validations")
        .tags("claim_type", claimType, "outcome", outcome, "reason", reason)
        .counter()
        .count();
  }
}
