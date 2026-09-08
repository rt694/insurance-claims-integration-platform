package com.rohantummala.insurance.claims.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rohantummala.insurance.claims.support.WithMockAdminJwt;
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
class ClaimSubmissionApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void createsAndNormalizesAValidClaim() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/claims")
                .header(CorrelationIdFilter.HEADER_NAME, "test-correlation-1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("  ext-api-1001  ")))
        .andExpect(status().isCreated())
        .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "test-correlation-1001"))
        .andExpect(header().string("Location", startsWith("/api/v1/claims/")))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.externalReference").value("EXT-API-1001"))
        .andExpect(jsonPath("$.policyNumber").value("POL-2001"))
        .andExpect(jsonPath("$.claimantName").value("Synthetic Claimant"))
        .andExpect(jsonPath("$.status").value("SUBMITTED"));
  }

  @Test
  void rejectsDuplicateExternalReferencesIgnoringCase() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("EXT-API-DUPLICATE")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("ext-api-duplicate")))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Duplicate external reference"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void returnsProblemDetailsForInvalidInput() throws Exception {
    String invalidRequest =
        """
        {
          "externalReference": "",
          "policyNumber": "invalid policy number",
          "claimantName": "",
          "claimType": null,
          "incidentDate": "2999-01-01",
          "description": "",
          "estimatedLoss": -1.00
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
        .andExpect(jsonPath("$.type").value("urn:problem:request-validation"))
        .andExpect(jsonPath("$.title").value("Invalid claim request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.instance").value("/api/v1/claims"))
        .andExpect(jsonPath("$.errors.externalReference").exists())
        .andExpect(jsonPath("$.errors.policyNumber").exists())
        .andExpect(jsonPath("$.errors.claimantName").exists())
        .andExpect(jsonPath("$.errors.claimType").exists())
        .andExpect(jsonPath("$.errors.incidentDate").exists())
        .andExpect(jsonPath("$.errors.description").exists())
        .andExpect(jsonPath("$.errors.estimatedLoss").exists());
  }

  @Test
  void returnsSafeProblemDetailsForMalformedJson() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claimType\": \"NOT_A_CLAIM_TYPE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:problem:malformed-json"))
        .andExpect(jsonPath("$.title").value("Malformed JSON request"))
        .andExpect(jsonPath("$.detail").value("The request body could not be parsed"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void publishesAnOpenApiDocument() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Insurance Claims API"))
        .andExpect(jsonPath("$.paths['/api/v1/claims'].post").exists());
  }

  private String validRequest(String externalReference) {
    return """
        {
          "externalReference": "%s",
          "policyNumber": "  pol-2001 ",
          "claimantName": "  Synthetic   Claimant  ",
          "claimType": "AUTO",
          "incidentDate": "2026-01-10",
          "description": "  Synthetic incident description  ",
          "estimatedLoss": 1250.00
        }
        """
        .formatted(externalReference);
  }
}
