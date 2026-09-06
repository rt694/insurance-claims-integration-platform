package com.rohantummala.insurance.claims.infrastructure.policy;

import com.rohantummala.insurance.claims.application.port.CorrelationIdProvider;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MdcCorrelationIdProvider implements CorrelationIdProvider {

  private static final String MDC_KEY = "correlationId";

  @Override
  public String currentCorrelationId() {
    String correlationId = MDC.get(MDC_KEY);
    return correlationId == null ? UUID.randomUUID().toString() : correlationId;
  }
}
