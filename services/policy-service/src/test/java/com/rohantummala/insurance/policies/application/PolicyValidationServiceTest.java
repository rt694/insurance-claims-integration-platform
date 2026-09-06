package com.rohantummala.insurance.policies.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import com.rohantummala.insurance.policies.infrastructure.SyntheticPolicyCatalog;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyValidationServiceTest {

  private PolicyValidationService service;

  @BeforeEach
  void setUp() {
    service = new PolicyValidationService(new SyntheticPolicyCatalog());
  }

  @Test
  void acceptsAnActivePolicyThatCoversDateAndType() {
    PolicyValidationDecision result =
        service.validate("POL-AUTO-1001", PolicyClaimType.AUTO, LocalDate.of(2026, 1, 10));

    assertThat(result.valid()).isTrue();
    assertThat(result.code()).isEqualTo("VALID");
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
  }
}
