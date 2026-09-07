package com.rohantummala.insurance.claims.application.service;

public record OutboxPublicationResult(int claimed, int published, int failed) {}
