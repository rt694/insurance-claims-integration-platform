package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.domain.model.Claim;

public interface ClaimRepository {

  boolean saveIfAbsent(Claim claim);
}
