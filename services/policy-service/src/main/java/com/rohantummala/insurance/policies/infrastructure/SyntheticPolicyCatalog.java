package com.rohantummala.insurance.policies.infrastructure;

import com.rohantummala.insurance.policies.domain.PolicyClaimType;
import com.rohantummala.insurance.policies.domain.SyntheticPolicy;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class SyntheticPolicyCatalog {

  private final Map<String, SyntheticPolicy> policies =
      Map.of(
          "POL-AUTO-1001",
          policy("POL-AUTO-1001", true, PolicyClaimType.AUTO),
          "POL-HOME-2001",
          policy("POL-HOME-2001", true, PolicyClaimType.PROPERTY),
          "POL-MULTI-3001",
          policy("POL-MULTI-3001", true, PolicyClaimType.AUTO, PolicyClaimType.PROPERTY),
          "POL-INACTIVE-9001",
          policy("POL-INACTIVE-9001", false, PolicyClaimType.AUTO));

  public Optional<SyntheticPolicy> findByPolicyNumber(String policyNumber) {
    return Optional.ofNullable(policies.get(policyNumber.toUpperCase(Locale.ROOT)));
  }

  private static SyntheticPolicy policy(
      String number, boolean active, PolicyClaimType... coveredTypes) {
    return new SyntheticPolicy(
        number, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31), Set.of(coveredTypes), active);
  }
}
