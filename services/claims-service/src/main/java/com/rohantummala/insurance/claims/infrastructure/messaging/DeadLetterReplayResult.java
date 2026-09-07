package com.rohantummala.insurance.claims.infrastructure.messaging;

public record DeadLetterReplayResult(int requested, int replayed) {}
