package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("!in-memory")
public class PostgresClaimSummaryRepositoryAdapter implements ClaimSummaryRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public PostgresClaimSummaryRepositoryAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(ClaimSummary summary) {
    jdbcClient
        .sql(
            """
            INSERT INTO claim_summaries (
                claim_id, source_event_id, summary, missing_information,
                recommended_human_review_queue, safety_flags, generated_at, received_at
            ) VALUES (
                :claimId, :sourceEventId, :summary, CAST(:missingInformation AS jsonb),
                :reviewQueue, CAST(:safetyFlags AS jsonb), :generatedAt, :receivedAt
            )
            ON CONFLICT (claim_id) DO UPDATE
            SET source_event_id = EXCLUDED.source_event_id,
                summary = EXCLUDED.summary,
                missing_information = EXCLUDED.missing_information,
                recommended_human_review_queue = EXCLUDED.recommended_human_review_queue,
                safety_flags = EXCLUDED.safety_flags,
                generated_at = EXCLUDED.generated_at,
                received_at = EXCLUDED.received_at
            WHERE claim_summaries.generated_at <= EXCLUDED.generated_at
            """)
        .param("claimId", summary.claimId())
        .param("sourceEventId", summary.sourceEventId())
        .param("summary", summary.summary())
        .param("missingInformation", serialize(summary.missingInformation()))
        .param("reviewQueue", summary.recommendedHumanReviewQueue().name())
        .param("safetyFlags", serialize(summary.safetyFlags()))
        .param("generatedAt", summary.generatedAt().atOffset(ZoneOffset.UTC))
        .param("receivedAt", summary.receivedAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public Optional<ClaimSummary> findByClaimId(UUID claimId) {
    return jdbcClient
        .sql(
            """
            SELECT claim_id, source_event_id, summary, missing_information::text,
                   recommended_human_review_queue, safety_flags::text,
                   generated_at, received_at
            FROM claim_summaries
            WHERE claim_id = :claimId
            """)
        .param("claimId", claimId)
        .query(
            (resultSet, rowNumber) ->
                new ClaimSummary(
                    resultSet.getObject("claim_id", UUID.class),
                    resultSet.getObject("source_event_id", UUID.class),
                    resultSet.getString("summary"),
                    deserialize(resultSet.getString("missing_information")),
                    HumanReviewQueue.valueOf(resultSet.getString("recommended_human_review_queue")),
                    deserialize(resultSet.getString("safety_flags")),
                    resultSet.getObject("generated_at", java.time.OffsetDateTime.class).toInstant(),
                    resultSet.getObject("received_at", java.time.OffsetDateTime.class).toInstant()))
        .optional();
  }

  private String serialize(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialize claim summary values", exception);
    }
  }

  private List<String> deserialize(String value) {
    try {
      return objectMapper.readValue(value, STRING_LIST);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to deserialize claim summary values", exception);
    }
  }
}
