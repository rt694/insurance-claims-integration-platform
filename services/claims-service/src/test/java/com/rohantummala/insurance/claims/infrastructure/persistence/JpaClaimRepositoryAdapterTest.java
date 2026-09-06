package com.rohantummala.insurance.claims.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "CLAIMS_DB_PASSWORD=test-only-placeholder")
@Testcontainers
@Transactional
class JpaClaimRepositoryAdapterTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired private ClaimRepository claimRepository;

  @Autowired private JdbcClient jdbcClient;

  @Test
  void persistsAndQueriesTheCompleteDomainModel() {
    Claim olderAuto = claim("EXT-DB-1001", ClaimType.AUTO, "2026-01-15T10:00:00Z");
    Claim property = claim("EXT-DB-1002", ClaimType.PROPERTY, "2026-01-15T11:00:00Z");
    Claim newerAuto = claim("EXT-DB-1003", ClaimType.AUTO, "2026-01-15T12:00:00Z");

    assertThat(claimRepository.saveIfAbsent(olderAuto)).isTrue();
    assertThat(claimRepository.saveIfAbsent(property)).isTrue();
    assertThat(claimRepository.saveIfAbsent(newerAuto)).isTrue();
    assertThat(claimRepository.findById(property.id())).contains(property);

    ClaimPage page =
        claimRepository.findAll(new ClaimQuery(0, 1, ClaimStatus.SUBMITTED, ClaimType.AUTO));
    assertThat(page.content()).containsExactly(newerAuto);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(2);
  }

  @Test
  void rejectsCaseInsensitiveDuplicatesThroughTheAdapter() {
    Claim original = claim("EXT-DB-DUPLICATE", ClaimType.LIFE, "2026-01-15T10:00:00Z");
    Claim duplicate = claim("ext-db-duplicate", ClaimType.LIFE, "2026-01-15T11:00:00Z");

    assertThat(claimRepository.saveIfAbsent(original)).isTrue();
    assertThat(claimRepository.saveIfAbsent(duplicate)).isFalse();
  }

  @Test
  void databaseUniqueIndexRejectsDuplicatesThatBypassTheApplication() {
    Claim original = claim("EXT-DB-CONSTRAINT", ClaimType.AUTO, "2026-01-15T10:00:00Z");
    assertThat(claimRepository.saveIfAbsent(original)).isTrue();

    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        """
                        INSERT INTO claims (
                            id, external_reference, policy_number, claimant_name, claim_type,
                            incident_date, description, estimated_loss, status, created_at, updated_at
                        ) VALUES (
                            :id, :externalReference, 'POL-2001', 'Synthetic Claimant', 'AUTO',
                            DATE '2026-01-10', 'Synthetic duplicate', 100.00, 'SUBMITTED',
                            TIMESTAMPTZ '2026-01-15 11:00:00Z', TIMESTAMPTZ '2026-01-15 11:00:00Z'
                        )
                        """)
                    .param("id", UUID.randomUUID())
                    .param("externalReference", "ext-db-constraint")
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void flywayOwnsTheDatabaseSchema() {
    Integer migrationCount =
        jdbcClient
            .sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success")
            .query(Integer.class)
            .single();

    assertThat(migrationCount).isEqualTo(1);
  }

  private Claim claim(String reference, ClaimType claimType, String createdAt) {
    Instant timestamp = Instant.parse(createdAt);
    return Claim.submitted(
        UUID.randomUUID(),
        reference,
        "POL-2001",
        "Synthetic Claimant",
        claimType,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description",
        new BigDecimal("1250.00"),
        timestamp);
  }
}
