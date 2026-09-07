package com.rohantummala.insurance.claims.application.service;

import com.rohantummala.insurance.claims.domain.model.ClaimSummary;

public record SummaryProcessingResult(Status status, ClaimSummary summary) {

  public enum Status {
    STORED,
    DUPLICATE
  }

  public static SummaryProcessingResult stored(ClaimSummary summary) {
    return new SummaryProcessingResult(Status.STORED, summary);
  }

  public static SummaryProcessingResult duplicate() {
    return new SummaryProcessingResult(Status.DUPLICATE, null);
  }
}
