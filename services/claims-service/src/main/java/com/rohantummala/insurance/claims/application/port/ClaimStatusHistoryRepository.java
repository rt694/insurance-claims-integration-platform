package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.domain.model.ClaimStatusChange;
import java.util.List;
import java.util.UUID;

public interface ClaimStatusHistoryRepository {

  void append(ClaimStatusChange statusChange);

  List<ClaimStatusChange> findByClaimId(UUID claimId);
}
