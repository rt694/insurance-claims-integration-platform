package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rabbitmq.client.Channel;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.service.ClaimSummaryService;
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

  public ClaimSummaryResultListener(
      ClaimSummaryMessageDecoder decoder, ClaimSummaryService summaryService) {
    this.decoder = decoder;
    this.summaryService = summaryService;
  }

  @RabbitListener(queues = RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE, ackMode = "MANUAL")
  public void consume(Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
      ClaimSummaryCompletedEnvelope event = decoder.decode(message.getBody());
      try (MDC.MDCCloseable ignored =
          MDC.putCloseable(CORRELATION_ID_MDC_KEY, event.correlationId())) {
        summaryService.record(event);
        channel.basicAck(deliveryTag, false);
        LOGGER.info(
            "Claim summary stored eventId={} claimId={}", event.eventId(), event.aggregateId());
      }
    } catch (InvalidClaimSummaryEventException | ClaimNotFoundException exception) {
      channel.basicReject(deliveryTag, false);
      LOGGER.warn(
          "Claim summary event rejected messageId={} failureType={}",
          message.getMessageProperties().getMessageId(),
          exception.getClass().getSimpleName());
    } catch (RuntimeException exception) {
      channel.basicNack(deliveryTag, false, true);
      LOGGER.warn(
          "Claim summary processing will be retried messageId={} failureType={}",
          message.getMessageProperties().getMessageId(),
          exception.getClass().getSimpleName());
    }
  }
}
