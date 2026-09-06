package com.rohantummala.insurance.policies.api;

import com.rohantummala.insurance.policies.application.PolicyValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

  private final PolicyValidationService policyValidationService;

  public PolicyController(PolicyValidationService policyValidationService) {
    this.policyValidationService = policyValidationService;
  }

  @PostMapping("/validation")
  @Operation(summary = "Validate a synthetic policy for a proposed claim")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Validation decision returned"),
    @ApiResponse(responseCode = "400", description = "Request validation failed")
  })
  PolicyValidationResponse validate(@Valid @RequestBody PolicyValidationRequest request) {
    return PolicyValidationResponse.from(
        policyValidationService.validate(
            request.policyNumber(), request.claimType(), request.incidentDate()));
  }
}
