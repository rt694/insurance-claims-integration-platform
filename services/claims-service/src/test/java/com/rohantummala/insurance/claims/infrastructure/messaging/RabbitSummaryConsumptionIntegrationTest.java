package com.rohantummala.insurance.claims.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedPayload;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.application.service.ClaimSubmissionService;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "CLAIMS_DB_PASSWORD=test-only-placeholder",
      "integration.outbox.publisher-enabled=false"
    })
@Testcontainers
class RabbitSummaryConsumptionIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Container @ServiceConnection
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer("rabbitmq:4.3.5-management-alpine");

  @Autowired private ClaimSubmissionService claimSubmissionService;

  @Autowired private ClaimSummaryRepository claimSummaryRepository;

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private JdbcClient jdbcClient;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PolicyValidationPort policyValidationPort;

  @BeforeEach
  void resetState() {
    jdbcClient.sql("DELETE FROM claim_summaries").update();
    jdbcClient.sql("DELETE FROM outbox_events").update();
    jdbcClient.sql("DELETE FROM claim_status_history").update();
    jdbcClient.sql("DELETE FROM claims").update();
    rabbitTemplate.execute(
        channel -> {
          channel.queuePurge(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE);
          return null;
        });
    when(policyValidationPort.validate(any()))
        .thenReturn(new PolicyValidationResult(true, "VALID", "Policy covers this claim"));
  }

  @Test
  void consumesACompletedSummaryStoresItAndAcknowledgesTheMessage() throws Exception {
    Claim claim = claimSubmissionService.submit(command());
    ClaimSummaryCompletedEnvelope event = event(claim.id());
    Message message =
        MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
            .setContentType("application/json")
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(event.eventId().toString())
            .setCorrelationId(event.correlationId())
            .build();

    rabbitTemplate.send(
        RabbitTopology.CLAIMS_EVENTS_EXCHANGE,
        RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY,
        message);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ClaimSummary stored = claimSummaryRepository.findByClaimId(claim.id()).orElseThrow();
              assertThat(stored.sourceEventId()).isEqualTo(event.eventId());
              assertThat(stored.summary()).isEqualTo(event.data().summary());
              assertThat(stored.missingInformation()).containsExactly("Police report");
              assertThat(stored.recommendedHumanReviewQueue())
                  .isEqualTo(HumanReviewQueue.STANDARD_REVIEW);
              assertThat(stored.safetyFlags()).containsExactly("DESCRIPTION_REQUIRES_REVIEW");
            });

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(resultQueueMessageCount()).isZero());
    assertThat(
            jdbcClient
                .sql("SELECT status FROM claims WHERE id = :claimId")
                .param("claimId", claim.id())
                .query(String.class)
                .single())
        .isEqualTo("SUBMITTED");
  }

  @Test
  void rejectsASchemaInvalidResultWithoutWritingASummary() throws Exception {
    Claim claim = claimSubmissionService.submit(command());
    Message invalidMessage =
        MessageBuilder.withBody(
                """
                {"eventType":"claim.summary.completed","eventVersion":1}
                """
                    .getBytes(StandardCharsets.UTF_8))
            .setContentType("application/json")
            .setMessageId(UUID.randomUUID().toString())
            .build();
    CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

    rabbitTemplate.send(
        RabbitTopology.CLAIMS_EVENTS_EXCHANGE,
        RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY,
        invalidMessage,
        correlationData);

    CorrelationData.Confirm confirm =
        correlationData.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(confirm.isAck()).isTrue();
    assertThat(correlationData.getReturned()).isNull();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(resultQueueMessageCount()).isZero());
    assertThat(claimSummaryRepository.findByClaimId(claim.id())).isEmpty();
  }

  private long resultQueueMessageCount() {
    return rabbitTemplate.execute(
        channel ->
            (long)
                channel
                    .queueDeclarePassive(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE)
                    .getMessageCount());
  }

  private ClaimSummaryCompletedEnvelope event(UUID claimId) {
    Instant generatedAt = Instant.parse("2026-03-01T11:59:55Z");
    return new ClaimSummaryCompletedEnvelope(
        UUID.randomUUID(),
        ClaimSummaryCompletedEnvelope.EVENT_TYPE,
        ClaimSummaryCompletedEnvelope.EVENT_VERSION,
        ClaimSummaryCompletedEnvelope.AGGREGATE_TYPE,
        claimId,
        "rabbit-summary-correlation",
        generatedAt,
        new ClaimSummaryCompletedPayload(
            claimId,
            "Vehicle damage requires human review.",
            List.of("Police report"),
            HumanReviewQueue.STANDARD_REVIEW,
            List.of("DESCRIPTION_REQUIRES_REVIEW"),
            generatedAt));
  }

  private SubmitClaimCommand command() {
    return new SubmitClaimCommand(
        "EXT-RABBIT-SUMMARY-1001",
        "POL-AUTO-1001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description for RabbitMQ consumption verification",
        new BigDecimal("1250.00"));
  }
}
