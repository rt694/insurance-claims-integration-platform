package com.rohantummala.insurance.claims.infrastructure.messaging;

public class EventPublicationException extends RuntimeException {

  public EventPublicationException(String message) {
    super(message);
  }

  public EventPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
