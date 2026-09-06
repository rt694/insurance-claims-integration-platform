package com.rohantummala.insurance.claims.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimStatusHistoryRepository;
import com.rohantummala.insurance.claims.domain.exception.InvalidClaimStatusTransitionException;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimStatusServiceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:00:00Z");
  private static final Instant CHANGED_AT = Instant.parse("2026-01-15T12:00:00Z");

  @Mock private ClaimRepository claimRepository;

  @Mock private ClaimStatusHistoryRepository historyRepository;

  private ClaimStatusService service;

  @BeforeEach
  void setUp() {
    service =
        new ClaimStatusService(
            claimRepository, historyRepository, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));
  }

  @Test
  void transitionsTheClaimAndAppendsHistory() {
    Claim current = claim();
    when(claimRepository.findById(current.id())).thenReturn(Optional.of(current));
    when(claimRepository.update(any(Claim.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Claim result = service.transition(current.id(), ClaimStatus.UNDER_REVIEW);

    assertThat(result.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    assertThat(result.updatedAt()).isEqualTo(CHANGED_AT);
    verify(claimRepository).update(result);

    ArgumentCaptor<ClaimStatusChange> historyCaptor =
        ArgumentCaptor.forClass(ClaimStatusChange.class);
    verify(historyRepository).append(historyCaptor.capture());
    assertThat(historyCaptor.getValue().claimId()).isEqualTo(current.id());
    assertThat(historyCaptor.getValue().previousStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(historyCaptor.getValue().newStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    assertThat(historyCaptor.getValue().changedAt()).isEqualTo(CHANGED_AT);
  }

  @Test
  void rejectsAnInvalidTransitionBeforeWritingAnything() {
    Claim current = claim();
    when(claimRepository.findById(current.id())).thenReturn(Optional.of(current));

    assertThatThrownBy(() -> service.transition(current.id(), ClaimStatus.APPROVED))
        .isInstanceOf(InvalidClaimStatusTransitionException.class)
        .hasMessage("Cannot transition claim from SUBMITTED to APPROVED");

    verify(claimRepository, never()).update(any());
    verify(historyRepository, never()).append(any());
  }

  @Test
  void returnsChronologicalHistoryForAnExistingClaim() {
    Claim current = claim();
    ClaimStatusChange initial = ClaimStatusChange.initial(UUID.randomUUID(), current);
    when(claimRepository.findById(current.id())).thenReturn(Optional.of(current));
    when(historyRepository.findByClaimId(current.id())).thenReturn(List.of(initial));

    assertThat(service.getHistory(current.id())).containsExactly(initial);
  }

  @Test
  void reportsMissingClaimsForTransitionsAndHistory() {
    UUID missingId = UUID.randomUUID();
    when(claimRepository.findById(missingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.transition(missingId, ClaimStatus.UNDER_REVIEW))
        .isInstanceOf(ClaimNotFoundException.class);
    assertThatThrownBy(() -> service.getHistory(missingId))
        .isInstanceOf(ClaimNotFoundException.class);
  }

  private Claim claim() {
    return Claim.submitted(
        UUID.randomUUID(),
        "EXT-STATUS-1001",
        "POL-2001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"),
        CREATED_AT);
  }
}
