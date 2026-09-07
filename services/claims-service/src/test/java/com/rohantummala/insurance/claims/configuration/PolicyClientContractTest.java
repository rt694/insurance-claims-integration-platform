package com.rohantummala.insurance.claims.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohantummala.insurance.claims.application.exception.InvalidPolicyServiceResponseException;
import com.rohantummala.insurance.claims.application.exception.PolicyServiceUnavailableException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import com.rohantummala.insurance.claims.infrastructure.policy.HttpPolicyValidationAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PolicyClientContractTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void mapsAValidResponseAndPropagatesTheCorrelationId() throws Exception {
    AtomicReference<String> correlationId = new AtomicReference<>();
    startServer(
        exchange -> {
          correlationId.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
          respond(exchange, 200, "{\"valid\":true,\"code\":\"VALID\",\"message\":\"Covered\"}");
        });

    PolicyValidationResult result = adapter(3, 5).validate(request());

    assertThat(result.valid()).isTrue();
    assertThat(result.code()).isEqualTo("VALID");
    assertThat(correlationId).hasValue("contract-correlation-1001");
  }

  @Test
  void returnsBusinessRejectionsWithoutRetrying() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    startServer(
        exchange -> {
          calls.incrementAndGet();
          respond(
              exchange,
              200,
              "{\"valid\":false,\"code\":\"POLICY_INACTIVE\",\"message\":\"Inactive\"}");
        });

    PolicyValidationResult result = adapter(3, 5).validate(request());

    assertThat(result.valid()).isFalse();
    assertThat(result.code()).isEqualTo("POLICY_INACTIVE");
    assertThat(calls).hasValue(1);
  }

  @Test
  void retriesTransientServerFailuresAndThenSucceeds() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    startServer(
        exchange -> {
          if (calls.incrementAndGet() < 3) {
            respond(exchange, 503, "{\"error\":\"temporary\"}");
          } else {
            respond(exchange, 200, "{\"valid\":true,\"code\":\"VALID\",\"message\":\"Covered\"}");
          }
        });

    assertThat(adapter(3, 5).validate(request()).valid()).isTrue();
    assertThat(calls).hasValue(3);
  }

  @Test
  void treatsAReadTimeoutAsAServiceAvailabilityFailure() throws Exception {
    startServer(
        exchange -> {
          try {
            Thread.sleep(200);
            respond(exchange, 200, "{\"valid\":true,\"code\":\"VALID\",\"message\":\"Covered\"}");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        });

    assertThatThrownBy(() -> adapter(1, 5, Duration.ofMillis(50)).validate(request()))
        .isInstanceOf(PolicyServiceUnavailableException.class);
  }

  @Test
  void rejectsMalformedSuccessfulResponses() throws Exception {
    startServer(exchange -> respond(exchange, 200, "{\"valid\":true}"));

    assertThatThrownBy(() -> adapter(3, 5).validate(request()))
        .isInstanceOf(InvalidPolicyServiceResponseException.class)
        .hasMessage("Policy validation service returned an invalid response body");
  }

  @Test
  void opensTheCircuitAfterTheConfiguredFailureThreshold() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    startServer(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 503, "{\"error\":\"temporary\"}");
        });
    HttpPolicyValidationAdapter adapter = adapter(1, 2);

    assertThatThrownBy(() -> adapter.validate(request()))
        .isInstanceOf(PolicyServiceUnavailableException.class);
    assertThatThrownBy(() -> adapter.validate(request()))
        .isInstanceOf(PolicyServiceUnavailableException.class);
    assertThatThrownBy(() -> adapter.validate(request()))
        .isInstanceOf(PolicyServiceUnavailableException.class)
        .hasMessageContaining("circuit is open");
    assertThat(calls).hasValue(2);
  }

  private HttpPolicyValidationAdapter adapter(int maxAttempts, int minimumCalls) {
    return adapter(maxAttempts, minimumCalls, Duration.ofMillis(500));
  }

  private HttpPolicyValidationAdapter adapter(
      int maxAttempts, int minimumCalls, Duration readTimeout) {
    PolicyServiceProperties properties =
        new PolicyServiceProperties(
            URI.create("http://localhost:" + server.getAddress().getPort()),
            Duration.ofMillis(250),
            readTimeout,
            new PolicyServiceProperties.Retry(maxAttempts, Duration.ofMillis(1)),
            new PolicyServiceProperties.CircuitBreaker(
                50, 2, minimumCalls, Duration.ofSeconds(10)));
    PolicyClientConfiguration configuration = new PolicyClientConfiguration();
    RestClient restClient = configuration.policyRestClient(properties);
    Retry retry = configuration.policyServiceRetry(properties);
    CircuitBreaker circuitBreaker = configuration.policyServiceCircuitBreaker(properties);
    return new HttpPolicyValidationAdapter(
        restClient, retry, circuitBreaker, () -> "contract-correlation-1001");
  }

  private PolicyValidationRequest request() {
    return new PolicyValidationRequest("POL-AUTO-1001", ClaimType.AUTO, LocalDate.of(2026, 1, 10));
  }

  private void startServer(HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/api/v1/policies/validation", handler);
    server.start();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] response = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }
}
