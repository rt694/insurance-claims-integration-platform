package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.exception.PolicyValidationRejectedException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationRequest;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.domain.model.Claim;
import org.springframework.stereotype.Service;

@Service
public class ClaimSubmissionService {

  private final PolicyValidationPort policyValidationPort;
  private final ClaimCreationService claimCreationService;

  public ClaimSubmissionService(
      PolicyValidationPort policyValidationPort, ClaimCreationService claimCreationService) {
    this.policyValidationPort = policyValidationPort;
    this.claimCreationService = claimCreationService;
  }

  public Claim submit(SubmitClaimCommand command) {
    PolicyValidationResult validationResult =
        policyValidationPort.validate(
            new PolicyValidationRequest(
                command.policyNumber(), command.claimType(), command.incidentDate()));
    if (!validationResult.valid()) {
      throw new PolicyValidationRejectedException(
          validationResult.code(), validationResult.message());
    }
    return claimCreationService.create(command);
  }
}
