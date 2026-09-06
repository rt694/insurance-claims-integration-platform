package com.rohantummala.insurance.claims.infrastructure.persistence;

import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!in-memory")
public class JpaClaimRepositoryAdapter implements ClaimRepository {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

  private final SpringDataClaimRepository repository;

  public JpaClaimRepositoryAdapter(SpringDataClaimRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean saveIfAbsent(Claim claim) {
    if (repository.existsByExternalReferenceIgnoreCase(claim.externalReference())) {
      return false;
    }

    try {
      repository.saveAndFlush(JpaClaimEntity.fromDomain(claim));
      return true;
    } catch (DataIntegrityViolationException exception) {
      return false;
    }
  }

  @Override
  public Optional<Claim> findById(UUID id) {
    return repository.findById(id).map(JpaClaimEntity::toDomain);
  }

  @Override
  public ClaimPage findAll(ClaimQuery query) {
    PageRequest pageRequest = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);
    Page<JpaClaimEntity> result =
        repository.findAllFiltered(query.status(), query.claimType(), pageRequest);
    return new ClaimPage(
        result.getContent().stream().map(JpaClaimEntity::toDomain).toList(),
        query.page(),
        query.size(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
