package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;
import com.rohantummala.insurance.claims.application.port.EventPublisher;
import com.rohantummala.insurance.claims.configuration.OutboxPublisherProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!in-memory")
public class RabbitEventPublisherAdapter implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final Duration confirmTimeout;

  public RabbitEventPublisherAdapter(
      RabbitTemplate rabbitTemplate, OutboxPublisherProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.confirmTimeout = properties.confirmTimeout();
  }

  @Override
  public void publish(OutboxEventPublication event) {
    requireSupportedEvent(event);
    CorrelationData correlationData = new CorrelationData(event.eventId().toString());
    rabbitTemplate.send(
        RabbitTopology.CLAIMS_EVENTS_EXCHANGE,
        RabbitTopology.CLAIM_SUBMITTED_ROUTING_KEY,
        message(event),
        correlationData);

    try {
      CorrelationData.Confirm confirm =
          correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.isAck()) {
        throw new EventPublicationException("RabbitMQ rejected the event publication");
      }
      if (correlationData.getReturned() != null) {
        throw new EventPublicationException("RabbitMQ could not route the event publication");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new EventPublicationException(
          "Interrupted while awaiting RabbitMQ confirmation", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new EventPublicationException(
          "RabbitMQ did not confirm the event publication", exception);
    }
  }

  private Message message(OutboxEventPublication event) {
    return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
        .setContentType("application/json")
        .setContentEncoding(StandardCharsets.UTF_8.name())
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
        .setMessageId(event.eventId().toString())
        .setCorrelationId(event.correlationId())
        .setType(event.eventType())
        .setHeader("eventVersion", event.eventVersion())
        .setHeader("aggregateType", event.aggregateType())
        .setHeader("aggregateId", event.aggregateId().toString())
        .build();
  }

  private void requireSupportedEvent(OutboxEventPublication event) {
    if (!"claim.submitted".equals(event.eventType()) || event.eventVersion() != 1) {
      throw new EventPublicationException(
          "No RabbitMQ route is configured for event type %s version %d"
              .formatted(event.eventType(), event.eventVersion()));
    }
  }
}
