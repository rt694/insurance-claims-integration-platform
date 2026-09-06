package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClaimRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String externalReference,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String policyNumber,
    @NotBlank @Size(max = 200) String claimantName,
    @NotNull ClaimType claimType,
    @NotNull @PastOrPresent LocalDate incidentDate,
    @NotBlank @Size(max = 4000) String description,
    @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal estimatedLoss) {

  public CreateClaimRequest {
    externalReference = ClaimInputNormalizer.identifier(externalReference);
    policyNumber = ClaimInputNormalizer.identifier(policyNumber);
    claimantName = ClaimInputNormalizer.personName(claimantName);
    description = ClaimInputNormalizer.description(description);
  }

  SubmitClaimCommand toCommand() {
    return new SubmitClaimCommand(
        externalReference,
        policyNumber,
        claimantName,
        claimType,
        incidentDate,
        description,
        estimatedLoss);
  }
}
