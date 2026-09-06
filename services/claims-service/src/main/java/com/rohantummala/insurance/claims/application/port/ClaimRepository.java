package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository {

  boolean saveIfAbsent(Claim claim);

  Optional<Claim> findById(UUID id);

  ClaimPage findAll(ClaimQuery query);
}
