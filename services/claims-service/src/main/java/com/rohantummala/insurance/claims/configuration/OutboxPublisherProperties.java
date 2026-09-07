package com.rohantummala.insurance.claims.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("integration.outbox")
public record OutboxPublisherProperties(
    boolean publisherEnabled,
    @Min(1) @Max(100) int batchSize,
    @NotNull Duration pollInterval,
    @NotNull Duration leaseDuration,
    @NotNull Duration retryDelay,
    @NotNull Duration confirmTimeout) {}
