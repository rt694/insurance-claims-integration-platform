package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.application.service.ClaimSubmissionService;
import com.rohantummala.insurance.claims.domain.model.Claim;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

  private final ClaimSubmissionService claimSubmissionService;

  public ClaimController(ClaimSubmissionService claimSubmissionService) {
    this.claimSubmissionService = claimSubmissionService;
  }

  @PostMapping
  @Operation(summary = "Submit a synthetic insurance claim")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Claim created"),
    @ApiResponse(responseCode = "400", description = "Request validation failed"),
    @ApiResponse(responseCode = "409", description = "External reference already exists")
  })
  ResponseEntity<ClaimResponse> createClaim(@Valid @RequestBody CreateClaimRequest request) {
    Claim claim = claimSubmissionService.submit(request.toCommand());
    URI location = URI.create("/api/v1/claims/" + claim.id());
    return ResponseEntity.created(location).body(ClaimResponse.from(claim));
  }
}
