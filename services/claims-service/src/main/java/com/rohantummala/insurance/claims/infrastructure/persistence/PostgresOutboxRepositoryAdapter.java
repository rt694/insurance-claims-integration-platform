package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.application.event.EventEnvelope;
import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;
import com.rohantummala.insurance.claims.application.port.OutboxPublicationRepository;
import com.rohantummala.insurance.claims.application.port.OutboxRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("!in-memory")
public class PostgresOutboxRepositoryAdapter
    implements OutboxRepository, OutboxPublicationRepository {

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

  @Override
  public List<OutboxEventPublication> claimReadyBatch(
      int batchSize, Instant now, Instant leaseUntil) {
    return jdbcClient
        .sql(
            """
            WITH ready_events AS (
                SELECT event_id
                FROM outbox_events
                WHERE status IN ('PENDING', 'FAILED', 'PUBLISHING')
                  AND available_at <= :now
                ORDER BY available_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE outbox_events AS event
            SET status = 'PUBLISHING',
                attempt_count = event.attempt_count + 1,
                available_at = :leaseUntil,
                last_error = NULL
            FROM ready_events
            WHERE event.event_id = ready_events.event_id
            RETURNING event.event_id,
                      event.aggregate_type,
                      event.aggregate_id,
                      event.event_type,
                      event.event_version,
                      event.correlation_id,
                      event.payload::text,
                      event.attempt_count
            """)
        .param("now", now.atOffset(ZoneOffset.UTC))
        .param("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
        .param("batchSize", batchSize)
        .query(
            (resultSet, rowNumber) ->
                new OutboxEventPublication(
                    resultSet.getObject("event_id", UUID.class),
                    resultSet.getString("aggregate_type"),
                    resultSet.getObject("aggregate_id", UUID.class),
                    resultSet.getString("event_type"),
                    resultSet.getInt("event_version"),
                    resultSet.getString("correlation_id"),
                    resultSet.getString("payload"),
                    resultSet.getInt("attempt_count")))
        .list();
  }

  @Override
  public void markPublished(UUID eventId, int attemptNumber, Instant publishedAt) {
    int updatedRows =
        jdbcClient
            .sql(
                """
                UPDATE outbox_events
                SET status = 'PUBLISHED',
                    published_at = :publishedAt,
                    available_at = :publishedAt,
                    last_error = NULL
                WHERE event_id = :eventId
                  AND status = 'PUBLISHING'
                  AND attempt_count = :attemptNumber
                """)
            .param("eventId", eventId)
            .param("attemptNumber", attemptNumber)
            .param("publishedAt", publishedAt.atOffset(ZoneOffset.UTC))
            .update();
    requireSingleUpdatedRow(updatedRows, eventId, attemptNumber, "published");
  }

  @Override
  public void markFailed(UUID eventId, int attemptNumber, Instant retryAt, String failureReason) {
    int updatedRows =
        jdbcClient
            .sql(
                """
                UPDATE outbox_events
                SET status = 'FAILED',
                    available_at = :retryAt,
                    last_error = :failureReason
                WHERE event_id = :eventId
                  AND status = 'PUBLISHING'
                  AND attempt_count = :attemptNumber
                """)
            .param("eventId", eventId)
            .param("attemptNumber", attemptNumber)
            .param("retryAt", retryAt.atOffset(ZoneOffset.UTC))
            .param("failureReason", failureReason)
            .update();
    requireSingleUpdatedRow(updatedRows, eventId, attemptNumber, "failed");
  }

  private String serialize(EventEnvelope<?> event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialize outbox event", exception);
    }
  }

  private void requireSingleUpdatedRow(
      int updatedRows, UUID eventId, int attemptNumber, String targetState) {
    if (updatedRows != 1) {
      throw new IllegalStateException(
          "Could not mark outbox event %s attempt %d as %s"
              .formatted(eventId, attemptNumber, targetState));
    }
  }
}
