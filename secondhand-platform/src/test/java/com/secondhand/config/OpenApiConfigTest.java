package com.secondhand.config;

import io.swagger.v3.oas.models.OpenAPI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void customOpenApiDoesNotExposeLegacySecurityScheme() {
        OpenAPI openAPI = new OpenApiConfig().customOpenAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("二手交易平台接口文档");
        assertThat(openAPI.getInfo().getDescription()).contains("二手交易平台").contains("Spring Session");
        assertThat(openAPI.getSecurity()).isNullOrEmpty();
        if (openAPI.getComponents() != null) {
            assertThat(openAPI.getComponents().getSecuritySchemes()).doesNotContainKey("J" + "WT");
        }
    }

    @Test
    void applicationConfigKeepsKnife4jApiDocsWithoutDisablingSwaggerSupport() throws Exception {
        String yaml = Files.readString(
                Path.of("src", "main", "resources", "application.yml"),
                StandardCharsets.UTF_8);

        assertThat(yaml).contains("path: /v3/api-docs");
        assertThat(yaml).doesNotContain("enabled: false");
    }
}
