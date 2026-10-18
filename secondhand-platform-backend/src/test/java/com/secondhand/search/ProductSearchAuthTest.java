package com.secondhand.search;

import com.secondhand.interceptor.AuthInterceptor;
import javax.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductSearchAuthTest {
    @Test
    void anonymousGetSearchIsPublicWithoutOpeningWriteOrManagementPaths() throws Exception {
        AuthInterceptor filter = new AuthInterceptor();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/product/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(read, response, chain);
        verify(chain).doFilter(read, response);
        for (String path : new String[]{"/product/search", "/product/search/reindex"}) {
            MockHttpServletRequest write = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse denied = new MockHttpServletResponse();
            filter.doFilter(write, denied, chain);
            assertThat(denied.getStatus()).isEqualTo(401);
        }
        verifyNoMoreInteractions(chain);
    }
}
