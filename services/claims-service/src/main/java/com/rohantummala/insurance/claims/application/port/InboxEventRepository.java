package com.rohantummala.insurance.claims.application.port;

import java.time.Instant;
import java.util.UUID;

public interface InboxEventRepository {

  boolean registerIfFirst(
      UUID eventId, String consumerName, String eventType, UUID aggregateId, Instant processedAt);
}
