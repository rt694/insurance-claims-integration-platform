package com.rohantummala.insurance.claims.application.event;

import java.util.UUID;

public record OutboxEventPublication(
    UUID eventId,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    int eventVersion,
    String correlationId,
    String payload,
    int attemptNumber) {}
