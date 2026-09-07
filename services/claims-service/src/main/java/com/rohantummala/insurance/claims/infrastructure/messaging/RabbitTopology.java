package com.rohantummala.insurance.claims.infrastructure.messaging;

public final class RabbitTopology {

  public static final String CLAIMS_EVENTS_EXCHANGE = "claims.events";
  public static final String CLAIM_SUMMARY_REQUESTS_QUEUE = "claim.summary.requests.v1";
  public static final String CLAIM_SUMMARY_REQUESTS_RETRY_QUEUE = "claim.summary.requests.retry.v1";
  public static final String CLAIM_SUMMARY_REQUESTS_DEAD_LETTER_QUEUE =
      "claim.summary.requests.dlq.v1";
  public static final String CLAIM_SUBMITTED_ROUTING_KEY = "claim.submitted.v1";
  public static final String CLAIM_SUMMARY_RESULTS_QUEUE = "claim.summary.results.v1";
  public static final String CLAIM_SUMMARY_COMPLETED_ROUTING_KEY = "claim.summary.completed.v1";
  public static final String CLAIMS_RETRY_EXCHANGE = "claims.retry";
  public static final String CLAIM_SUMMARY_RESULTS_RETRY_QUEUE = "claim.summary.results.retry.v1";
  public static final String CLAIMS_DEAD_LETTER_EXCHANGE = "claims.dead-letter";
  public static final String CLAIM_SUMMARY_RESULTS_DEAD_LETTER_QUEUE =
      "claim.summary.results.dlq.v1";

  public static final String RETRY_COUNT_HEADER = "retryCount";
  public static final String FAILURE_TYPE_HEADER = "failureType";
  public static final String DEAD_LETTERED_AT_HEADER = "deadLetteredAt";
  public static final String REPLAYED_AT_HEADER = "replayedAt";

  private RabbitTopology() {}
}
