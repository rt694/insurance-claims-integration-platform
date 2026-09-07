package com.rohantummala.insurance.claims.infrastructure.messaging;

import org.springframework.amqp.core.Message;

public interface SummaryFailureRouter {

  void routeForRetry(Message original, int retryCount, String failureType);

  void routeToDeadLetter(Message original, String failureType);

  void replay(Message original);
}
