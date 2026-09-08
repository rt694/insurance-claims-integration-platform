package com.rohantummala.insurance.claims.configuration;

import com.rohantummala.insurance.claims.application.port.EventPublisher;
import com.rohantummala.insurance.claims.application.port.OutboxPublicationRepository;
import com.rohantummala.insurance.claims.application.service.OutboxPublicationService;
import com.rohantummala.insurance.claims.infrastructure.messaging.RabbitTopology;
import com.rohantummala.insurance.claims.observability.ClaimsMetrics;
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
@EnableConfigurationProperties({
  OutboxPublisherProperties.class,
  SummaryConsumerProperties.class,
  DeadLetterReplayProperties.class
})
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
  Queue claimSummaryResultsQueue() {
    return QueueBuilder.durable(RabbitTopology.CLAIM_SUMMARY_RESULTS_QUEUE).build();
  }

  @Bean
  Queue claimSummaryRequestsRetryQueue(SummaryConsumerProperties properties) {
    return QueueBuilder.durable(RabbitTopology.CLAIM_SUMMARY_REQUESTS_RETRY_QUEUE)
        .quorum()
        .withArgument("x-dead-letter-strategy", "at-least-once")
        .withArgument("x-overflow", "reject-publish")
        .ttl(Math.toIntExact(properties.retryDelay().toMillis()))
        .deadLetterExchange(RabbitTopology.CLAIMS_EVENTS_EXCHANGE)
        .deadLetterRoutingKey(RabbitTopology.CLAIM_SUBMITTED_ROUTING_KEY)
        .build();
  }

  @Bean
  TopicExchange claimsRetryExchange() {
    return new TopicExchange(RabbitTopology.CLAIMS_RETRY_EXCHANGE, true, false);
  }

  @Bean
  Queue claimSummaryResultsRetryQueue(SummaryConsumerProperties properties) {
    return QueueBuilder.durable(RabbitTopology.CLAIM_SUMMARY_RESULTS_RETRY_QUEUE)
        .quorum()
        .withArgument("x-dead-letter-strategy", "at-least-once")
        .withArgument("x-overflow", "reject-publish")
        .ttl(Math.toIntExact(properties.retryDelay().toMillis()))
        .deadLetterExchange(RabbitTopology.CLAIMS_EVENTS_EXCHANGE)
        .deadLetterRoutingKey(RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY)
        .build();
  }

  @Bean
  Binding claimSummaryResultsRetryBinding(
      TopicExchange claimsRetryExchange, Queue claimSummaryResultsRetryQueue) {
    return BindingBuilder.bind(claimSummaryResultsRetryQueue)
        .to(claimsRetryExchange)
        .with(RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY);
  }

  @Bean
  Binding claimSummaryRequestsRetryBinding(
      TopicExchange claimsRetryExchange, Queue claimSummaryRequestsRetryQueue) {
    return BindingBuilder.bind(claimSummaryRequestsRetryQueue)
        .to(claimsRetryExchange)
        .with(RabbitTopology.CLAIM_SUBMITTED_ROUTING_KEY);
  }

  @Bean
  TopicExchange claimsDeadLetterExchange() {
    return new TopicExchange(RabbitTopology.CLAIMS_DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  Queue claimSummaryResultsDeadLetterQueue() {
    return QueueBuilder.durable(RabbitTopology.CLAIM_SUMMARY_RESULTS_DEAD_LETTER_QUEUE).build();
  }

  @Bean
  Queue claimSummaryRequestsDeadLetterQueue() {
    return QueueBuilder.durable(RabbitTopology.CLAIM_SUMMARY_REQUESTS_DEAD_LETTER_QUEUE).build();
  }

  @Bean
  Binding claimSummaryResultsDeadLetterBinding(
      TopicExchange claimsDeadLetterExchange, Queue claimSummaryResultsDeadLetterQueue) {
    return BindingBuilder.bind(claimSummaryResultsDeadLetterQueue)
        .to(claimsDeadLetterExchange)
        .with(RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY);
  }

  @Bean
  Binding claimSummaryRequestsDeadLetterBinding(
      TopicExchange claimsDeadLetterExchange, Queue claimSummaryRequestsDeadLetterQueue) {
    return BindingBuilder.bind(claimSummaryRequestsDeadLetterQueue)
        .to(claimsDeadLetterExchange)
        .with(RabbitTopology.CLAIM_SUBMITTED_ROUTING_KEY);
  }

  @Bean
  Binding claimSubmittedBinding(
      TopicExchange claimsEventsExchange, Queue claimSummaryRequestsQueue) {
    return BindingBuilder.bind(claimSummaryRequestsQueue)
        .to(claimsEventsExchange)
        .with(RabbitTopology.CLAIM_SUBMITTED_ROUTING_KEY);
  }

  @Bean
  Binding claimSummaryCompletedBinding(
      TopicExchange claimsEventsExchange, Queue claimSummaryResultsQueue) {
    return BindingBuilder.bind(claimSummaryResultsQueue)
        .to(claimsEventsExchange)
        .with(RabbitTopology.CLAIM_SUMMARY_COMPLETED_ROUTING_KEY);
  }

  @Bean
  OutboxPublicationService outboxPublicationService(
      OutboxPublicationRepository repository,
      EventPublisher eventPublisher,
      Clock clock,
      OutboxPublisherProperties properties,
      ClaimsMetrics metrics) {
    return new OutboxPublicationService(
        repository,
        eventPublisher,
        clock,
        properties.batchSize(),
        properties.leaseDuration(),
        properties.retryDelay(),
        metrics);
  }
}
