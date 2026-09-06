package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.application.exception.ClaimNotFoundException;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClaimQueryService {

  private final ClaimRepository claimRepository;

  public ClaimQueryService(ClaimRepository claimRepository) {
    this.claimRepository = claimRepository;
  }

  public Claim getById(UUID id) {
    return claimRepository.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
  }

  public ClaimPage findAll(ClaimQuery query) {
    return claimRepository.findAll(query);
  }
}
