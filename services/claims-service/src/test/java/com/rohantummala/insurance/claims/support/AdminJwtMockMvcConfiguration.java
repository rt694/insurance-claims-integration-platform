package com.rohantummala.insurance.claims.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@TestConfiguration(proxyBeanMethods = false)
class AdminJwtMockMvcConfiguration {

  @Bean
  MockMvcBuilderCustomizer adminJwtMockMvcBuilderCustomizer() {
    return builder ->
        builder.defaultRequest(
            get("/").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }
}
