package com.rohantummala.insurance.claims.application.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    String correlationId,
    Instant occurredAt,
    T data) {}
