package com.rohantummala.insurance.policies.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
class PolicyValidationApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsAcceptedAndRejectedBusinessDecisions() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/policies/validation")
                .header(CorrelationIdFilter.HEADER_NAME, "policy-api-1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("pol-auto-1001", "AUTO", "2026-01-10")))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "policy-api-1001"))
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.code").value("VALID"));

    mockMvc
        .perform(
            post("/api/v1/policies/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("POL-AUTO-1001", "PROPERTY", "2026-01-10")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.code").value("CLAIM_TYPE_NOT_COVERED"));
  }

  @Test
  void returnsProblemDetailsForInvalidAndMalformedRequests() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/policies/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("", "AUTO", "2026-01-10")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors.policyNumber").exists())
        .andExpect(jsonPath("$.correlationId").isNotEmpty());

    mockMvc
        .perform(
            post("/api/v1/policies/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("POL-AUTO-1001", "UNKNOWN", "2026-01-10")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem:malformed-policy-request"));
  }

  @Test
  void publishesHealthAndOpenApiDocuments() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_info")));
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/policies/validation'].post").exists());
  }

  private String request(String number, String type, String date) {
    return """
        {
          "policyNumber": "%s",
          "claimType": "%s",
          "incidentDate": "%s"
        }
        """
        .formatted(number, type, date);
  }
}
