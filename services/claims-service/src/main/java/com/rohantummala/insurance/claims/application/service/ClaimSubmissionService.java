package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.exception.PolicyValidationRejectedException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.observability.ClaimsMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class ClaimSubmissionService {

  private final PolicyValidationPort policyValidationPort;
  private final ClaimCreationService claimCreationService;
  private final ClaimsMetrics metrics;

  public ClaimSubmissionService(
      PolicyValidationPort policyValidationPort,
      ClaimCreationService claimCreationService,
      ClaimsMetrics metrics) {
    this.policyValidationPort = policyValidationPort;
    this.claimCreationService = claimCreationService;
    this.metrics = metrics;
  }

  public Claim submit(SubmitClaimCommand command) {
    PolicyValidationResult validationResult;
    Timer.Sample policyTimer = metrics.startPolicyValidation();
    try {
      validationResult =
          policyValidationPort.validate(
              new PolicyValidationRequest(
                  command.policyNumber(), command.claimType(), command.incidentDate()));
    } catch (RuntimeException exception) {
      metrics.recordPolicyValidation(policyTimer, "error");
      metrics.recordSubmission(command.claimType(), "error");
      throw exception;
    }

    metrics.recordPolicyValidation(policyTimer, validationResult.valid() ? "accepted" : "rejected");
    if (!validationResult.valid()) {
      metrics.recordSubmission(command.claimType(), "rejected");
      throw new PolicyValidationRejectedException(
          validationResult.code(), validationResult.message());
    }

    try {
      Claim claim = claimCreationService.create(command);
      metrics.recordSubmission(command.claimType(), "accepted");
      return claim;
    } catch (DuplicateClaimExternalReferenceException exception) {
      metrics.recordSubmission(command.claimType(), "duplicate");
      throw exception;
    } catch (RuntimeException exception) {
      metrics.recordSubmission(command.claimType(), "error");
      throw exception;
    }
  }
}
