package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claims")
class JpaClaimEntity {

  @Id private UUID id;

  @Column(name = "external_reference", nullable = false, length = 100)
  private String externalReference;

  @Column(name = "policy_number", nullable = false, length = 50)
  private String policyNumber;

  @Column(name = "claimant_name", nullable = false, length = 200)
  private String claimantName;

  @Enumerated(EnumType.STRING)
  @Column(name = "claim_type", nullable = false, length = 30)
  private ClaimType claimType;

  @Column(name = "incident_date", nullable = false)
  private LocalDate incidentDate;

  @Column(nullable = false, length = 4000)
  private String description;

  @Column(name = "estimated_loss", nullable = false, precision = 19, scale = 2)
  private BigDecimal estimatedLoss;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ClaimStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected JpaClaimEntity() {}

  private JpaClaimEntity(Claim claim) {
    this.id = claim.id();
    this.externalReference = claim.externalReference();
    this.policyNumber = claim.policyNumber();
    this.claimantName = claim.claimantName();
    this.claimType = claim.claimType();
    this.incidentDate = claim.incidentDate();
    this.description = claim.description();
    this.estimatedLoss = claim.estimatedLoss();
    this.status = claim.status();
    this.createdAt = claim.createdAt();
    this.updatedAt = claim.updatedAt();
  }

  static JpaClaimEntity fromDomain(Claim claim) {
    return new JpaClaimEntity(claim);
  }

  Claim toDomain() {
    return new Claim(
        id,
        externalReference,
        policyNumber,
        claimantName,
        claimType,
        incidentDate,
        description,
        estimatedLoss,
        status,
        createdAt,
        updatedAt);
  }

  void applyStatus(Claim claim) {
    this.status = claim.status();
    this.updatedAt = claim.updatedAt();
  }
}
