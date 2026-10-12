package com.secondhand.internal;

import com.secondhand.interceptor.AuthInterceptor;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
}
