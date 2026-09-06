package com.rohantummala.insurance.claims.infrastructure.inmemory;

import com.rohantummala.insurance.claims.application.port.ClaimStatusHistoryRepository;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("in-memory")
public class InMemoryClaimStatusHistoryRepository implements ClaimStatusHistoryRepository {

  private static final Comparator<ClaimStatusChange> CHRONOLOGICAL_ORDER =
      Comparator.comparing(ClaimStatusChange::changedAt).thenComparing(ClaimStatusChange::id);

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<ClaimStatusChange>> historyByClaimId =
      new ConcurrentHashMap<>();

  @Override
  public void append(ClaimStatusChange statusChange) {
    historyByClaimId
        .computeIfAbsent(statusChange.claimId(), ignored -> new CopyOnWriteArrayList<>())
        .add(statusChange);
  }

  @Override
  public List<ClaimStatusChange> findByClaimId(UUID claimId) {
    return historyByClaimId.getOrDefault(claimId, new CopyOnWriteArrayList<>()).stream()
        .sorted(CHRONOLOGICAL_ORDER)
        .toList();
  }
}
