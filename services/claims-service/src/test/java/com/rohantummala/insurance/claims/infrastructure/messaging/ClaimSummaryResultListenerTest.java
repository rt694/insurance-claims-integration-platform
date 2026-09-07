package com.rohantummala.insurance.claims.infrastructure.messaging;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import com.rohantummala.insurance.claims.application.service.ClaimSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ClaimSummaryResultListenerTest {

  private final ClaimSummaryMessageDecoder decoder = mock(ClaimSummaryMessageDecoder.class);
  private final ClaimSummaryService summaryService = mock(ClaimSummaryService.class);
  private final Channel channel = mock(Channel.class);
  private final ClaimSummaryResultListener listener =
      new ClaimSummaryResultListener(decoder, summaryService);

  @Test
  void acknowledgesOnlyAfterTheSummaryServiceSucceeds() throws Exception {
    Message message = message(11L);
    ClaimSummaryCompletedEnvelope event = mock(ClaimSummaryCompletedEnvelope.class);
    when(event.correlationId()).thenReturn("listener-correlation");
    when(decoder.decode(message.getBody())).thenReturn(event);

    listener.consume(message, channel);

    var inOrder = org.mockito.Mockito.inOrder(summaryService, channel);
    inOrder.verify(summaryService).record(event);
    inOrder.verify(channel).basicAck(11L, false);
  }

  @Test
  void rejectsAnInvalidEventWithoutRequeueingIt() throws Exception {
    Message message = message(12L);
    when(decoder.decode(message.getBody()))
        .thenThrow(new InvalidClaimSummaryEventException("invalid"));

    listener.consume(message, channel);

    verify(channel).basicReject(12L, false);
  }

  @Test
  void requeuesATransientProcessingFailure() throws Exception {
    Message message = message(13L);
    ClaimSummaryCompletedEnvelope event = mock(ClaimSummaryCompletedEnvelope.class);
    when(event.correlationId()).thenReturn("listener-correlation");
    when(decoder.decode(message.getBody())).thenReturn(event);
    doThrow(new IllegalStateException("database unavailable")).when(summaryService).record(event);

    listener.consume(message, channel);

    verify(channel).basicNack(13L, false, true);
  }

  private Message message(long deliveryTag) {
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(deliveryTag);
    properties.setMessageId("message-" + deliveryTag);
    return new Message("{}".getBytes(), properties);
  }
}
