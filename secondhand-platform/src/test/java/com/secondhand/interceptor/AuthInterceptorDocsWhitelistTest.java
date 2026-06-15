package com.secondhand.interceptor;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInterceptorDocsWhitelistTest {

    @SuppressWarnings("unchecked")
    @Test
    void docsWhitelistKeepsOnlyKnife4jRequiredPublicDocEndpoints() throws Exception {
        Field field = AuthInterceptor.class.getDeclaredField("EXCLUDE");
        field.setAccessible(true);

        List<String> excludes = (List<String>) field.get(null);

        assertThat(excludes).contains("/doc.html", "/v3/api-docs", "/webjars/");
        assertThat(excludes).doesNotContain("/swagger-ui/", "/swagger-resources");
    }
}
