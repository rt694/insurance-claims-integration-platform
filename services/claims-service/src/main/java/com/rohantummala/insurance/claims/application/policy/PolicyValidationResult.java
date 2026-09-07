package com.rohantummala.insurance.claims.application.policy;

public record PolicyValidationResult(boolean valid, String code, String message) {}
