package com.rohantummala.insurance.claims.infrastructure.policy;

import com.rohantummala.insurance.claims.application.exception.InvalidPolicyServiceResponseException;
import com.rohantummala.insurance.claims.application.exception.PolicyServiceUnavailableException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.CorrelationIdProvider;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("!in-memory")
public class HttpPolicyValidationAdapter implements PolicyValidationPort {

  private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_]{1,64}");
  private static final int MAX_MESSAGE_LENGTH = 500;

  private final RestClient restClient;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;
  private final CorrelationIdProvider correlationIdProvider;

  public HttpPolicyValidationAdapter(
      RestClient policyRestClient,
      Retry policyServiceRetry,
      CircuitBreaker policyServiceCircuitBreaker,
      CorrelationIdProvider correlationIdProvider) {
    this.restClient = policyRestClient;
    this.retry = policyServiceRetry;
    this.circuitBreaker = policyServiceCircuitBreaker;
    this.correlationIdProvider = correlationIdProvider;
  }

  @Override
  public PolicyValidationResult validate(PolicyValidationRequest request) {
    Supplier<PolicyValidationResult> retried =
        Retry.decorateSupplier(retry, () -> callPolicyService(request));
    try {
      return CircuitBreaker.decorateSupplier(circuitBreaker, retried).get();
    } catch (CallNotPermittedException exception) {
      throw new PolicyServiceUnavailableException(
          "Policy validation is temporarily unavailable because the circuit is open", exception);
    }
  }

  private PolicyValidationResult callPolicyService(PolicyValidationRequest request) {
    try {
      RemotePolicyValidationResponse response =
          restClient
              .post()
              .uri("/api/v1/policies/validation")
              .header(CORRELATION_ID_HEADER, correlationIdProvider.currentCorrelationId())
              .body(RemotePolicyValidationRequest.from(request))
              .retrieve()
              .body(RemotePolicyValidationResponse.class);
      return validateResponse(response);
    } catch (HttpServerErrorException | ResourceAccessException exception) {
      throw new PolicyServiceUnavailableException(
          "Policy validation service is temporarily unavailable", exception);
    } catch (RestClientResponseException exception) {
      throw new InvalidPolicyServiceResponseException(
          "Policy validation service returned an unexpected HTTP response", exception);
    } catch (RestClientException exception) {
      throw new InvalidPolicyServiceResponseException(
          "Policy validation service response could not be parsed", exception);
    }
  }

  private PolicyValidationResult validateResponse(RemotePolicyValidationResponse response) {
    if (response == null
        || response.valid() == null
        || response.code() == null
        || !SAFE_CODE.matcher(response.code()).matches()
        || response.message() == null
        || response.message().isBlank()
        || response.message().length() > MAX_MESSAGE_LENGTH) {
      throw new InvalidPolicyServiceResponseException(
          "Policy validation service returned an invalid response body");
    }
    return new PolicyValidationResult(response.valid(), response.code(), response.message());
  }

  private record RemotePolicyValidationRequest(
      String policyNumber, ClaimType claimType, LocalDate incidentDate) {

    static RemotePolicyValidationRequest from(PolicyValidationRequest request) {
      return new RemotePolicyValidationRequest(
          request.policyNumber(), request.claimType(), request.incidentDate());
    }
  }

  private record RemotePolicyValidationResponse(Boolean valid, String code, String message) {}
}
