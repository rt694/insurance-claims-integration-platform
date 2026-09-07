package com.rohantummala.insurance.claims.application.port;

import com.rohantummala.insurance.claims.application.event.OutboxEventPublication;

public interface EventPublisher {

  void publish(OutboxEventPublication event);
}
