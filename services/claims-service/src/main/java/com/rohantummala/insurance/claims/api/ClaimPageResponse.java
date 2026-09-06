package com.rohantummala.insurance.claims.api;

import com.rohantummala.insurance.claims.application.query.ClaimPage;
import java.util.List;

public record ClaimPageResponse(
    List<ClaimResponse> content, int page, int size, long totalElements, long totalPages) {

  static ClaimPageResponse from(ClaimPage claimPage) {
    return new ClaimPageResponse(
        claimPage.content().stream().map(ClaimResponse::from).toList(),
        claimPage.page(),
        claimPage.size(),
        claimPage.totalElements(),
        claimPage.totalPages());
  }
}
