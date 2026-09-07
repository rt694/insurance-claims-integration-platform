package com.rohantummala.insurance.policies.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public record SyntheticPolicy(
    String policyNumber,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Set<PolicyClaimType> coveredClaimTypes,
    boolean active) {

  public SyntheticPolicy {
    Objects.requireNonNull(policyNumber);
    Objects.requireNonNull(effectiveFrom);
    Objects.requireNonNull(effectiveTo);
    coveredClaimTypes = Set.copyOf(coveredClaimTypes);
  }

  public boolean coversDate(LocalDate incidentDate) {
    return !incidentDate.isBefore(effectiveFrom) && !incidentDate.isAfter(effectiveTo);
  }

  public boolean coversType(PolicyClaimType claimType) {
    return coveredClaimTypes.contains(claimType);
  }
}
