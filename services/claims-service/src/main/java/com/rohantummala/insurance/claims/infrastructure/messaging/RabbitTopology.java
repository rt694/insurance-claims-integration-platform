package com.rohantummala.insurance.claims.infrastructure.messaging;

public final class RabbitTopology {

  public static final String CLAIMS_EVENTS_EXCHANGE = "claims.events";
  public static final String CLAIM_SUMMARY_REQUESTS_QUEUE = "claim.summary.requests.v1";
  public static final String CLAIM_SUBMITTED_ROUTING_KEY = "claim.submitted.v1";

  private RabbitTopology() {}
}
