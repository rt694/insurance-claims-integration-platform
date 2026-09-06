package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    ProblemDetail problem =
        baseProblem(
            HttpStatus.BAD_REQUEST,
            "Invalid claim request",
            "One or more request fields failed validation",
            "urn:problem:request-validation",
            request);

    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(DuplicateClaimExternalReferenceException.class)
  ProblemDetail handleDuplicate(
      DuplicateClaimExternalReferenceException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.CONFLICT,
        "Duplicate external reference",
        exception.getMessage(),
        "urn:problem:duplicate-external-reference",
        request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleUnreadableRequest(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.BAD_REQUEST,
        "Malformed JSON request",
        "The request body could not be parsed",
        "urn:problem:malformed-json",
        request);
  }

  private ProblemDetail baseProblem(
      HttpStatus status, String title, String detail, String type, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create(type));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
    return problem;
  }
}
