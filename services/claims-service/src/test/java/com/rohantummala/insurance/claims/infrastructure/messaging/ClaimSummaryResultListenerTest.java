package com.rohantummala.insurance.claims.infrastructure.messaging;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.service.ClaimSummaryService;
import com.rohantummala.insurance.claims.application.service.SummaryProcessingResult;
import com.rohantummala.insurance.claims.configuration.SummaryConsumerProperties;
import com.rohantummala.insurance.claims.observability.ClaimsMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ClaimSummaryResultListenerTest {

  private final ClaimSummaryMessageDecoder decoder = mock(ClaimSummaryMessageDecoder.class);
  private final ClaimSummaryService summaryService = mock(ClaimSummaryService.class);
  private final SummaryFailureRouter failureRouter = mock(SummaryFailureRouter.class);
  private final Channel channel = mock(Channel.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ClaimSummaryResultListener listener =
      new ClaimSummaryResultListener(
          decoder,
          summaryService,
          failureRouter,
          new SummaryConsumerProperties(true, 3, Duration.ofSeconds(5), Duration.ofSeconds(5)),
          new ClaimsMetrics(meterRegistry));

  @Test
  void acknowledgesOnlyAfterTheSummaryServiceSucceeds() throws Exception {
    Message message = message(11L);
    ClaimSummaryCompletedEnvelope event = mock(ClaimSummaryCompletedEnvelope.class);
    when(event.correlationId()).thenReturn("listener-correlation");
    when(decoder.decode(message.getBody())).thenReturn(event);
    when(summaryService.record(event)).thenReturn(SummaryProcessingResult.duplicate());

    listener.consume(message, channel);

    var inOrder = org.mockito.Mockito.inOrder(summaryService, channel);
    inOrder.verify(summaryService).record(event);
    inOrder.verify(channel).basicAck(11L, false);
    org.assertj.core.api.Assertions.assertThat(summaryCount("duplicate")).isEqualTo(1);
  }

  @Test
  void deadLettersAnInvalidEventBeforeAcknowledgingIt() throws Exception {
    Message message = message(12L);
    when(decoder.decode(message.getBody()))
        .thenThrow(new InvalidClaimSummaryEventException("invalid"));

    listener.consume(message, channel);

    var inOrder = org.mockito.Mockito.inOrder(failureRouter, channel);
    inOrder
        .verify(failureRouter)
        .routeToDeadLetter(message, InvalidClaimSummaryEventException.class.getSimpleName());
    inOrder.verify(channel).basicAck(12L, false);
    org.assertj.core.api.Assertions.assertThat(summaryCount("dead_lettered")).isEqualTo(1);
  }

  @Test
  void schedulesATransientProcessingFailureBeforeAcknowledgingIt() throws Exception {
    Message message = message(13L);
    ClaimSummaryCompletedEnvelope event = mock(ClaimSummaryCompletedEnvelope.class);
    when(event.correlationId()).thenReturn("listener-correlation");
    when(decoder.decode(message.getBody())).thenReturn(event);
    doThrow(new IllegalStateException("database unavailable")).when(summaryService).record(event);

    listener.consume(message, channel);

    var inOrder = org.mockito.Mockito.inOrder(failureRouter, channel);
    inOrder
        .verify(failureRouter)
        .routeForRetry(message, 1, IllegalStateException.class.getSimpleName());
    inOrder.verify(channel).basicAck(13L, false);
    org.assertj.core.api.Assertions.assertThat(summaryCount("retried")).isEqualTo(1);
  }

  @Test
  void deadLettersATransientFailureAfterTheMaximumAttempt() throws Exception {
    Message message = message(14L);
    message.getMessageProperties().setHeader(RabbitTopology.RETRY_COUNT_HEADER, 2);
    ClaimSummaryCompletedEnvelope event = mock(ClaimSummaryCompletedEnvelope.class);
    when(event.correlationId()).thenReturn("listener-correlation");
    when(decoder.decode(message.getBody())).thenReturn(event);
    doThrow(new IllegalStateException("database unavailable")).when(summaryService).record(event);

    listener.consume(message, channel);

    var inOrder = org.mockito.Mockito.inOrder(failureRouter, channel);
    inOrder
        .verify(failureRouter)
        .routeToDeadLetter(message, IllegalStateException.class.getSimpleName());
    inOrder.verify(channel).basicAck(14L, false);
  }

  @Test
  void requeuesTheOriginalWhenRabbitCannotConfirmFailureRouting() throws Exception {
    Message message = message(15L);
    when(decoder.decode(message.getBody()))
        .thenThrow(new InvalidClaimSummaryEventException("invalid"));
    doThrow(new MessageRoutingException("broker unavailable"))
        .when(failureRouter)
        .routeToDeadLetter(message, InvalidClaimSummaryEventException.class.getSimpleName());

    listener.consume(message, channel);

    verify(channel).basicNack(15L, false, true);
    org.assertj.core.api.Assertions.assertThat(summaryCount("requeued")).isEqualTo(1);
  }

  private Message message(long deliveryTag) {
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(deliveryTag);
    properties.setMessageId("message-" + deliveryTag);
    return new Message("{}".getBytes(), properties);
  }

  private double summaryCount(String outcome) {
    return meterRegistry
        .get("insurance.claims.summary.results")
        .tag("outcome", outcome)
        .counter()
        .count();
  }
}
