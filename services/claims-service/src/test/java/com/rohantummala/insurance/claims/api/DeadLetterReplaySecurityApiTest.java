package com.rohantummala.insurance.claims.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rohantummala.insurance.claims.infrastructure.messaging.DeadLetterReplayEndpoint;
import com.rohantummala.insurance.claims.infrastructure.messaging.DeadLetterReplayResult;
import com.rohantummala.insurance.claims.infrastructure.messaging.RabbitDeadLetterReplayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = "management.endpoints.web.exposure.include=health,info,deadLetterReplay")
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
@Import(DeadLetterReplaySecurityApiTest.ReplayTestConfiguration.class)
class DeadLetterReplaySecurityApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RabbitDeadLetterReplayService replayService;

  @Test
  void rejectsAnonymousReplayWithoutCallingTheService() throws Exception {
    mockMvc
        .perform(
            post("/actuator/deadLetterReplay")
                .header(CorrelationIdFilter.HEADER_NAME, "replay-anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":10}"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.type").value("urn:problem:authentication-required"))
        .andExpect(jsonPath("$.correlationId").value("replay-anonymous"));
    verifyNoInteractions(replayService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AGENT", "REVIEWER", "UNASSIGNED"})
  void rejectsNonAdministrativeReplayWithoutCallingTheService(String role) throws Exception {
    mockMvc
        .perform(
            post("/actuator/deadLetterReplay")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .header(CorrelationIdFilter.HEADER_NAME, "replay-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":10}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:problem:access-denied"))
        .andExpect(jsonPath("$.correlationId").value("replay-forbidden"));
    verifyNoInteractions(replayService);
  }

  @Test
  void allowsAdministratorsToInvokeTheRealActuatorWriteOperation() throws Exception {
    when(replayService.replay(10)).thenReturn(new DeadLetterReplayResult(10, 2));
    mockMvc
        .perform(
            post("/actuator/deadLetterReplay")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":10}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(10))
        .andExpect(jsonPath("$.replayed").value(2));
    verify(replayService).replay(10);
  }

  @Test
  void doesNotAllowReadAccessToReplayEvenForAdministrators() throws Exception {
    mockMvc
        .perform(
            get("/actuator/deadLetterReplay")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(replayService);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ReplayTestConfiguration {
    // The in-memory profile intentionally excludes broker components. Register the actual
    // Actuator endpoint explicitly so these tests exercise HTTP security without a broker.
    @Bean
    DeadLetterReplayEndpoint deadLetterReplayEndpoint(RabbitDeadLetterReplayService replayService) {
      return new DeadLetterReplayEndpoint(replayService);
    }
  }
}
