package com.rohantummala.insurance.claims.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohantummala.insurance.claims.domain.exception.InvalidClaimStatusTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClaimTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T12:00:00Z");

  @Test
  void submittedClaimStartsInSubmittedStatus() {
    Claim claim = submittedClaim();

    assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(claim.createdAt()).isEqualTo(CREATED_AT);
    assertThat(claim.updatedAt()).isEqualTo(CREATED_AT);
  }

  @ParameterizedTest(name = "{0} can transition to {1}")
  @MethodSource("allowedTransitions")
  void allowsConfiguredStatusTransitions(ClaimStatus currentStatus, ClaimStatus requestedStatus) {
    Claim claim = claimWithStatus(currentStatus);
    Instant transitionedAt = CREATED_AT.plusSeconds(60);

    Claim transitionedClaim = claim.transitionTo(requestedStatus, transitionedAt);

    assertThat(transitionedClaim.status()).isEqualTo(requestedStatus);
    assertThat(transitionedClaim.updatedAt()).isEqualTo(transitionedAt);
    assertThat(transitionedClaim.createdAt()).isEqualTo(claim.createdAt());
    assertThat(transitionedClaim.id()).isEqualTo(claim.id());
  }

  @TestFactory
  Stream<DynamicTest> rejectsEveryTransitionThatIsNotConfigured() {
    return Arrays.stream(ClaimStatus.values())
        .flatMap(
            currentStatus ->
                Arrays.stream(ClaimStatus.values())
                    .filter(requestedStatus -> !currentStatus.canTransitionTo(requestedStatus))
                    .map(
                        requestedStatus ->
                            DynamicTest.dynamicTest(
                                currentStatus + " cannot transition to " + requestedStatus,
                                () ->
                                    assertThatThrownBy(
                                            () ->
                                                claimWithStatus(currentStatus)
                                                    .transitionTo(
                                                        requestedStatus,
                                                        CREATED_AT.plusSeconds(60)))
                                        .isInstanceOf(InvalidClaimStatusTransitionException.class)
                                        .hasMessage(
                                            "Cannot transition claim from %s to %s",
                                            currentStatus, requestedStatus))));
  }

  @Test
  void rejectsNegativeEstimatedLoss() {
    assertThatThrownBy(
            () ->
                Claim.submitted(
                    UUID.randomUUID(),
                    "EXT-1001",
                    "POL-2001",
                    "Synthetic Claimant",
                    ClaimType.AUTO,
                    LocalDate.of(2026, 1, 10),
                    "Synthetic incident description",
                    new BigDecimal("-0.01"),
                    CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("estimatedLoss must not be negative");
  }

  @Test
  void rejectsTransitionTimestampBeforeLastUpdate() {
    Claim claim = submittedClaim();

    assertThatThrownBy(
            () -> claim.transitionTo(ClaimStatus.UNDER_REVIEW, CREATED_AT.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("transitionedAt must not be before updatedAt");
  }

  private static Stream<Arguments> allowedTransitions() {
    return Stream.of(
        Arguments.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW),
        Arguments.of(ClaimStatus.SUBMITTED, ClaimStatus.CANCELLED),
        Arguments.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED),
        Arguments.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.DENIED),
        Arguments.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.CANCELLED),
        Arguments.of(ClaimStatus.APPROVED, ClaimStatus.CLOSED),
        Arguments.of(ClaimStatus.DENIED, ClaimStatus.CLOSED));
  }

  private static Claim submittedClaim() {
    return Claim.submitted(
        UUID.fromString("6cab36d0-8dd7-4eaa-a6e3-e3f27ec62b4f"),
        "EXT-1001",
        "POL-2001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"),
        CREATED_AT);
  }

  private static Claim claimWithStatus(ClaimStatus status) {
    Claim submittedClaim = submittedClaim();
    return new Claim(
        submittedClaim.id(),
        submittedClaim.externalReference(),
        submittedClaim.policyNumber(),
        submittedClaim.claimantName(),
        submittedClaim.claimType(),
        submittedClaim.incidentDate(),
        submittedClaim.description(),
        submittedClaim.estimatedLoss(),
        status,
        submittedClaim.createdAt(),
        submittedClaim.updatedAt());
  }
}
