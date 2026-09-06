package com.rohantummala.insurance.claims.application.query;

import com.rohantummala.insurance.claims.domain.model.Claim;
import java.util.List;

public record ClaimPage(
    List<Claim> content, int page, int size, long totalElements, long totalPages) {

  public ClaimPage {
    content = List.copyOf(content);
  }

  public static ClaimPage of(List<Claim> content, ClaimQuery query, long totalElements) {
    long totalPages = totalElements == 0 ? 0 : ((totalElements - 1) / query.size()) + 1;
    return new ClaimPage(content, query.page(), query.size(), totalElements, totalPages);
  }
}
