package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimStatusHistoryRepository;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimStatusService {

  private final ClaimRepository claimRepository;
  private final ClaimStatusHistoryRepository historyRepository;
  private final Clock clock;

  public ClaimStatusService(
      ClaimRepository claimRepository,
      ClaimStatusHistoryRepository historyRepository,
      Clock clock) {
    this.claimRepository = claimRepository;
    this.historyRepository = historyRepository;
    this.clock = clock;
  }

  @Transactional
  public Claim transition(UUID claimId, ClaimStatus requestedStatus) {
    Claim currentClaim = findClaim(claimId);
    Instant changedAt = clock.instant();
    Claim updatedClaim = currentClaim.transitionTo(requestedStatus, changedAt);

    Claim savedClaim = claimRepository.update(updatedClaim);
    historyRepository.append(
        ClaimStatusChange.transition(
            UUID.randomUUID(), savedClaim, currentClaim.status(), changedAt));
    return savedClaim;
  }

  @Transactional(readOnly = true)
  public List<ClaimStatusChange> getHistory(UUID claimId) {
    findClaim(claimId);
    return historyRepository.findByClaimId(claimId);
  }

  private Claim findClaim(UUID claimId) {
    return claimRepository.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));
  }
}
