package com.rohantummala.insurance.claims.infrastructure.messaging;

public class MessageRoutingException extends RuntimeException {

  public MessageRoutingException(String message) {
    super(message);
  }

  public MessageRoutingException(String message, Throwable cause) {
    super(message, cause);
  }
}
