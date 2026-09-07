package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rohantummala.insurance.claims.configuration.SummaryConsumerProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!in-memory")
public class RabbitSummaryFailureRouter implements SummaryFailureRouter {

  private final RabbitTemplate rabbitTemplate;
  private final Clock clock;
  private final Duration confirmTimeout;

  public RabbitSummaryFailureRouter(
      RabbitTemplate rabbitTemplate, Clock clock, SummaryConsumerProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.clock = clock;
    this.confirmTimeout = properties.confirmTimeout();
  }

  @Override
  public void routeForRetry(Message original, int retryCount, String failureType) {
    Message retry =
        copy(original)
            .setHeader(RabbitTopology.RETRY_COUNT_HEADER, retryCount)
            .setHeader(RabbitTopology.FAILURE_TYPE_HEADER, failureType)
            .build();
    publishConfirmed(RabbitTopology.CLAIMS_RETRY_EXCHANGE, retry);
  }

  @Override
  public void routeToDeadLetter(Message original, String failureType) {
    Message deadLetter =
        copy(original)
            .setHeader(RabbitTopology.FAILURE_TYPE_HEADER, failureType)
            .setHeader(RabbitTopology.DEAD_LETTERED_AT_HEADER, clock.instant().toString())
            .build();
    publishConfirmed(RabbitTopology.CLAIMS_DEAD_LETTER_EXCHANGE, deadLetter);
  }

  @Override
  public void replay(Message original) {
    Message replay =
        copy(original)
            .removeHeader(RabbitTopology.RETRY_COUNT_HEADER)
            .removeHeader(RabbitTopology.FAILURE_TYPE_HEADER)
            .removeHeader(RabbitTopology.DEAD_LETTERED_AT_HEADER)
            .setHeader(RabbitTopology.REPLAYED_AT_HEADER, clock.instant().toString())
            .build();
    publishConfirmed(RabbitTopology.CLAIMS_EVENTS_EXCHANGE, replay);
  }

  private MessageBuilderSupport<Message> copy(Message original) {
    return MessageBuilder.fromClonedMessage(original)
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
  }

  private void publishConfirmed(String exchange, Message message) {
    CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
    try {
      rabbitTemplate.send(
          exchange, RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY, message, correlationData);
      CorrelationData.Confirm confirm =
          correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.isAck()) {
        throw new MessageRoutingException("RabbitMQ rejected the routed message");
      }
      if (correlationData.getReturned() != null) {
        throw new MessageRoutingException("RabbitMQ could not route the message");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new MessageRoutingException(
          "Interrupted while awaiting RabbitMQ confirmation", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new MessageRoutingException("RabbitMQ did not confirm the routed message", exception);
    } catch (MessageRoutingException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new MessageRoutingException("RabbitMQ could not publish the routed message", exception);
    }
  }
}
