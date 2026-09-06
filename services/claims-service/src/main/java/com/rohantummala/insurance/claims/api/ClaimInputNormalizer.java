package com.rohantummala.insurance.claims.api;

import java.util.Locale;

final class ClaimInputNormalizer {

  private ClaimInputNormalizer() {}

  static String identifier(String value) {
    return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
  }

  static String personName(String value) {
    return value == null ? null : value.strip().replaceAll("\\s+", " ");
  }

  static String description(String value) {
    return value == null ? null : value.strip();
  }
}
