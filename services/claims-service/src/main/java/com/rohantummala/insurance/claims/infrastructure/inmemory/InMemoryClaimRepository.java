package com.rohantummala.insurance.claims.infrastructure.inmemory;

import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("in-memory")
public class InMemoryClaimRepository implements ClaimRepository {

  private final ConcurrentMap<String, Claim> claimsByExternalReference = new ConcurrentHashMap<>();

  @Override
  public boolean saveIfAbsent(Claim claim) {
    String normalizedReference = claim.externalReference().toUpperCase(Locale.ROOT);
    return claimsByExternalReference.putIfAbsent(normalizedReference, claim) == null;
  }

  @Override
  public Optional<Claim> findById(UUID id) {
    return claimsByExternalReference.values().stream()
        .filter(claim -> claim.id().equals(id))
        .findFirst();
  }

  @Override
  public ClaimPage findAll(ClaimQuery query) {
    List<Claim> matchingClaims =
        claimsByExternalReference.values().stream()
            .filter(claim -> query.status() == null || claim.status() == query.status())
            .filter(claim -> query.claimType() == null || claim.claimType() == query.claimType())
            .sorted(Comparator.comparing(Claim::createdAt).reversed().thenComparing(Claim::id))
            .toList();

    long offset = (long) query.page() * query.size();
    List<Claim> content = matchingClaims.stream().skip(offset).limit(query.size()).toList();
    return ClaimPage.of(content, query, matchingClaims.size());
  }
}
