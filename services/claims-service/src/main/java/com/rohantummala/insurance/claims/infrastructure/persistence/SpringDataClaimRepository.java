package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataClaimRepository extends JpaRepository<JpaClaimEntity, UUID> {

  boolean existsByExternalReferenceIgnoreCase(String externalReference);

  @Query(
      """
      SELECT claim
      FROM JpaClaimEntity claim
      WHERE (:status IS NULL OR claim.status = :status)
        AND (:claimType IS NULL OR claim.claimType = :claimType)
      """)
  Page<JpaClaimEntity> findAllFiltered(
      @Param("status") ClaimStatus status,
      @Param("claimType") ClaimType claimType,
      Pageable pageable);
}
