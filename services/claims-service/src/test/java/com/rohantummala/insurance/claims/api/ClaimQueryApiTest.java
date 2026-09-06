package com.rohantummala.insurance.claims.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class ClaimQueryApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void getsAnExistingClaimById() throws Exception {
    String id = createClaim("EXT-GET-1001", "DISABILITY");

    mockMvc
        .perform(get("/api/v1/claims/{id}", id).header(CorrelationIdFilter.HEADER_NAME, "get-1001"))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "get-1001"))
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.externalReference").value("EXT-GET-1001"))
        .andExpect(jsonPath("$.claimType").value("DISABILITY"));
  }

  @Test
  void browsesClaimsUsingPaginationAndFilters() throws Exception {
    createClaim("EXT-LIST-1001", "LIFE");
    createClaim("EXT-LIST-1002", "LIFE");

    mockMvc
        .perform(
            get("/api/v1/claims")
                .param("page", "0")
                .param("size", "1")
                .param("status", "SUBMITTED")
                .param("claimType", "LIFE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].claimType").value("LIFE"))
        .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void returnsProblemDetailsWhenAClaimDoesNotExist() throws Exception {
    UUID missingId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/v1/claims/{id}", missingId))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:problem:claim-not-found"))
        .andExpect(jsonPath("$.title").value("Claim not found"))
        .andExpect(jsonPath("$.detail").value("Claim %s was not found".formatted(missingId)))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void rejectsInvalidPaginationAndUnparseableFilters() throws Exception {
    mockMvc
        .perform(get("/api/v1/claims").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem:invalid-request-parameter"))
        .andExpect(jsonPath("$.detail").value("page must be greater than or equal to 0"));

    mockMvc
        .perform(get("/api/v1/claims").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));

    mockMvc
        .perform(get("/api/v1/claims").param("claimType", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request parameter"));
  }

  @Test
  void documentsBothQueryOperationsInOpenApi() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/claims'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/claims/{id}'].get").exists());
  }

  private String createClaim(String externalReference, String claimType) throws Exception {
    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/claims")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest(externalReference, claimType)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(responseBody, "$.id");
  }

  private String validRequest(String externalReference, String claimType) {
    return """
        {
          "externalReference": "%s",
          "policyNumber": "POL-QUERY-2001",
          "claimantName": "Synthetic Query Claimant",
          "claimType": "%s",
          "incidentDate": "2026-01-10",
          "description": "Synthetic query API test claim",
          "estimatedLoss": 1250.00
        }
        """
        .formatted(externalReference, claimType);
  }
}
