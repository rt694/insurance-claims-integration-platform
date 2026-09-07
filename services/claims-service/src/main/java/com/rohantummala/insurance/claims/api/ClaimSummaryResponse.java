package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.domain.model.ClaimSummary;
import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClaimSummaryResponse(
    UUID claimId,
    String summary,
    List<String> missingInformation,
    HumanReviewQueue recommendedHumanReviewQueue,
    List<String> safetyFlags,
    Instant generatedAt) {

  static ClaimSummaryResponse from(ClaimSummary summary) {
    return new ClaimSummaryResponse(
        summary.claimId(),
        summary.summary(),
        summary.missingInformation(),
        summary.recommendedHumanReviewQueue(),
        summary.safetyFlags(),
        summary.generatedAt());
  }
}
