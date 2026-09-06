package com.rohantummala.insurance.claims.application.command;

import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SubmitClaimCommand(
    String externalReference,
    String policyNumber,
    String claimantName,
    ClaimType claimType,
    LocalDate incidentDate,
    String description,
    BigDecimal estimatedLoss) {}
