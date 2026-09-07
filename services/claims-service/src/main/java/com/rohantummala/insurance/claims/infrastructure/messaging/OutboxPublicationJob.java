package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rohantummala.insurance.claims.application.service.OutboxPublicationResult;
import com.rohantummala.insurance.claims.application.service.OutboxPublicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!in-memory")
@ConditionalOnProperty(
    prefix = "integration.outbox",
    name = "publisher-enabled",
    havingValue = "true")
public class OutboxPublicationJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublicationJob.class);

  private final OutboxPublicationService publicationService;

  public OutboxPublicationJob(OutboxPublicationService publicationService) {
    this.publicationService = publicationService;
  }

  @Scheduled(fixedDelayString = "${integration.outbox.poll-interval}")
  public void publishReadyEvents() {
    OutboxPublicationResult result = publicationService.publishNextBatch();
    if (result.claimed() > 0) {
      LOGGER.info(
          "Outbox publication batch completed claimed={} published={} failed={}",
          result.claimed(),
          result.published(),
          result.failed());
    }
  }
}
