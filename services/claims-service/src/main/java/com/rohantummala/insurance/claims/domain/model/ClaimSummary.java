package com.rohantummala.insurance.claims.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimSummary(
    UUID claimId,
    UUID sourceEventId,
    String summary,
    List<String> missingInformation,
    HumanReviewQueue recommendedHumanReviewQueue,
    List<String> safetyFlags,
    Instant generatedAt,
    Instant receivedAt) {

  public ClaimSummary {
    Objects.requireNonNull(claimId, "claimId must not be null");
    Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
    requireText(summary, "summary");
    missingInformation = immutableList(missingInformation, "missingInformation");
    Objects.requireNonNull(
        recommendedHumanReviewQueue, "recommendedHumanReviewQueue must not be null");
    safetyFlags = immutableList(safetyFlags, "safetyFlags");
    Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    Objects.requireNonNull(receivedAt, "receivedAt must not be null");
  }

  private static List<String> immutableList(List<String> values, String fieldName) {
    Objects.requireNonNull(values, fieldName + " must not be null");
    values.forEach(value -> requireText(value, fieldName + " item"));
    return List.copyOf(values);
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
