package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.exception.ClaimSummaryNotFoundException;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.application.port.InboxEventRepository;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimSummaryService {

  static final String CONSUMER_NAME = "claim-summary-result-consumer";

  private final ClaimRepository claimRepository;
  private final ClaimSummaryRepository summaryRepository;
  private final InboxEventRepository inboxEventRepository;
  private final Clock clock;

  public ClaimSummaryService(
      ClaimRepository claimRepository,
      ClaimSummaryRepository summaryRepository,
      InboxEventRepository inboxEventRepository,
      Clock clock) {
    this.claimRepository = claimRepository;
    this.summaryRepository = summaryRepository;
    this.inboxEventRepository = inboxEventRepository;
    this.clock = clock;
  }

  @Transactional
  public SummaryProcessingResult record(ClaimSummaryCompletedEnvelope event) {
    validateContract(event);
    UUID claimId = event.data().claimId();
    claimRepository.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));

    Instant processedAt = clock.instant();
    if (!inboxEventRepository.registerIfFirst(
        event.eventId(), CONSUMER_NAME, event.eventType(), event.aggregateId(), processedAt)) {
      return SummaryProcessingResult.duplicate();
    }

    ClaimSummary summary =
        new ClaimSummary(
            claimId,
            event.eventId(),
            event.data().summary(),
            event.data().missingInformation(),
            event.data().recommendedHumanReviewQueue(),
            event.data().safetyFlags(),
            event.data().generatedAt(),
            processedAt);
    summaryRepository.save(summary);
    return SummaryProcessingResult.stored(summary);
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
