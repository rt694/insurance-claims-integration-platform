package com.rohantummala.insurance.claims.infrastructure.inmemory;

import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryClaimRepository implements ClaimRepository {

  private final ConcurrentMap<String, Claim> claimsByExternalReference = new ConcurrentHashMap<>();

  @Override
  public boolean saveIfAbsent(Claim claim) {
    String normalizedReference = claim.externalReference().toUpperCase(Locale.ROOT);
    return claimsByExternalReference.putIfAbsent(normalizedReference, claim) == null;
  }
}
