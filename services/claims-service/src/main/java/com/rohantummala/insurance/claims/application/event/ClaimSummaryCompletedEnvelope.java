package com.rohantummala.insurance.claims.application.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public record ClaimSummaryCompletedEnvelope(
    @NotNull UUID eventId,
    @NotBlank String eventType,
    @Positive int eventVersion,
    @NotBlank String aggregateType,
    @NotNull UUID aggregateId,
    @NotBlank String correlationId,
    @NotNull Instant occurredAt,
    @NotNull @Valid ClaimSummaryCompletedPayload data) {

  public static final String EVENT_TYPE = "claim.summary.completed";
  public static final int EVENT_VERSION = 1;
  public static final String AGGREGATE_TYPE = "claim";
}
