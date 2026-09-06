package com.rohantummala.insurance.claims.application.policy;

import com.rohantummala.insurance.claims.domain.model.ClaimType;
import java.time.LocalDate;

public record PolicyValidationRequest(
    String policyNumber, ClaimType claimType, LocalDate incidentDate) {}
