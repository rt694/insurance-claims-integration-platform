package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.application.service.ClaimQueryService;
import com.rohantummala.insurance.claims.application.service.ClaimSubmissionService;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

  private final ClaimSubmissionService claimSubmissionService;
  private final ClaimQueryService claimQueryService;

  public ClaimController(
      ClaimSubmissionService claimSubmissionService, ClaimQueryService claimQueryService) {
    this.claimSubmissionService = claimSubmissionService;
    this.claimQueryService = claimQueryService;
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

  @GetMapping("/{id}")
  @Operation(summary = "Get a claim by its internal identifier")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Claim found"),
    @ApiResponse(responseCode = "400", description = "Identifier is not a UUID"),
    @ApiResponse(responseCode = "404", description = "Claim not found")
  })
  ClaimResponse getClaim(@PathVariable UUID id) {
    return ClaimResponse.from(claimQueryService.getById(id));
  }

  @GetMapping
  @Operation(summary = "Browse claims with pagination and optional filters")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Claims returned"),
    @ApiResponse(responseCode = "400", description = "Query parameter is invalid")
  })
  ClaimPageResponse findClaims(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) ClaimStatus status,
      @RequestParam(required = false) ClaimType claimType) {
    return ClaimPageResponse.from(
        claimQueryService.findAll(new ClaimQuery(page, size, status, claimType)));
  }
}
