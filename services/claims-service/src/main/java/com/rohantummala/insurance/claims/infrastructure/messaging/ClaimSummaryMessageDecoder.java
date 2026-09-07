package com.rohantummala.insurance.claims.infrastructure.messaging;

import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.exception.InvalidClaimSummaryEventException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!in-memory")
public class ClaimSummaryMessageDecoder {

  static final int MAX_MESSAGE_BYTES = 65_536;

  private final ObjectMapper objectMapper;
  private final Validator validator;

  public ClaimSummaryMessageDecoder(ObjectMapper objectMapper, Validator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  public ClaimSummaryCompletedEnvelope decode(byte[] body) {
    if (body.length > MAX_MESSAGE_BYTES) {
      throw new InvalidClaimSummaryEventException("Claim summary event exceeds the size limit");
    }

    ClaimSummaryCompletedEnvelope event;
    try {
      event = objectMapper.readValue(body, ClaimSummaryCompletedEnvelope.class);
    } catch (JacksonException exception) {
      throw new InvalidClaimSummaryEventException(
          "Claim summary event is not valid JSON", exception);
    }

    Set<ConstraintViolation<ClaimSummaryCompletedEnvelope>> violations = validator.validate(event);
    if (!violations.isEmpty()) {
      throw new InvalidClaimSummaryEventException("Claim summary event failed schema validation");
    }
    return event;
  }
}
