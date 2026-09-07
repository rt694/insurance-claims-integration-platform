package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import java.util.Optional;
import java.util.UUID;

public interface ClaimSummaryRepository {

  void save(ClaimSummary summary);

  Optional<ClaimSummary> findByClaimId(UUID claimId);
}
