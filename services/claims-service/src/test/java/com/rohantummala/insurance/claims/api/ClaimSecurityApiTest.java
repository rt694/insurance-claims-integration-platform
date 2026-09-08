package com.rohantummala.insurance.claims.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ClaimSecurityApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JwtAuthenticationConverter jwtAuthenticationConverter;

  @Test
  void keepsHealthAndApiDocumentationPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
        .andExpect(jsonPath("$.paths['/api/v1/claims'].get.security[0]['bearer-jwt']").exists());
  }

  @Test
  void allowsOnlyTheConfiguredPortalOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/claims")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "authorization,content-type,x-correlation-id"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH,OPTIONS"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    "authorization, content-type, x-correlation-id"));

    mockMvc
        .perform(
            options("/api/v1/claims")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void returnsSafeProblemDetailsWhenAuthenticationIsMissing() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/claims")
                .header(CorrelationIdFilter.HEADER_NAME, "security-missing-token-1001"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "security-missing-token-1001"))
        .andExpect(jsonPath("$.type").value("urn:problem:authentication-required"))
        .andExpect(jsonPath("$.title").value("Authentication required"))
        .andExpect(jsonPath("$.instance").value("/api/v1/claims"))
        .andExpect(jsonPath("$.correlationId").value("security-missing-token-1001"));
  }

  @Test
  void allowsAgentsToSubmitAndReadButNotTransitionClaims() throws Exception {
    String claimId = createClaim("EXT-SECURITY-AGENT-1001", role("AGENT"));

    mockMvc.perform(get("/api/v1/claims").with(role("AGENT"))).andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v1/claims/{id}/status", claimId)
                .with(role("AGENT"))
                .header(CorrelationIdFilter.HEADER_NAME, "security-agent-denied-1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:problem:access-denied"))
        .andExpect(jsonPath("$.correlationId").value("security-agent-denied-1001"));
  }

  @Test
  void allowsReviewersToReadAndTransitionButNotSubmitClaims() throws Exception {
    String claimId = createClaim("EXT-SECURITY-REVIEWER-1001", role("ADMIN"));

    mockMvc
        .perform(get("/api/v1/claims/{id}", claimId).with(role("REVIEWER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v1/claims/{id}/status", claimId)
                .with(role("REVIEWER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

    mockMvc
        .perform(
            post("/api/v1/claims")
                .with(role("REVIEWER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("EXT-SECURITY-REVIEWER-DENIED")))
        .andExpect(status().isForbidden());
  }

  @Test
  void mapsTheConfiguredRolesClaimToSpringAuthorities() {
    Instant issuedAt = Instant.parse("2026-09-07T12:00:00Z");
    Jwt jwt =
        Jwt.withTokenValue("synthetic-test-token")
            .header("alg", "RS256")
            .subject("synthetic-reviewer")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .claim("roles", List.of("REVIEWER", "ADMIN"))
            .build();

    assertThat(jwtAuthenticationConverter.convert(jwt).getAuthorities())
        .extracting("authority")
        .contains("ROLE_REVIEWER", "ROLE_ADMIN", "FACTOR_BEARER");
  }

  private String createClaim(String externalReference, RequestPostProcessor authentication)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/claims")
                    .with(authentication)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest(externalReference)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private RequestPostProcessor role(String role) {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private String validRequest(String externalReference) {
    return """
        {
          "externalReference": "%s",
          "policyNumber": "POL-AUTO-1001",
          "claimantName": "Synthetic Security Claimant",
          "claimType": "AUTO",
          "incidentDate": "2026-01-10",
          "description": "Synthetic security authorization test claim",
          "estimatedLoss": 1250.00
        }
        """
        .formatted(externalReference);
  }
}
