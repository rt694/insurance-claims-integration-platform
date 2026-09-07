package com.rohantummala.insurance.claims.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.ClaimRepository;
import com.rohantummala.insurance.claims.application.port.ClaimSummaryRepository;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.application.query.ClaimPage;
import com.rohantummala.insurance.claims.application.query.ClaimQuery;
import com.rohantummala.insurance.claims.application.service.ClaimStatusService;
import com.rohantummala.insurance.claims.application.service.ClaimSubmissionService;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "CLAIMS_DB_PASSWORD=test-only-placeholder",
      "integration.outbox.publisher-enabled=false",
      "integration.summary-consumer.enabled=false"
    })
@Testcontainers
@Transactional
class JpaClaimRepositoryAdapterTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired private ClaimRepository claimRepository;

  @Autowired private ClaimSummaryRepository claimSummaryRepository;

  @Autowired private JdbcClient jdbcClient;

  @Autowired private ClaimSubmissionService claimSubmissionService;

  @Autowired private ClaimStatusService claimStatusService;

  @MockitoBean private PolicyValidationPort policyValidationPort;

  @BeforeEach
  void acceptSyntheticPolicies() {
    when(policyValidationPort.validate(any()))
        .thenReturn(new PolicyValidationResult(true, "VALID", "Policy covers this claim"));
  }

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

    assertThat(migrationCount).isEqualTo(5);
  }

  @Test
  void submissionAndStatusTransitionProduceAnAtomicAuditTrail() {
    Claim submitted =
        claimSubmissionService.submit(
            new SubmitClaimCommand(
                "EXT-DB-HISTORY",
                "POL-2001",
                "Synthetic Claimant",
                ClaimType.DISABILITY,
                LocalDate.of(2026, 1, 10),
                "Synthetic history integration test",
                new BigDecimal("1250.00")));

    Claim updated = claimStatusService.transition(submitted.id(), ClaimStatus.UNDER_REVIEW);

    assertThat(updated.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    assertThat(claimRepository.findById(submitted.id())).contains(updated);
    assertThat(claimStatusService.getHistory(submitted.id()))
        .extracting(change -> change.previousStatus() + "->" + change.newStatus())
        .containsExactly("null->SUBMITTED", "SUBMITTED->UNDER_REVIEW");
    Long version =
        jdbcClient
            .sql("SELECT version FROM claims WHERE id = :id")
            .param("id", submitted.id())
            .query(Long.class)
            .single();
    assertThat(version).isEqualTo(1);

    Integer outboxCount =
        jdbcClient
            .sql(
                """
                SELECT COUNT(*) FROM outbox_events
                WHERE aggregate_id = :claimId
                  AND event_type = 'claim.submitted'
                  AND event_version = 1
                  AND status = 'PENDING'
                  AND attempt_count = 0
                """)
            .param("claimId", submitted.id())
            .query(Integer.class)
            .single();
    assertThat(outboxCount).isEqualTo(1);

    String payloadClaimId =
        jdbcClient
            .sql(
                """
                SELECT payload #>> '{data,claimId}' FROM outbox_events
                WHERE aggregate_id = :claimId
                """)
            .param("claimId", submitted.id())
            .query(String.class)
            .single();
    assertThat(payloadClaimId).isEqualTo(submitted.id().toString());
  }

  @Test
  void aLateOlderSummaryCannotOverwriteANewerGeneratedSummary() {
    Claim claim = claim("EXT-DB-SUMMARY", ClaimType.AUTO, "2026-01-15T10:00:00Z");
    assertThat(claimRepository.saveIfAbsent(claim)).isTrue();
    ClaimSummary newer =
        summary(
            claim.id(),
            "Newer reviewer-assistance summary",
            "2026-01-15T12:00:00Z",
            "2026-01-15T12:00:01Z");
    ClaimSummary lateOlder =
        summary(
            claim.id(),
            "Older late-arriving summary",
            "2026-01-15T11:00:00Z",
            "2026-01-15T13:00:00Z");

    claimSummaryRepository.save(newer);
    claimSummaryRepository.save(lateOlder);

    assertThat(claimSummaryRepository.findByClaimId(claim.id())).contains(newer);
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

  private ClaimSummary summary(UUID claimId, String text, String generatedAt, String receivedAt) {
    return new ClaimSummary(
        claimId,
        UUID.randomUUID(),
        text,
        List.of("Police report"),
        HumanReviewQueue.STANDARD_REVIEW,
        List.of(),
        Instant.parse(generatedAt),
        Instant.parse(receivedAt));
  }
}
