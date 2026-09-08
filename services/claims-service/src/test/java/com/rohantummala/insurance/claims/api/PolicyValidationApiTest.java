package com.rohantummala.insurance.claims.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rohantummala.insurance.claims.application.exception.InvalidPolicyServiceResponseException;
import com.rohantummala.insurance.claims.application.exception.PolicyServiceUnavailableException;
import com.rohantummala.insurance.claims.application.policy.PolicyValidationResult;
import com.rohantummala.insurance.claims.application.port.PolicyValidationPort;
import com.rohantummala.insurance.claims.support.WithMockAdminJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
@WithMockAdminJwt
class PolicyValidationApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PolicyValidationPort policyValidationPort;

  @Test
  void returnsUnprocessableContentAndDoesNotStoreRejectedClaims() throws Exception {
    when(policyValidationPort.validate(any()))
        .thenReturn(
            new PolicyValidationResult(
                false, "CLAIM_TYPE_NOT_COVERED", "Claim type is not covered by the policy"));

    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("EXT-POLICY-REJECTED")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem:policy-validation-rejected"))
        .andExpect(jsonPath("$.code").value("CLAIM_TYPE_NOT_COVERED"))
        .andExpect(jsonPath("$.detail").value("Claim type is not covered by the policy"));

    mockMvc
        .perform(get("/api/v1/claims"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void mapsUnavailableAndInvalidUpstreamResponsesWithoutLeakingDetails() throws Exception {
    when(policyValidationPort.validate(any()))
        .thenThrow(new PolicyServiceUnavailableException("Internal connection detail"));
    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("EXT-POLICY-UNAVAILABLE")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("urn:problem:policy-service-unavailable"))
        .andExpect(jsonPath("$.detail").value("The policy could not be validated at this time"));

    doThrow(new InvalidPolicyServiceResponseException("Raw upstream body"))
        .when(policyValidationPort)
        .validate(any());
    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("EXT-POLICY-INVALID-RESPONSE")))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.type").value("urn:problem:invalid-policy-service-response"))
        .andExpect(jsonPath("$.detail").value("The policy service returned an invalid response"));
  }

  private String validRequest(String externalReference) {
    return """
        {
          "externalReference": "%s",
          "policyNumber": "POL-AUTO-1001",
          "claimantName": "Synthetic Policy Claimant",
          "claimType": "AUTO",
          "incidentDate": "2026-01-10",
          "description": "Synthetic policy integration test claim",
          "estimatedLoss": 1250.00
        }
        """
        .formatted(externalReference);
  }
}
