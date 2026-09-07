package com.rohantummala.insurance.claims.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rohantummala.insurance.claims.application.command.SubmitClaimCommand;
import com.rohantummala.insurance.claims.application.event.EventEnvelope;
import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;
import com.rohantummala.insurance.claims.application.exception.DuplicateClaimExternalReferenceException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.OutboxPublicationRepository;
import com.rohantummala.insurance.claims.application.port.OutboxRepository;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.application.service.ClaimSubmissionService;
import com.rohantummala.insurance.claims.domain.model.Claim;
import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "CLAIMS_DB_PASSWORD=test-only-placeholder",
      "integration.outbox.publisher-enabled=false"
    })
@Testcontainers
@Import(TransactionalOutboxIntegrationTest.OutboxFailureTestConfiguration.class)
class TransactionalOutboxIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired private ClaimSubmissionService claimSubmissionService;

  @Autowired private JdbcClient jdbcClient;

  @Autowired private ControllableOutboxRepository outboxRepository;

  @Autowired private OutboxPublicationRepository outboxPublicationRepository;

  @MockitoBean private PolicyValidationPort policyValidationPort;

  @BeforeEach
  void resetDatabaseAndDependencies() {
    outboxRepository.failAfterAppend(false);
    jdbcClient.sql("DELETE FROM outbox_events").update();
    jdbcClient.sql("DELETE FROM claim_status_history").update();
    jdbcClient.sql("DELETE FROM claims").update();
    when(policyValidationPort.validate(any()))
        .thenReturn(new PolicyValidationResult(true, "VALID", "Policy covers this claim"));
  }

  @Test
  void commitsClaimHistoryAndPendingEventTogether() {
    Claim claim = claimSubmissionService.submit(command("EXT-OUTBOX-1001"));

    assertThat(rowCount("claims")).isEqualTo(1);
    assertThat(rowCount("claim_status_history")).isEqualTo(1);
    assertThat(rowCount("outbox_events")).isEqualTo(1);

    OutboxRow row =
        jdbcClient
            .sql(
                """
                SELECT event_type, event_version, aggregate_type, aggregate_id::text,
                       correlation_id, status, attempt_count,
                       payload #>> '{data,claimId}' AS payload_claim_id,
                       payload #> '{data,claimantName}' IS NULL AS claimant_name_omitted,
                       payload #> '{data,policyNumber}' IS NULL AS policy_number_omitted
                FROM outbox_events
                """)
            .query(OutboxRow.class)
            .single();

    assertThat(row.eventType()).isEqualTo("claim.submitted");
    assertThat(row.eventVersion()).isEqualTo(1);
    assertThat(row.aggregateType()).isEqualTo("claim");
    assertThat(row.aggregateId()).isEqualTo(claim.id().toString());
    assertThat(row.correlationId()).isNotBlank();
    assertThat(row.status()).isEqualTo("PENDING");
    assertThat(row.attemptCount()).isZero();
    assertThat(row.payloadClaimId()).isEqualTo(claim.id().toString());
    assertThat(row.claimantNameOmitted()).isTrue();
    assertThat(row.policyNumberOmitted()).isTrue();
  }

  @Test
  void rollsBackAllWritesWhenOutboxAppendFails() {
    outboxRepository.failAfterAppend(true);

    assertThatThrownBy(() -> claimSubmissionService.submit(command("EXT-OUTBOX-ROLLBACK")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Simulated outbox failure");

    assertThat(rowCount("claims")).isZero();
    assertThat(rowCount("claim_status_history")).isZero();
    assertThat(rowCount("outbox_events")).isZero();
  }

  @Test
  void aDuplicateSubmissionDoesNotCreateAnotherEvent() {
    claimSubmissionService.submit(command("EXT-OUTBOX-DUPLICATE"));

    assertThatThrownBy(() -> claimSubmissionService.submit(command("ext-outbox-duplicate")))
        .isInstanceOf(DuplicateClaimExternalReferenceException.class);

    assertThat(rowCount("claims")).isEqualTo(1);
    assertThat(rowCount("claim_status_history")).isEqualTo(1);
    assertThat(rowCount("outbox_events")).isEqualTo(1);
  }

  @Test
  void anExpiredPublicationLeaseCanBeReclaimedWithoutAStaleAttemptChangingItsState() {
    claimSubmissionService.submit(command("EXT-OUTBOX-LEASE"));
    Instant firstClaimTime = Instant.now().plusSeconds(1);
    Instant leaseUntil = firstClaimTime.plusSeconds(15);

    OutboxEventPublication firstAttempt =
        outboxPublicationRepository.claimReadyBatch(1, firstClaimTime, leaseUntil).get(0);

    assertThat(
            outboxPublicationRepository.claimReadyBatch(
                1, firstClaimTime.plusSeconds(1), leaseUntil.plusSeconds(15)))
        .isEmpty();

    OutboxEventPublication recoveredAttempt =
        outboxPublicationRepository
            .claimReadyBatch(1, leaseUntil.plusSeconds(1), leaseUntil.plusSeconds(16))
            .get(0);

    assertThat(recoveredAttempt.eventId()).isEqualTo(firstAttempt.eventId());
    assertThat(recoveredAttempt.attemptNumber()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                outboxPublicationRepository.markPublished(
                    firstAttempt.eventId(),
                    firstAttempt.attemptNumber(),
                    leaseUntil.plusSeconds(2)))
        .hasMessageContaining("attempt 1")
        .hasMessageContaining("as published");

    outboxPublicationRepository.markPublished(
        recoveredAttempt.eventId(), recoveredAttempt.attemptNumber(), leaseUntil.plusSeconds(2));
    assertThat(
            jdbcClient
                .sql("SELECT status FROM outbox_events WHERE event_id = :eventId")
                .param("eventId", recoveredAttempt.eventId())
                .query(String.class)
                .single())
        .isEqualTo("PUBLISHED");
  }

  private int rowCount(String table) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
  }

  private SubmitClaimCommand command(String externalReference) {
    return new SubmitClaimCommand(
        externalReference,
        "POL-AUTO-1001",
        "Synthetic Claimant",
        ClaimType.AUTO,
        LocalDate.of(2026, 1, 10),
        "Synthetic incident description for outbox verification",
        new BigDecimal("1250.00"));
  }

  record OutboxRow(
      String eventType,
      int eventVersion,
      String aggregateType,
      String aggregateId,
      String correlationId,
      String status,
      int attemptCount,
      String payloadClaimId,
      boolean claimantNameOmitted,
      boolean policyNumberOmitted) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class OutboxFailureTestConfiguration {

    @Bean
    @Primary
    ControllableOutboxRepository controllableOutboxRepository(
        @Qualifier("postgresOutboxRepositoryAdapter") OutboxRepository delegate) {
      return new ControllableOutboxRepository(delegate);
    }
  }

  static class ControllableOutboxRepository implements OutboxRepository {

    private final OutboxRepository delegate;
    private boolean failAfterAppend;

    ControllableOutboxRepository(OutboxRepository delegate) {
      this.delegate = delegate;
    }

    void failAfterAppend(boolean failAfterAppend) {
      this.failAfterAppend = failAfterAppend;
    }

    @Override
    public void append(EventEnvelope<?> event) {
      delegate.append(event);
      if (failAfterAppend) {
        throw new IllegalStateException("Simulated outbox failure");
      }
    }
  }
}
