package com.rohantummala.insurance.policies.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Correlation-ID";
  private static final String MDC_KEY = "correlationId";
  private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestedValue = request.getHeader(HEADER_NAME);
    String correlationId =
        requestedValue != null && SAFE_VALUE.matcher(requestedValue).matches()
            ? requestedValue
            : UUID.randomUUID().toString();
    response.setHeader(HEADER_NAME, correlationId);
    MDC.put(MDC_KEY, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
