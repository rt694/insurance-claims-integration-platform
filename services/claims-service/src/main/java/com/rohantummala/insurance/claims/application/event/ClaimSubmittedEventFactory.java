package com.rohantummala.insurance.claims.application.event;

import com.rohantummala.insurance.claims.application.port.CorrelationIdProvider;
import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClaimSubmittedEventFactory {

  public static final String EVENT_TYPE = "claim.submitted";
  public static final int EVENT_VERSION = 1;
  public static final String AGGREGATE_TYPE = "claim";

  private final CorrelationIdProvider correlationIdProvider;

  public ClaimSubmittedEventFactory(CorrelationIdProvider correlationIdProvider) {
    this.correlationIdProvider = correlationIdProvider;
  }

  public EventEnvelope<ClaimSubmittedPayload> create(Claim claim) {
    ClaimSubmittedPayload payload =
        new ClaimSubmittedPayload(
            claim.id(),
            claim.claimType(),
            claim.incidentDate(),
            claim.description(),
            claim.estimatedLoss(),
            claim.status(),
            claim.createdAt());
    return new EventEnvelope<>(
        UUID.randomUUID(),
        EVENT_TYPE,
        EVENT_VERSION,
        AGGREGATE_TYPE,
        claim.id(),
        correlationIdProvider.currentCorrelationId(),
        claim.createdAt(),
        payload);
  }
}
