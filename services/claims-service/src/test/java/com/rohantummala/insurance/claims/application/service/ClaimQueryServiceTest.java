package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimQueryServiceTest {

  @Mock private ClaimRepository claimRepository;

  private ClaimQueryService service;

  @BeforeEach
  void setUp() {
    service = new ClaimQueryService(claimRepository);
  }

  @Test
  void returnsAClaimFoundById() {
    Claim claim = claim(UUID.randomUUID());
    when(claimRepository.findById(claim.id())).thenReturn(Optional.of(claim));

    assertThat(service.getById(claim.id())).isEqualTo(claim);
  }

  @Test
  void reportsWhenAClaimDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(claimRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessage("Claim %s was not found".formatted(id));
  }

  @Test
  void delegatesAQueryToTheRepository() {
    ClaimQuery query = new ClaimQuery(0, 20, ClaimStatus.SUBMITTED, ClaimType.AUTO);
    ClaimPage page = ClaimPage.of(List.of(), query, 0);
    when(claimRepository.findAll(query)).thenReturn(page);

    assertThat(service.findAll(query)).isEqualTo(page);
    verify(claimRepository).findAll(query);
  }

  private Claim claim(UUID id) {
    Instant now = Instant.parse("2026-01-15T12:00:00Z");
    return Claim.submitted(
        id,
        "EXT-QUERY-1001",
        "POL-2001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"),
        now);
  }
}
