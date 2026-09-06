package com.rohantummala.insurance.claims.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Insurance Claims API",
            version = "v1",
            description = "Synthetic insurance claim intake and processing API"))
public class OpenApiConfiguration {}
