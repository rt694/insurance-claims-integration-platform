package com.rohantummala.insurance.claims.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimStatusChangeTest {

  @Test
  void createsAnInitialSubmittedHistoryEntry() {
    Claim claim = claim();

    ClaimStatusChange change = ClaimStatusChange.initial(UUID.randomUUID(), claim);

    assertThat(change.claimId()).isEqualTo(claim.id());
    assertThat(change.previousStatus()).isNull();
    assertThat(change.newStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(change.changedAt()).isEqualTo(claim.createdAt());
  }

  @Test
  void rejectsAHistoryEntryThatDoesNotChangeStatus() {
    assertThatThrownBy(
            () ->
                new ClaimStatusChange(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ClaimStatus.SUBMITTED,
                    ClaimStatus.SUBMITTED,
                    Instant.parse("2026-01-15T12:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("previousStatus and newStatus must be different");
  }

  private Claim claim() {
    Instant createdAt = Instant.parse("2026-01-15T10:00:00Z");
    return Claim.submitted(
        UUID.randomUUID(),
        "EXT-HISTORY-1001",
        "POL-2001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"),
        createdAt);
  }
}
