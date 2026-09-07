package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rabbitmq.client.GetResponse;
import com.rohantummala.insurance.claims.configuration.DeadLetterReplayProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!in-memory")
@ConditionalOnProperty(
    prefix = "integration.dead-letter-replay",
    name = "enabled",
    havingValue = "true")
public class RabbitDeadLetterReplayService {

  private final RabbitTemplate rabbitTemplate;
  private final SummaryFailureRouter failureRouter;
  private final int maxBatchSize;
  private final DefaultMessagePropertiesConverter propertiesConverter =
      new DefaultMessagePropertiesConverter();

  public RabbitDeadLetterReplayService(
      RabbitTemplate rabbitTemplate,
      SummaryFailureRouter failureRouter,
      DeadLetterReplayProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.failureRouter = failureRouter;
    this.maxBatchSize = properties.maxBatchSize();
  }

  public DeadLetterReplayResult replay(int requested) {
    if (requested < 1 || requested > maxBatchSize) {
      throw new IllegalArgumentException("Replay batch size must be between 1 and " + maxBatchSize);
    }

    Integer replayed =
        rabbitTemplate.execute(
            channel -> {
              int count = 0;
              while (count < requested) {
                GetResponse response =
                    channel.basicGet(RabbitTopology.CLAIM_SUMMARY_RESULTS_DEAD_LETTER_QUEUE, false);
                if (response == null) {
                  break;
                }

                long deliveryTag = response.getEnvelope().getDeliveryTag();
                Message message =
                    new Message(
                        response.getBody(),
                        propertiesConverter.toMessageProperties(
                            response.getProps(), response.getEnvelope(), "UTF-8"));
                try {
                  failureRouter.replay(message);
                  channel.basicAck(deliveryTag, false);
                  count++;
                } catch (MessageRoutingException exception) {
                  channel.basicNack(deliveryTag, false, true);
                  throw exception;
                }
              }
              return count;
            });

    return new DeadLetterReplayResult(requested, replayed == null ? 0 : replayed);
  }
}
