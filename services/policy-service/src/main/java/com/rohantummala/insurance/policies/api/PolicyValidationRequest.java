package com.rohantummala.insurance.policies.api;

import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;

public record PolicyValidationRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String policyNumber,
    @NotNull PolicyClaimType claimType,
    @NotNull LocalDate incidentDate) {

  public PolicyValidationRequest {
    policyNumber = policyNumber == null ? null : policyNumber.trim().toUpperCase(Locale.ROOT);
  }
}
