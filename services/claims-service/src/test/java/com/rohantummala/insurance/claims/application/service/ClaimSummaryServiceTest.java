package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedPayload;
import com.rohantummala.insurance.claims.application.exception.ClaimSummaryNotFoundException;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimSummaryServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

  private final ClaimRepository claimRepository = mock(ClaimRepository.class);
  private final ClaimSummaryRepository summaryRepository = mock(ClaimSummaryRepository.class);
  private final ClaimSummaryService service =
      new ClaimSummaryService(claimRepository, summaryRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void recordsReviewerAssistanceWithoutChangingTheClaim() {
    UUID claimId = UUID.randomUUID();
    Claim claim = claim(claimId);
    ClaimSummaryCompletedEnvelope event = event(claimId, claimId);
    when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

    ClaimSummary result = service.record(event);

    assertThat(result.claimId()).isEqualTo(claimId);
    assertThat(result.sourceEventId()).isEqualTo(event.eventId());
    assertThat(result.summary()).isEqualTo("Vehicle damage requires human review.");
    assertThat(result.recommendedHumanReviewQueue()).isEqualTo(HumanReviewQueue.STANDARD_REVIEW);
    assertThat(result.receivedAt()).isEqualTo(NOW);
    verify(summaryRepository).save(result);
    verify(claimRepository, never()).update(claim);
  }

  @Test
  void rejectsAnEnvelopeWhoseAggregateDoesNotMatchItsPayload() {
    ClaimSummaryCompletedEnvelope event = event(UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> service.record(event))
        .isInstanceOf(InvalidClaimSummaryEventException.class)
        .hasMessageContaining("aggregate ID");
    verify(summaryRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void distinguishesAnExistingClaimWithoutASummary() {
    UUID claimId = UUID.randomUUID();
    when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim(claimId)));
    when(summaryRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByClaimId(claimId))
        .isInstanceOf(ClaimSummaryNotFoundException.class)
        .hasMessageContaining(claimId.toString());
  }

  private ClaimSummaryCompletedEnvelope event(UUID aggregateId, UUID payloadClaimId) {
    Instant generatedAt = NOW.minusSeconds(5);
    return new ClaimSummaryCompletedEnvelope(
        UUID.randomUUID(),
        ClaimSummaryCompletedEnvelope.EVENT_TYPE,
        ClaimSummaryCompletedEnvelope.EVENT_VERSION,
        ClaimSummaryCompletedEnvelope.AGGREGATE_TYPE,
        aggregateId,
        "summary-test-correlation",
        generatedAt,
        new ClaimSummaryCompletedPayload(
            payloadClaimId,
            "Vehicle damage requires human review.",
            List.of("Police report"),
            HumanReviewQueue.STANDARD_REVIEW,
            List.of(),
            generatedAt));
  }

  private Claim claim(UUID claimId) {
    return Claim.submitted(
        claimId,
        "EXT-SUMMARY-UNIT",
        "POL-AUTO-1001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident",
        new BigDecimal("1250.00"),
        NOW.minusSeconds(10));
  }
}
