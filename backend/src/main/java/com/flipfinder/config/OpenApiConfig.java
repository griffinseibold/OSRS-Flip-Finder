package com.flipfinder.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Old School RuneScape Flip Finder API",
        version = "v1",
        description = "Paginated access to Grand Exchange item data."))
public class OpenApiConfig {
}
