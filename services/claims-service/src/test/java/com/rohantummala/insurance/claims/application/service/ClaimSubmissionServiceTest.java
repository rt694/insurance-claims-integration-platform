package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.exception.PolicyServiceUnavailableException;
import com.rohantummala.insurance.claims.application.exception.PolicyValidationRejectedException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import com.rohantummala.insurance.claims.observability.ClaimsMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimSubmissionServiceTest {

  @Mock private PolicyValidationPort policyValidationPort;

  @Mock private ClaimCreationService claimCreationService;

  private ClaimSubmissionService service;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service =
        new ClaimSubmissionService(
            policyValidationPort, claimCreationService, new ClaimsMetrics(meterRegistry));
  }

  @Test
  void validatesThePolicyBeforeCreatingTheClaim() {
    SubmitClaimCommand command = command("EXT-1001");
    Claim claim = claim(command);
    when(policyValidationPort.validate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PolicyValidationResult(true, "VALID", "Policy covers this claim"));
    when(claimCreationService.create(command)).thenReturn(claim);

    assertThat(service.submit(command)).isEqualTo(claim);

    ArgumentCaptor<PolicyValidationRequest> requestCaptor =
        ArgumentCaptor.forClass(PolicyValidationRequest.class);
    verify(policyValidationPort).validate(requestCaptor.capture());
    assertThat(requestCaptor.getValue().policyNumber()).isEqualTo("POL-AUTO-1001");
    assertThat(requestCaptor.getValue().claimType()).isEqualTo(ClaimType.AUTO);
    assertThat(requestCaptor.getValue().incidentDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    verify(claimCreationService).create(command);
    assertThat(submissionCount("auto", "accepted")).isEqualTo(1);
    assertThat(policyValidationCount("accepted")).isEqualTo(1);
  }

  @Test
  void rejectsAClaimWhenThePolicyDoesNotCoverIt() {
    SubmitClaimCommand command = command("EXT-1001");
    when(policyValidationPort.validate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new PolicyValidationResult(
                false, "CLAIM_TYPE_NOT_COVERED", "Claim type is not covered by the policy"));

    assertThatThrownBy(() -> service.submit(command))
        .isInstanceOf(PolicyValidationRejectedException.class)
        .hasMessage("Claim type is not covered by the policy");
    verify(claimCreationService, never()).create(command);
    assertThat(submissionCount("auto", "rejected")).isEqualTo(1);
    assertThat(policyValidationCount("rejected")).isEqualTo(1);
  }

  @Test
  void doesNotCreateAClaimWhenPolicyValidationIsUnavailable() {
    SubmitClaimCommand command = command("EXT-1001");
    when(policyValidationPort.validate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new PolicyServiceUnavailableException("Policy service unavailable"));

    assertThatThrownBy(() -> service.submit(command))
        .isInstanceOf(PolicyServiceUnavailableException.class);
    verify(claimCreationService, never()).create(command);
    assertThat(submissionCount("auto", "error")).isEqualTo(1);
    assertThat(policyValidationCount("error")).isEqualTo(1);
  }

  @Test
  void recordsDuplicateSubmissionsSeparatelyFromTechnicalErrors() {
    SubmitClaimCommand command = command("EXT-1001");
    when(policyValidationPort.validate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PolicyValidationResult(true, "VALID", "Policy covers this claim"));
    when(claimCreationService.create(command))
        .thenThrow(new DuplicateClaimExternalReferenceException(command.externalReference()));

    assertThatThrownBy(() -> service.submit(command))
        .isInstanceOf(DuplicateClaimExternalReferenceException.class);

    assertThat(submissionCount("auto", "duplicate")).isEqualTo(1);
    assertThat(policyValidationCount("accepted")).isEqualTo(1);
  }

  private double submissionCount(String claimType, String outcome) {
    return meterRegistry
        .get("insurance.claims.submissions")
        .tags("claim_type", claimType, "outcome", outcome)
        .counter()
        .count();
  }

  private long policyValidationCount(String outcome) {
    return meterRegistry
        .get("insurance.claims.policy.validation")
        .tag("outcome", outcome)
        .timer()
        .count();
  }

  private SubmitClaimCommand command(String externalReference) {
    return new SubmitClaimCommand(
        externalReference,
        "POL-AUTO-1001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"));
  }

  private Claim claim(SubmitClaimCommand command) {
    Instant now = Instant.parse("2026-01-15T12:00:00Z");
    return Claim.submitted(
        UUID.randomUUID(),
        command.externalReference(),
        command.policyNumber(),
        command.claimantName(),
        command.claimType(),
        command.incidentDate(),
        command.description(),
        command.estimatedLoss(),
        now);
  }
}
