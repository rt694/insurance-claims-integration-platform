package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.domain.model.ClaimStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateClaimStatusRequest(@NotNull ClaimStatus status) {}
