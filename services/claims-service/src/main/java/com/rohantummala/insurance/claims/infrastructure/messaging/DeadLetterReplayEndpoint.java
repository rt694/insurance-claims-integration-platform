package com.rohantummala.insurance.claims.infrastructure.messaging;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!in-memory")
@Endpoint(id = "deadLetterReplay")
@ConditionalOnProperty(
    prefix = "integration.dead-letter-replay",
    name = "enabled",
    havingValue = "true")
public class DeadLetterReplayEndpoint {

  private final RabbitDeadLetterReplayService replayService;

  public DeadLetterReplayEndpoint(RabbitDeadLetterReplayService replayService) {
    this.replayService = replayService;
  }

  @WriteOperation
  public DeadLetterReplayResult replay(int limit) {
    return replayService.replay(limit);
  }
}
