package com.secondhand.internal;

import com.secondhand.interceptor.AuthInterceptor;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceAuthFilterTest {

    @Test
    void rejectsMissingTokenForInternalPath() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/marketplace/listings/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsWrongTokenForInternalPath() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/marketplace/listings/search");
        request.addHeader("X-Internal-Service-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsNonInternalPathWithoutToken() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/product/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void springConstructorRejectsDefaultTokenInProduction() {
        com.secondhand.ai.AiServiceProperties properties = new com.secondhand.ai.AiServiceProperties();
        properties.setInternalToken("dev-ai-internal-token");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new InternalServiceAuthFilter(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认内部服务令牌");
    }

    @Test
    void springConstructorAcceptsConfiguredTokenInProduction() {
        com.secondhand.ai.AiServiceProperties properties = new com.secondhand.ai.AiServiceProperties();
        properties.setInternalToken("configured-secret");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertThat(new InternalServiceAuthFilter(properties, environment)).isNotNull();
    }

    @Test
    void acceptsCorrectTokenAndMarksRequest() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/marketplace/listings/search");
        request.addHeader("X-Internal-Service-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("internalServiceAuthenticated")).isEqualTo(true);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void authInterceptorAllowsAuthenticatedInternalRequest() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/marketplace/listings/search");
        request.setAttribute("internalServiceAuthenticated", true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        interceptor.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void authInterceptorDoesNotTrustAttributeOnPublicPath() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/product/private");
        request.setAttribute("internalServiceAuthenticated", true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        interceptor.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
