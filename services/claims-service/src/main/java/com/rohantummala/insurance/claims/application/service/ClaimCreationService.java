package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.event.ClaimSubmittedEventFactory;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimStatusHistoryRepository;
import com.rohantummala.insurance.claims.application.port.OutboxRepository;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimCreationService {

  private final ClaimRepository claimRepository;
  private final ClaimStatusHistoryRepository historyRepository;
  private final OutboxRepository outboxRepository;
  private final ClaimSubmittedEventFactory eventFactory;
  private final Clock clock;

  public ClaimCreationService(
      ClaimRepository claimRepository,
      ClaimStatusHistoryRepository historyRepository,
      OutboxRepository outboxRepository,
      ClaimSubmittedEventFactory eventFactory,
      Clock clock) {
    this.claimRepository = claimRepository;
    this.historyRepository = historyRepository;
    this.outboxRepository = outboxRepository;
    this.eventFactory = eventFactory;
    this.clock = clock;
  }

  @Transactional
  public Claim create(SubmitClaimCommand command) {
    Instant submittedAt = clock.instant();
    Claim claim =
        Claim.submitted(
            UUID.randomUUID(),
            command.externalReference(),
            command.policyNumber(),
            command.claimantName(),
            command.claimType(),
            command.incidentDate(),
            command.description(),
            command.estimatedLoss(),
            submittedAt);

    if (!claimRepository.saveIfAbsent(claim)) {
      throw new DuplicateClaimExternalReferenceException(command.externalReference());
    }

    historyRepository.append(ClaimStatusChange.initial(UUID.randomUUID(), claim));
    outboxRepository.append(eventFactory.create(claim));
    return claim;
  }
}
