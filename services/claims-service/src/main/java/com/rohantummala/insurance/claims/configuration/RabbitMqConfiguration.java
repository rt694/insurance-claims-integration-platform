package com.rohantummala.insurance.claims.configuration;

import com.rohantummala.insurance.claims.application.port.EventPublisher;
import com.rohantummala.insurance.claims.application.port.OutboxPublicationRepository;
import com.rohantummala.insurance.claims.application.service.OutboxPublicationService;
import com.rohantummala.insurance.claims.infrastructure.messaging.RabbitTopology;
import java.time.Clock;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!in-memory")
@EnableScheduling
@EnableConfigurationProperties(OutboxPublisherProperties.class)
public class RabbitMqConfiguration {

  @Bean
  TopicExchange claimsEventsExchange() {
    return new TopicExchange(RabbitTopology.CLAIMS_EVENTS_EXCHANGE, true, false);
  }

  @Bean
  Queue claimSummaryRequestsQueue() {
    return QueueBuilder.durable(RabbitTopology.CLAIM_SUMMARY_REQUESTS_QUEUE).build();
  }

  @Bean
  Binding claimSubmittedBinding(
      TopicExchange claimsEventsExchange, Queue claimSummaryRequestsQueue) {
    return BindingBuilder.bind(claimSummaryRequestsQueue)
        .to(claimsEventsExchange)
        .with(RabbitTopology.CLAIM_SUBMITTED_ROUTING_KEY);
  }

  @Bean
  OutboxPublicationService outboxPublicationService(
      OutboxPublicationRepository repository,
      EventPublisher eventPublisher,
      Clock clock,
      OutboxPublisherProperties properties) {
    return new OutboxPublicationService(
        repository,
        eventPublisher,
        clock,
        properties.batchSize(),
        properties.leaseDuration(),
        properties.retryDelay());
  }
}
