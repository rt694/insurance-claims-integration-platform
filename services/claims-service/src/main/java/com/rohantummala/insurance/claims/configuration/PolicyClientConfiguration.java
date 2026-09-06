package com.rohantummala.insurance.claims.configuration;

import com.rohantummala.insurance.claims.application.exception.InvalidPolicyServiceResponseException;
import com.rohantummala.insurance.claims.application.exception.PolicyServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PolicyServiceProperties.class)
public class PolicyClientConfiguration {

  @Bean
  RestClient policyRestClient(PolicyServiceProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl().toString())
        .requestFactory(requestFactory)
        .build();
  }

  @Bean
  Retry policyServiceRetry(PolicyServiceProperties properties) {
    RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(properties.retry().maxAttempts())
            .waitDuration(properties.retry().waitDuration())
            .retryExceptions(PolicyServiceUnavailableException.class)
            .build();
    return Retry.of("policy-service", retryConfig);
  }

  @Bean
  CircuitBreaker policyServiceCircuitBreaker(PolicyServiceProperties properties) {
    PolicyServiceProperties.CircuitBreaker settings = properties.circuitBreaker();
    CircuitBreakerConfig circuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(settings.failureRateThreshold())
            .slidingWindowSize(settings.slidingWindowSize())
            .minimumNumberOfCalls(settings.minimumNumberOfCalls())
            .waitDurationInOpenState(settings.waitDurationInOpenState())
            .recordExceptions(
                PolicyServiceUnavailableException.class,
                InvalidPolicyServiceResponseException.class)
            .build();
    return CircuitBreaker.of("policy-service", circuitBreakerConfig);
  }
}
