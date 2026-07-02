package com.secondhand.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDependencyConfigTest {

    @Test
    void pomKeepsKnife4jUiDependencies() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);

        assertThat(pom).contains("<artifactId>springdoc-openapi-ui</artifactId>");
        assertThat(pom).contains("<artifactId>knife4j-openapi3-ui</artifactId>");
    }
}
