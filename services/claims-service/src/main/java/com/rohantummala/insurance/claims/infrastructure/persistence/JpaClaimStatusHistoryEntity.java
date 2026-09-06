package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_status_history")
class JpaClaimStatusHistoryEntity {

  @Id private UUID id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", length = 30)
  private ClaimStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false, length = 30)
  private ClaimStatus newStatus;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected JpaClaimStatusHistoryEntity() {}

  private JpaClaimStatusHistoryEntity(ClaimStatusChange statusChange) {
    this.id = statusChange.id();
    this.claimId = statusChange.claimId();
    this.previousStatus = statusChange.previousStatus();
    this.newStatus = statusChange.newStatus();
    this.changedAt = statusChange.changedAt();
  }

  static JpaClaimStatusHistoryEntity fromDomain(ClaimStatusChange statusChange) {
    return new JpaClaimStatusHistoryEntity(statusChange);
  }

  ClaimStatusChange toDomain() {
    return new ClaimStatusChange(id, claimId, previousStatus, newStatus, changedAt);
  }
}
