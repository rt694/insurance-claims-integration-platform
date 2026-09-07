package com.rohantummala.insurance.claims.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest(
    properties = {
      "CLAIMS_DB_PASSWORD=test-only-placeholder",
      "integration.outbox.publisher-enabled=false",
      "integration.summary-consumer.enabled=false",
      "integration.summary-consumer.retry-delay=500ms",
      "integration.dead-letter-replay.enabled=true"
    })
@Testcontainers
class RabbitSummaryReliabilityIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Container @ServiceConnection
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer("rabbitmq:4.3.5-management-alpine");

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private SummaryFailureRouter failureRouter;

  @Autowired private RabbitDeadLetterReplayService replayService;

  @BeforeEach
  void purgeQueues() {
    rabbitTemplate.execute(
        channel -> {
          channel.queuePurge(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE);
          channel.queuePurge(RabbitTopology.CLAIM_SUMMARY_RESULTS_RETRY_QUEUE);
          channel.queuePurge(RabbitTopology.CLAIM_SUMMARY_RESULTS_DEAD_LETTER_QUEUE);
          return null;
        });
  }

  @Test
  void delaysARetryThenReturnsItToTheResultsQueue() {
    Message original = message();

    failureRouter.routeForRetry(original, 1, "IllegalStateException");

    assertThat(rabbitTemplate.receive(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE, 100)).isNull();
    Message retried = rabbitTemplate.receive(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE, 5_000);
    assertThat(retried).isNotNull();
    assertThat((Object) retried.getMessageProperties().getHeader(RabbitTopology.RETRY_COUNT_HEADER))
        .isEqualTo(1);
    assertThat(
            (Object) retried.getMessageProperties().getHeader(RabbitTopology.FAILURE_TYPE_HEADER))
        .isEqualTo("IllegalStateException");
  }

  @Test
  void replaysADeadLetterOnlyAfterConfirmedRepublishing() {
    Message original = message();
    failureRouter.routeToDeadLetter(original, "InvalidClaimSummaryEventException");
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        queueMessageCount(RabbitTopology.CLAIM_SUMMARY_RESULTS_DEAD_LETTER_QUEUE))
                    .isEqualTo(1));

    DeadLetterReplayResult result = replayService.replay(1);

    assertThat(result).isEqualTo(new DeadLetterReplayResult(1, 1));
    assertThat(queueMessageCount(RabbitTopology.CLAIM_SUMMARY_RESULTS_DEAD_LETTER_QUEUE)).isZero();
    Message replayed = rabbitTemplate.receive(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE, 5_000);
    assertThat(replayed).isNotNull();
    assertThat(
            (Object) replayed.getMessageProperties().getHeader(RabbitTopology.FAILURE_TYPE_HEADER))
        .isNull();
    assertThat(
            (Object)
                replayed.getMessageProperties().getHeader(RabbitTopology.DEAD_LETTERED_AT_HEADER))
        .isNull();
    assertThat(
            (Object) replayed.getMessageProperties().getHeader(RabbitTopology.REPLAYED_AT_HEADER))
        .isNotNull();
  }

  private Message message() {
    return MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
        .setContentType("application/json")
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
        .setMessageId(UUID.randomUUID().toString())
        .build();
  }

  private long queueMessageCount(String queue) {
    return rabbitTemplate.execute(
        channel -> (long) channel.queueDeclarePassive(queue).getMessageCount());
  }
}
