package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.application.port.ClaimStatusHistoryRepository;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!in-memory")
public class JpaClaimStatusHistoryRepositoryAdapter implements ClaimStatusHistoryRepository {

  private final SpringDataClaimStatusHistoryRepository repository;

  public JpaClaimStatusHistoryRepositoryAdapter(SpringDataClaimStatusHistoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void append(ClaimStatusChange statusChange) {
    repository.save(JpaClaimStatusHistoryEntity.fromDomain(statusChange));
  }

  @Override
  public List<ClaimStatusChange> findByClaimId(UUID claimId) {
    return repository.findByClaimIdOrderByChangedAtAscIdAsc(claimId).stream()
        .map(JpaClaimStatusHistoryEntity::toDomain)
        .toList();
  }
}
