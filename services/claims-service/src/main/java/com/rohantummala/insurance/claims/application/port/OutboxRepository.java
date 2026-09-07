package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.application.event.EventEnvelope;

public interface OutboxRepository {

  void append(EventEnvelope<?> event);
}
