package com.rohantummala.insurance.claims.infrastructure.inmemory;

import com.rohantummala.insurance.claims.application.event.EventEnvelope;
import com.rohantummala.insurance.claims.application.port.OutboxRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("in-memory")
public class InMemoryOutboxRepository implements OutboxRepository {

  private final List<EventEnvelope<?>> events = new CopyOnWriteArrayList<>();

  @Override
  public void append(EventEnvelope<?> event) {
    events.add(event);
  }
}
