package com.rohantummala.insurance.claims.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataClaimStatusHistoryRepository
    extends JpaRepository<JpaClaimStatusHistoryEntity, UUID> {

  List<JpaClaimStatusHistoryEntity> findByClaimIdOrderByChangedAtAscIdAsc(UUID claimId);
}
