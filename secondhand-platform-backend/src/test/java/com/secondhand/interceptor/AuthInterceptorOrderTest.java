package com.secondhand.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInterceptorOrderTest {

    @Test
    void authFilterRunsAfterSpringSessionFilter() {
        Order order = AnnotationUtils.findAnnotation(AuthInterceptor.class, Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isGreaterThan(SessionRepositoryFilter.DEFAULT_ORDER);
    }
}
