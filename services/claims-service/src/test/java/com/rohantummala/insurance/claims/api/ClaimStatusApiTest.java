package com.rohantummala.insurance.claims.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rohantummala.insurance.claims.support.WithMockAdminJwt;
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
class ClaimStatusApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void transitionsAClaimAndReturnsItsChronologicalHistory() throws Exception {
    String id = createClaim("EXT-STATUS-API-1001");

    mockMvc
        .perform(
            patch("/api/v1/claims/{id}/status", id)
                .header(CorrelationIdFilter.HEADER_NAME, "status-api-1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "status-api-1001"))
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

    mockMvc
        .perform(get("/api/v1/claims/{id}/history", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].claimId").value(id))
        .andExpect(jsonPath("$[0].previousStatus").doesNotExist())
        .andExpect(jsonPath("$[0].newStatus").value("SUBMITTED"))
        .andExpect(jsonPath("$[1].previousStatus").value("SUBMITTED"))
        .andExpect(jsonPath("$[1].newStatus").value("UNDER_REVIEW"));
  }

  @Test
  void rejectsAnInvalidLifecycleTransitionWithoutAddingHistory() throws Exception {
    String id = createClaim("EXT-STATUS-API-INVALID");

    mockMvc
        .perform(
            patch("/api/v1/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:problem:invalid-claim-status-transition"))
        .andExpect(jsonPath("$.title").value("Invalid claim status transition"))
        .andExpect(jsonPath("$.detail").value("Cannot transition claim from SUBMITTED to APPROVED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());

    mockMvc
        .perform(get("/api/v1/claims/{id}/history", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void validatesStatusRequestsAndMissingClaims() throws Exception {
    String id = createClaim("EXT-STATUS-API-VALIDATION");

    mockMvc
        .perform(
            patch("/api/v1/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.status").exists());

    mockMvc
        .perform(
            patch("/api/v1/claims/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem:claim-not-found"));

    mockMvc
        .perform(get("/api/v1/claims/{id}/history", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void documentsStatusAndHistoryOperationsInOpenApi() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/claims/{id}/status'].patch").exists())
        .andExpect(jsonPath("$.paths['/api/v1/claims/{id}/history'].get").exists());
  }

  private String createClaim(String externalReference) throws Exception {
    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/claims")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest(externalReference)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(responseBody, "$.id");
  }

  private String validRequest(String externalReference) {
    return """
        {
          "externalReference": "%s",
          "policyNumber": "POL-STATUS-2001",
          "claimantName": "Synthetic Status Claimant",
          "claimType": "AUTO",
          "incidentDate": "2026-01-10",
          "description": "Synthetic status API test claim",
          "estimatedLoss": 1250.00
        }
        """
        .formatted(externalReference);
  }
}
