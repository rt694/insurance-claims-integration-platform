package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimSubmissionServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

  @Mock private ClaimRepository claimRepository;

  private ClaimSubmissionService service;

  @BeforeEach
  void setUp() {
    service = new ClaimSubmissionService(claimRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void submitsAClaimThroughTheRepositoryPort() {
    when(claimRepository.saveIfAbsent(any(Claim.class))).thenReturn(true);

    Claim result = service.submit(command("EXT-1001"));

    ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
    verify(claimRepository).saveIfAbsent(claimCaptor.capture());
    assertThat(result).isEqualTo(claimCaptor.getValue());
    assertThat(result.id()).isNotNull();
    assertThat(result.status()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(result.createdAt()).isEqualTo(NOW);
    assertThat(result.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void reportsADuplicateWhenTheRepositoryRejectsTheReference() {
    when(claimRepository.saveIfAbsent(any(Claim.class))).thenReturn(false);

    assertThatThrownBy(() -> service.submit(command("EXT-1001")))
        .isInstanceOf(DuplicateClaimExternalReferenceException.class)
        .hasMessage("A claim with external reference EXT-1001 already exists");
  }

  private SubmitClaimCommand command(String externalReference) {
    return new SubmitClaimCommand(
        externalReference,
        "POL-2001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"));
  }
}
