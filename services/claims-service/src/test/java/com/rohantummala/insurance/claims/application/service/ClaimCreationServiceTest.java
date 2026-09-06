package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimStatusHistoryRepository;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
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
class ClaimCreationServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

  @Mock private ClaimRepository claimRepository;

  @Mock private ClaimStatusHistoryRepository historyRepository;

  private ClaimCreationService service;

  @BeforeEach
  void setUp() {
    service =
        new ClaimCreationService(
            claimRepository, historyRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void storesAClaimAndItsInitialHistory() {
    when(claimRepository.saveIfAbsent(any(Claim.class))).thenReturn(true);

    Claim result = service.create(command("EXT-1001"));

    ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
    verify(claimRepository).saveIfAbsent(claimCaptor.capture());
    assertThat(result).isEqualTo(claimCaptor.getValue());
    assertThat(result.id()).isNotNull();
    assertThat(result.status()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(result.createdAt()).isEqualTo(NOW);

    ArgumentCaptor<ClaimStatusChange> historyCaptor =
        ArgumentCaptor.forClass(ClaimStatusChange.class);
    verify(historyRepository).append(historyCaptor.capture());
    assertThat(historyCaptor.getValue().claimId()).isEqualTo(result.id());
    assertThat(historyCaptor.getValue().previousStatus()).isNull();
    assertThat(historyCaptor.getValue().newStatus()).isEqualTo(ClaimStatus.SUBMITTED);
  }

  @Test
  void doesNotAppendHistoryForADuplicateClaim() {
    when(claimRepository.saveIfAbsent(any(Claim.class))).thenReturn(false);

    assertThatThrownBy(() -> service.create(command("EXT-1001")))
        .isInstanceOf(DuplicateClaimExternalReferenceException.class);
    verify(historyRepository, never()).append(any());
  }

  private SubmitClaimCommand command(String externalReference) {
    return new SubmitClaimCommand(
        externalReference,
        "POL-AUTO-1001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"));
  }
}
