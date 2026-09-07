package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;
import com.rohantummala.insurance.claims.application.port.EventPublisher;
import com.rohantummala.insurance.claims.application.port.OutboxPublicationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxPublicationServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
  private static final Duration LEASE_DURATION = Duration.ofSeconds(15);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

  private final OutboxPublicationRepository repository = mock(OutboxPublicationRepository.class);
  private final EventPublisher eventPublisher = mock(EventPublisher.class);
  private final OutboxPublicationService service =
      new OutboxPublicationService(
          repository,
          eventPublisher,
          Clock.fixed(NOW, ZoneOffset.UTC),
          25,
          LEASE_DURATION,
          RETRY_DELAY);

  @BeforeEach
  void claimDefaultEmptyBatch() {
    when(repository.claimReadyBatch(25, NOW, NOW.plus(LEASE_DURATION))).thenReturn(List.of());
  }

  @Test
  void publishesAndCompletesEveryClaimedEvent() {
    OutboxEventPublication first = event(1);
    OutboxEventPublication second = event(1);
    when(repository.claimReadyBatch(25, NOW, NOW.plus(LEASE_DURATION)))
        .thenReturn(List.of(first, second));

    OutboxPublicationResult result = service.publishNextBatch();

    assertThat(result).isEqualTo(new OutboxPublicationResult(2, 2, 0));
    verify(eventPublisher).publish(first);
    verify(eventPublisher).publish(second);
    verify(repository).markPublished(first.eventId(), 1, NOW);
    verify(repository).markPublished(second.eventId(), 1, NOW);
  }

  @Test
  void recordsAConfirmFailureAndContinuesWithTheRestOfTheBatch() {
    OutboxEventPublication failedEvent = event(2);
    OutboxEventPublication successfulEvent = event(1);
    when(repository.claimReadyBatch(25, NOW, NOW.plus(LEASE_DURATION)))
        .thenReturn(List.of(failedEvent, successfulEvent));
    doThrow(new IllegalStateException("broker unavailable"))
        .when(eventPublisher)
        .publish(failedEvent);

    OutboxPublicationResult result = service.publishNextBatch();

    assertThat(result).isEqualTo(new OutboxPublicationResult(2, 1, 1));
    verify(repository)
        .markFailed(
            failedEvent.eventId(),
            2,
            NOW.plus(RETRY_DELAY),
            "IllegalStateException: broker unavailable");
    verify(repository).markPublished(successfulEvent.eventId(), 1, NOW);
  }

  @Test
  void doesNothingWhenNoEventIsReady() {
    OutboxPublicationResult result = service.publishNextBatch();

    assertThat(result).isEqualTo(new OutboxPublicationResult(0, 0, 0));
    verifyNoInteractions(eventPublisher);
  }

  private OutboxEventPublication event(int attemptNumber) {
    UUID eventId = UUID.randomUUID();
    UUID aggregateId = UUID.randomUUID();
    return new OutboxEventPublication(
        eventId,
        "claim",
        aggregateId,
        "claim.submitted",
        1,
        "correlation-" + eventId,
        "{\"eventId\":\"%s\"}".formatted(eventId),
        attemptNumber);
  }
}
