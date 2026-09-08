package com.rohantummala.insurance.claims.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityProblemResponseWriter {

  private final ObjectMapper objectMapper;

  public SecurityProblemResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    write(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        "Authentication required",
        "A valid bearer token is required to access this resource",
        "urn:problem:authentication-required");
  }

  public void writeForbidden(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    write(
        request,
        response,
        HttpStatus.FORBIDDEN,
        "Access denied",
        "The authenticated user does not have permission to access this resource",
        "urn:problem:access-denied");
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String title,
      String detail,
      String type)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create(type));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
