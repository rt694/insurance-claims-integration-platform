package com.rohantummala.insurance.claims.infrastructure.inmemory;

import com.rohantummala.insurance.claims.application.port.InboxEventRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("in-memory")
public class InMemoryInboxEventRepository implements InboxEventRepository {

  private final Set<InboxKey> processedEvents = ConcurrentHashMap.newKeySet();

  @Override
  public boolean registerIfFirst(
      UUID eventId, String consumerName, String eventType, UUID aggregateId, Instant processedAt) {
    return processedEvents.add(new InboxKey(consumerName, eventId));
  }

  private record InboxKey(String consumerName, UUID eventId) {}
}
