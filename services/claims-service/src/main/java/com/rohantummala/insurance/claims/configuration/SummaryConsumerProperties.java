package com.rohantummala.insurance.claims.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("integration.summary-consumer")
public record SummaryConsumerProperties(
    boolean enabled,
    @Min(1) @Max(10) int maxAttempts,
    @NotNull Duration retryDelay,
    @NotNull Duration confirmTimeout) {}
