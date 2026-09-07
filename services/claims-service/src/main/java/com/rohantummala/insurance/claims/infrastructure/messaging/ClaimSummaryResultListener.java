package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rabbitmq.client.Channel;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.service.ClaimSummaryService;
import com.rohantummala.insurance.claims.application.service.SummaryProcessingResult;
import com.rohantummala.insurance.claims.configuration.SummaryConsumerProperties;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!in-memory")
@ConditionalOnProperty(
    prefix = "integration.summary-consumer",
    name = "enabled",
    havingValue = "true")
public class ClaimSummaryResultListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClaimSummaryResultListener.class);
  private static final String CORRELATION_ID_MDC_KEY = "correlationId";

  private final ClaimSummaryMessageDecoder decoder;
  private final ClaimSummaryService summaryService;
  private final SummaryFailureRouter failureRouter;
  private final int maxAttempts;

  public ClaimSummaryResultListener(
      ClaimSummaryMessageDecoder decoder,
      ClaimSummaryService summaryService,
      SummaryFailureRouter failureRouter,
      SummaryConsumerProperties properties) {
    this.decoder = decoder;
    this.summaryService = summaryService;
    this.failureRouter = failureRouter;
    this.maxAttempts = properties.maxAttempts();
  }

  @RabbitListener(queues = RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE, ackMode = "MANUAL")
  public void consume(Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
      ClaimSummaryCompletedEnvelope event = decoder.decode(message.getBody());
      try (MDC.MDCCloseable ignored =
          MDC.putCloseable(CORRELATION_ID_MDC_KEY, event.correlationId())) {
        SummaryProcessingResult result = summaryService.record(event);
        channel.basicAck(deliveryTag, false);
        LOGGER.info(
            "Claim summary processed status={} eventId={} claimId={}",
            result.status(),
            event.eventId(),
            event.aggregateId());
      }
    } catch (InvalidClaimSummaryEventException | ClaimNotFoundException exception) {
      routeToDeadLetterAndAcknowledge(message, channel, deliveryTag, exception);
    } catch (RuntimeException exception) {
      routeTransientFailure(message, channel, deliveryTag, exception);
    }
  }

  private void routeTransientFailure(
      Message message, Channel channel, long deliveryTag, RuntimeException processingFailure)
      throws IOException {
    int attempt = retryCount(message) + 1;
    try {
      if (attempt >= maxAttempts) {
        failureRouter.routeToDeadLetter(message, processingFailure.getClass().getSimpleName());
        channel.basicAck(deliveryTag, false);
        LOGGER.warn(
            "Claim summary moved to dead-letter queue after attempt={} messageId={} failureType={}",
            attempt,
            message.getMessageProperties().getMessageId(),
            processingFailure.getClass().getSimpleName());
        return;
      }

      failureRouter.routeForRetry(message, attempt, processingFailure.getClass().getSimpleName());
      channel.basicAck(deliveryTag, false);
      LOGGER.warn(
          "Claim summary scheduled for retry attempt={} messageId={} failureType={}",
          attempt + 1,
          message.getMessageProperties().getMessageId(),
          processingFailure.getClass().getSimpleName());
    } catch (MessageRoutingException routingFailure) {
      requeueOriginal(message, channel, deliveryTag, routingFailure);
    }
  }

  private void routeToDeadLetterAndAcknowledge(
      Message message, Channel channel, long deliveryTag, RuntimeException processingFailure)
      throws IOException {
    try {
      failureRouter.routeToDeadLetter(message, processingFailure.getClass().getSimpleName());
      channel.basicAck(deliveryTag, false);
      LOGGER.warn(
          "Claim summary moved to dead-letter queue messageId={} failureType={}",
          message.getMessageProperties().getMessageId(),
          processingFailure.getClass().getSimpleName());
    } catch (MessageRoutingException routingFailure) {
      requeueOriginal(message, channel, deliveryTag, routingFailure);
    }
  }

  private void requeueOriginal(
      Message message, Channel channel, long deliveryTag, MessageRoutingException routingFailure)
      throws IOException {
    channel.basicNack(deliveryTag, false, true);
    LOGGER.error(
        "Failed to route claim summary; original requeued messageId={} failureType={}",
        message.getMessageProperties().getMessageId(),
        routingFailure.getClass().getSimpleName());
  }

  private int retryCount(Message message) {
    Object value = message.getMessageProperties().getHeader(RabbitTopology.RETRY_COUNT_HEADER);
    if (value instanceof Number number) {
      return Math.max(0, number.intValue());
    }
    if (value instanceof String text) {
      try {
        return Math.max(0, Integer.parseInt(text));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }
}
