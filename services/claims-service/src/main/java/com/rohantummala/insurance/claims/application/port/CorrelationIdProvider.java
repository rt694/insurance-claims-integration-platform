package com.rohantummala.insurance.claims.application.port;

public interface CorrelationIdProvider {

  String currentCorrelationId();
}
