package com.rohantummala.insurance.claims.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.policy")
public record PolicyServiceProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Retry retry,
    CircuitBreaker circuitBreaker) {

  public record Retry(int maxAttempts, Duration waitDuration) {}

  public record CircuitBreaker(
      float failureRateThreshold,
      int slidingWindowSize,
      int minimumNumberOfCalls,
      Duration waitDurationInOpenState) {}
}
