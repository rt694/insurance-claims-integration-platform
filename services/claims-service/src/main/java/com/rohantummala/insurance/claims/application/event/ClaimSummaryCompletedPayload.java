package com.rohantummala.insurance.claims.application.event;

import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClaimSummaryCompletedPayload(
    @NotNull UUID claimId,
    @NotBlank @Size(max = 2_000) String summary,
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> missingInformation,
    @NotNull @Valid HumanReviewQueue recommendedHumanReviewQueue,
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> safetyFlags,
    @NotNull Instant generatedAt) {}
