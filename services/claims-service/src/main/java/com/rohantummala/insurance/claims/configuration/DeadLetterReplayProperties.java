package com.rohantummala.insurance.claims.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("integration.dead-letter-replay")
public record DeadLetterReplayProperties(boolean enabled, @Min(1) @Max(100) int maxBatchSize) {}
