package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.exception.ClaimSummaryNotFoundException;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimSummaryService {

  private final ClaimRepository claimRepository;
  private final ClaimSummaryRepository summaryRepository;
  private final Clock clock;

  public ClaimSummaryService(
      ClaimRepository claimRepository, ClaimSummaryRepository summaryRepository, Clock clock) {
    this.claimRepository = claimRepository;
    this.summaryRepository = summaryRepository;
    this.clock = clock;
  }

  @Transactional
  public ClaimSummary record(ClaimSummaryCompletedEnvelope event) {
    validateContract(event);
    UUID claimId = event.data().claimId();
    claimRepository.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));

    ClaimSummary summary =
        new ClaimSummary(
            claimId,
            event.eventId(),
            event.data().summary(),
            event.data().missingInformation(),
            event.data().recommendedHumanReviewQueue(),
            event.data().safetyFlags(),
            event.data().generatedAt(),
            clock.instant());
    summaryRepository.save(summary);
    return summary;
  }

  @Transactional(readOnly = true)
  public ClaimSummary getByClaimId(UUID claimId) {
    claimRepository.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));
    return summaryRepository
        .findByClaimId(claimId)
        .orElseThrow(() -> new ClaimSummaryNotFoundException(claimId));
  }

  private void validateContract(ClaimSummaryCompletedEnvelope event) {
    if (!ClaimSummaryCompletedEnvelope.EVENT_TYPE.equals(event.eventType())
        || event.eventVersion() != ClaimSummaryCompletedEnvelope.EVENT_VERSION
        || !ClaimSummaryCompletedEnvelope.AGGREGATE_TYPE.equals(event.aggregateType())) {
      throw new InvalidClaimSummaryEventException("Unsupported claim summary event contract");
    }
    if (!event.aggregateId().equals(event.data().claimId())) {
      throw new InvalidClaimSummaryEventException(
          "Summary event aggregate ID does not match its claim ID");
    }
  }
}
