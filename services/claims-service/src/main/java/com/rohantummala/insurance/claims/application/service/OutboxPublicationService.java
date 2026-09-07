package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;
import com.rohantummala.insurance.claims.application.port.EventPublisher;
import com.rohantummala.insurance.claims.application.port.OutboxPublicationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboxPublicationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublicationService.class);
  private static final int MAX_FAILURE_REASON_LENGTH = 1_000;

  private final OutboxPublicationRepository repository;
  private final EventPublisher eventPublisher;
  private final Clock clock;
  private final int batchSize;
  private final Duration leaseDuration;
  private final Duration retryDelay;

  public OutboxPublicationService(
      OutboxPublicationRepository repository,
      EventPublisher eventPublisher,
      Clock clock,
      int batchSize,
      Duration leaseDuration,
      Duration retryDelay) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.batchSize = batchSize;
    this.leaseDuration = leaseDuration;
    this.retryDelay = retryDelay;
  }

  public OutboxPublicationResult publishNextBatch() {
    Instant claimedAt = clock.instant();
    var events = repository.claimReadyBatch(batchSize, claimedAt, claimedAt.plus(leaseDuration));
    int published = 0;
    int failed = 0;

    for (OutboxEventPublication event : events) {
      try {
        eventPublisher.publish(event);
      } catch (RuntimeException exception) {
        failed++;
        repository.markFailed(
            event.eventId(),
            event.attemptNumber(),
            clock.instant().plus(retryDelay),
            failureReason(exception));
        LOGGER.warn(
            "Outbox publication failed eventId={} attemptNumber={} failureType={}",
            event.eventId(),
            event.attemptNumber(),
            exception.getClass().getSimpleName());
        continue;
      }
      repository.markPublished(event.eventId(), event.attemptNumber(), clock.instant());
      published++;
    }

    return new OutboxPublicationResult(events.size(), published, failed);
  }

  private String failureReason(RuntimeException exception) {
    String message = exception.getMessage();
    String reason =
        message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getClass().getSimpleName() + ": " + message;
    return reason.substring(0, Math.min(reason.length(), MAX_FAILURE_REASON_LENGTH));
  }
}
