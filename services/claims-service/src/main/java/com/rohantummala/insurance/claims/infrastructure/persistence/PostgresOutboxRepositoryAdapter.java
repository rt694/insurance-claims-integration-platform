package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.application.event.EventEnvelope;
import com.rohantummala.insurance.claims.application.port.OutboxRepository;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("!in-memory")
public class PostgresOutboxRepositoryAdapter implements OutboxRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public PostgresOutboxRepositoryAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void append(EventEnvelope<?> event) {
    jdbcClient
        .sql(
            """
            INSERT INTO outbox_events (
                event_id, aggregate_type, aggregate_id, event_type, event_version,
                correlation_id, payload, status, attempt_count, available_at, created_at
            ) VALUES (
                :eventId, :aggregateType, :aggregateId, :eventType, :eventVersion,
                :correlationId, CAST(:payload AS jsonb), 'PENDING', 0, :availableAt, :createdAt
            )
            """)
        .param("eventId", event.eventId())
        .param("aggregateType", event.aggregateType())
        .param("aggregateId", event.aggregateId())
        .param("eventType", event.eventType())
        .param("eventVersion", event.eventVersion())
        .param("correlationId", event.correlationId())
        .param("payload", serialize(event))
        .param("availableAt", event.occurredAt().atOffset(ZoneOffset.UTC))
        .param("createdAt", event.occurredAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  private String serialize(EventEnvelope<?> event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialize outbox event", exception);
    }
  }
}
