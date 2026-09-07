package com.rohantummala.insurance.claims.infrastructure.inmemory;

import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("in-memory")
public class InMemoryClaimSummaryRepository implements ClaimSummaryRepository {

  private final ConcurrentMap<UUID, ClaimSummary> summaries = new ConcurrentHashMap<>();

  @Override
  public void save(ClaimSummary summary) {
    summaries.compute(
        summary.claimId(),
        (claimId, current) ->
            current == null || !current.generatedAt().isAfter(summary.generatedAt())
                ? summary
                : current);
  }

  @Override
  public Optional<ClaimSummary> findByClaimId(UUID claimId) {
    return Optional.ofNullable(summaries.get(claimId));
  }
}
