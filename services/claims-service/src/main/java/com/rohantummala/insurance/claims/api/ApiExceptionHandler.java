package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.exception.ClaimSummaryNotFoundException;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimQueryException;
import com.rohantummala.insurance.claims.application.exception.InvalidPolicyServiceResponseException;
import com.rohantummala.insurance.claims.application.exception.PolicyServiceUnavailableException;
import com.rohantummala.insurance.claims.application.exception.PolicyValidationRejectedException;
import com.rohantummala.insurance.claims.domain.exception.InvalidClaimStatusTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

  @ExceptionHandler(ClaimNotFoundException.class)
  ProblemDetail handleNotFound(ClaimNotFoundException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.NOT_FOUND,
        "Claim not found",
        exception.getMessage(),
        "urn:problem:claim-not-found",
        request);
  }

  @ExceptionHandler(ClaimSummaryNotFoundException.class)
  ProblemDetail handleSummaryNotFound(
      ClaimSummaryNotFoundException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.NOT_FOUND,
        "Claim summary not available",
        exception.getMessage(),
        "urn:problem:claim-summary-not-found",
        request);
  }

  @ExceptionHandler({InvalidClaimQueryException.class, MethodArgumentTypeMismatchException.class})
  ProblemDetail handleInvalidQuery(Exception exception, HttpServletRequest request) {
    String detail =
        exception instanceof InvalidClaimQueryException
            ? exception.getMessage()
            : "One or more path or query parameters could not be parsed";
    return baseProblem(
        HttpStatus.BAD_REQUEST,
        "Invalid request parameter",
        detail,
        "urn:problem:invalid-request-parameter",
        request);
  }

  @ExceptionHandler(InvalidClaimStatusTransitionException.class)
  ProblemDetail handleInvalidStatusTransition(
      InvalidClaimStatusTransitionException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.CONFLICT,
        "Invalid claim status transition",
        exception.getMessage(),
        "urn:problem:invalid-claim-status-transition",
        request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail handleConcurrentUpdate(
      ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.CONFLICT,
        "Claim was updated concurrently",
        "Reload the claim and retry the status transition",
        "urn:problem:concurrent-claim-update",
        request);
  }

  @ExceptionHandler(PolicyValidationRejectedException.class)
  ProblemDetail handlePolicyRejection(
      PolicyValidationRejectedException exception, HttpServletRequest request) {
    ProblemDetail problem =
        baseProblem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Policy validation rejected the claim",
            exception.getMessage(),
            "urn:problem:policy-validation-rejected",
            request);
    problem.setProperty("code", exception.getCode());
    return problem;
  }

  @ExceptionHandler(PolicyServiceUnavailableException.class)
  ProblemDetail handlePolicyServiceUnavailable(
      PolicyServiceUnavailableException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Policy validation unavailable",
        "The policy could not be validated at this time",
        "urn:problem:policy-service-unavailable",
        request);
  }

  @ExceptionHandler(InvalidPolicyServiceResponseException.class)
  ProblemDetail handleInvalidPolicyResponse(
      InvalidPolicyServiceResponseException exception, HttpServletRequest request) {
    return baseProblem(
        HttpStatus.BAD_GATEWAY,
        "Invalid policy service response",
        "The policy service returned an invalid response",
        "urn:problem:invalid-policy-service-response",
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
