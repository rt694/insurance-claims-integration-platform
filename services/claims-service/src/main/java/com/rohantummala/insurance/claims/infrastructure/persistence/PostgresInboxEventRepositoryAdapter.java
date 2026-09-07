package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.application.port.InboxEventRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!in-memory")
public class PostgresInboxEventRepositoryAdapter implements InboxEventRepository {

  private final JdbcClient jdbcClient;

  public PostgresInboxEventRepositoryAdapter(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public boolean registerIfFirst(
      UUID eventId, String consumerName, String eventType, UUID aggregateId, Instant processedAt) {
    return jdbcClient
            .sql(
                """
                INSERT INTO processed_inbox_events (
                    consumer_name, event_id, event_type, aggregate_id, processed_at
                ) VALUES (
                    :consumerName, :eventId, :eventType, :aggregateId, :processedAt
                )
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
            .param("consumerName", consumerName)
            .param("eventId", eventId)
            .param("eventType", eventType)
            .param("aggregateId", aggregateId)
            .param("processedAt", processedAt.atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }
}
