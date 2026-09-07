package com.rohantummala.insurance.claims.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.application.service.ClaimSubmissionService;
import com.rohantummala.insurance.claims.application.service.OutboxPublicationResult;
import com.rohantummala.insurance.claims.application.service.OutboxPublicationService;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "CLAIMS_DB_PASSWORD=test-only-placeholder",
      "integration.outbox.publisher-enabled=false"
    })
@Testcontainers
class RabbitOutboxPublicationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Container @ServiceConnection
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer("rabbitmq:4.3.5-management-alpine");

  @Autowired private ClaimSubmissionService claimSubmissionService;

  @Autowired private OutboxPublicationService outboxPublicationService;

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private JdbcClient jdbcClient;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PolicyValidationPort policyValidationPort;

  @BeforeEach
  void resetState() {
    jdbcClient.sql("DELETE FROM outbox_events").update();
    jdbcClient.sql("DELETE FROM claim_status_history").update();
    jdbcClient.sql("DELETE FROM claims").update();
    rabbitTemplate.execute(
        channel -> {
          channel.queuePurge(RabbitTopology.CLAIM_SUMMARY_REQUESTS_QUEUE);
          return null;
        });
    when(policyValidationPort.validate(any()))
        .thenReturn(new PolicyValidationResult(true, "VALID", "Policy covers this claim"));
  }

  @Test
  void publishesTheStoredEnvelopeAndMarksItPublishedAfterBrokerConfirmation() {
    Claim claim = claimSubmissionService.submit(command());

    OutboxPublicationResult result = outboxPublicationService.publishNextBatch();

    assertThat(result).isEqualTo(new OutboxPublicationResult(1, 1, 0));
    Message message = rabbitTemplate.receive(RabbitTopology.CLAIM_SUMMARY_REQUESTS_QUEUE, 5_000);
    assertThat(message).isNotNull();

    PublishedRow row =
        jdbcClient
            .sql(
                """
                SELECT event_id::text, status, attempt_count, published_at, payload::text
                FROM outbox_events
                """)
            .query(PublishedRow.class)
            .single();
    JsonNode body = objectMapper.readTree(message.getBody());

    assertThat(row.status()).isEqualTo("PUBLISHED");
    assertThat(row.attemptCount()).isEqualTo(1);
    assertThat(row.publishedAt()).isNotNull();
    assertThat(body.get("eventId").asText()).isEqualTo(row.eventId());
    assertThat(body.get("data").get("claimId").asText()).isEqualTo(claim.id().toString());
    assertThat(body.get("data").has("claimantName")).isFalse();
    assertThat(body.get("data").has("policyNumber")).isFalse();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(row.payload());
    assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.eventId());
    assertThat(message.getMessageProperties().getCorrelationId()).isNotBlank();
    assertThat(message.getMessageProperties().getType()).isEqualTo("claim.submitted");
    assertThat((Object) message.getMessageProperties().getHeader("eventVersion")).isEqualTo(1);
    assertThat((Object) message.getMessageProperties().getHeader("aggregateType"))
        .isEqualTo("claim");
    assertThat((Object) message.getMessageProperties().getHeader("aggregateId"))
        .isEqualTo(claim.id().toString());
  }

  private SubmitClaimCommand command() {
    return new SubmitClaimCommand(
        "EXT-RABBIT-1001",
        "POL-AUTO-1001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description for RabbitMQ publication verification",
        new BigDecimal("1250.00"));
  }

  record PublishedRow(
      String eventId,
      String status,
      int attemptCount,
      OffsetDateTime publishedAt,
      String payload) {}
}
