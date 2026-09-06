package com.rohantummala.insurance.claims.infrastructure.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryClaimRepositoryTest {

  private InMemoryClaimRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryClaimRepository();
  }

  @Test
  void findsASavedClaimById() {
    Claim claim = claim("EXT-1001", ClaimType.AUTO, "2026-01-15T12:00:00Z");
    repository.saveIfAbsent(claim);

    assertThat(repository.findById(claim.id())).contains(claim);
    assertThat(repository.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void filtersOrdersAndPaginatesClaims() {
    Claim oldestAuto = claim("EXT-1001", ClaimType.AUTO, "2026-01-15T10:00:00Z");
    Claim property = claim("EXT-1002", ClaimType.PROPERTY, "2026-01-15T11:00:00Z");
    Claim newestAuto = claim("EXT-1003", ClaimType.AUTO, "2026-01-15T12:00:00Z");
    repository.saveIfAbsent(oldestAuto);
    repository.saveIfAbsent(property);
    repository.saveIfAbsent(newestAuto);

    ClaimPage firstPage = repository.findAll(new ClaimQuery(0, 1, null, ClaimType.AUTO));
    ClaimPage secondPage = repository.findAll(new ClaimQuery(1, 1, null, ClaimType.AUTO));

    assertThat(firstPage.content()).containsExactly(newestAuto);
    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.content()).containsExactly(oldestAuto);
  }

  @Test
  void returnsAnEmptyPageWhenTheRequestedPageIsPastTheEnd() {
    repository.saveIfAbsent(claim("EXT-1001", ClaimType.AUTO, "2026-01-15T12:00:00Z"));

    ClaimPage page = repository.findAll(new ClaimQuery(10, 20, null, null));

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  private Claim claim(String reference, ClaimType claimType, String createdAt) {
    Instant timestamp = Instant.parse(createdAt);
    return Claim.submitted(
        UUID.randomUUID(),
        reference,
        "POL-2001",
        "Synthetic Claimant",
        claimType,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"),
        timestamp);
  }
}
