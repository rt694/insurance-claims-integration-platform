package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxPublicationRepository {

  List<OutboxEventPublication> claimReadyBatch(int batchSize, Instant now, Instant leaseUntil);

  void markPublished(UUID eventId, int attemptNumber, Instant publishedAt);

  void markFailed(UUID eventId, int attemptNumber, Instant retryAt, String failureReason);
}
