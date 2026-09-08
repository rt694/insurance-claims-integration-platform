package com.rohantummala.insurance.claims.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedEnvelope;
import com.rohantummala.insurance.claims.application.event.ClaimSummaryCompletedPayload;
import com.rohantummala.insurance.claims.application.service.ClaimSummaryService;
import com.rohantummala.insurance.claims.domain.model.HumanReviewQueue;
import com.rohantummala.insurance.claims.support.WithMockAdminJwt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
@WithMockAdminJwt
class ClaimSummaryApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ClaimSummaryService claimSummaryService;

  @Test
  void returnsAStoredReviewerAssistanceSummary() throws Exception {
    UUID claimId = UUID.fromString(createClaim("EXT-SUMMARY-API-1001"));
    Instant generatedAt = Instant.parse("2026-03-01T11:59:55Z");
    claimSummaryService.record(event(claimId, generatedAt));

    mockMvc
        .perform(get("/api/v1/claims/{id}/summary", claimId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claimId").value(claimId.toString()))
        .andExpect(jsonPath("$.summary").value("Vehicle damage requires human review."))
        .andExpect(jsonPath("$.missingInformation[0]").value("Police report"))
        .andExpect(jsonPath("$.recommendedHumanReviewQueue").value("STANDARD_REVIEW"))
        .andExpect(jsonPath("$.safetyFlags[0]").value("DESCRIPTION_REQUIRES_REVIEW"))
        .andExpect(jsonPath("$.generatedAt").value(generatedAt.toString()));
  }

  @Test
  void returnsProblemDetailsWhenAnExistingClaimHasNoSummary() throws Exception {
    String claimId = createClaim("EXT-SUMMARY-PENDING-1001");

    mockMvc
        .perform(get("/api/v1/claims/{id}/summary", claimId))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:problem:claim-summary-not-found"))
        .andExpect(jsonPath("$.title").value("Claim summary not available"))
        .andExpect(
            jsonPath("$.detail").value("A summary is not yet available for claim " + claimId));
  }

  @Test
  void includesTheSummaryEndpointInOpenApi() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/claims/{id}/summary'].get").exists());
  }

  private ClaimSummaryCompletedEnvelope event(UUID claimId, Instant generatedAt) {
    return new ClaimSummaryCompletedEnvelope(
        UUID.randomUUID(),
        ClaimSummaryCompletedEnvelope.EVENT_TYPE,
        ClaimSummaryCompletedEnvelope.EVENT_VERSION,
        ClaimSummaryCompletedEnvelope.AGGREGATE_TYPE,
        claimId,
        "summary-api-correlation",
        generatedAt,
        new ClaimSummaryCompletedPayload(
            claimId,
            "Vehicle damage requires human review.",
            List.of("Police report"),
            HumanReviewQueue.STANDARD_REVIEW,
            List.of("DESCRIPTION_REQUIRES_REVIEW"),
            generatedAt));
  }

  private String createClaim(String externalReference) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/claims")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest(externalReference)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.id");
  }

  private String validRequest(String externalReference) {
    return """
        {
          "externalReference": "%s",
          "policyNumber": "POL-AUTO-1001",
          "claimantName": "Synthetic Summary Claimant",
          "claimType": "AUTO",
          "incidentDate": "2026-01-10",
          "description": "Synthetic summary API test claim",
          "estimatedLoss": 1250.00
        }
        """
        .formatted(externalReference);
  }
}
