package com.secondhand.internal;

import com.secondhand.ai.AiServiceProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 校验 AI 等内部服务访问令牌。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalServiceAuthFilter implements Filter {
    private static final String TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String AUTHENTICATED_ATTRIBUTE = "internalServiceAuthenticated";
    private final String internalToken;

    @Autowired
    public InternalServiceAuthFilter(AiServiceProperties properties, Environment environment) {
        // 1. 读取配置的内部令牌，null按空串处理
        String configuredToken = properties.getInternalToken();
        this.internalToken = configuredToken == null ? "" : configuredToken;

        // 2. 生产环境禁止空令牌或默认开发令牌
        if (environment.acceptsProfiles("prod", "production")
                && (this.internalToken.isBlank()
                || "dev-ai-internal-token".equals(this.internalToken))) {
            throw new IllegalStateException("生产环境必须配置 AI_INTERNAL_TOKEN，不能使用默认内部服务令牌");
        }
    }

    /** 单元测试使用的固定令牌构造器。 */
    public InternalServiceAuthFilter(String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("内部服务令牌不能为空");
        }
        this.internalToken = internalToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 1. 非内部接口直接放行，不影响普通业务请求
        if (!httpRequest.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 使用恒定时间比较，避免通过响应时间推测令牌
        String providedToken = httpRequest.getHeader(TOKEN_HEADER);
        boolean tokenValid = providedToken != null && MessageDigest.isEqual(
                providedToken.getBytes(StandardCharsets.UTF_8), internalToken.getBytes(StandardCharsets.UTF_8));

        // 3. 令牌不匹配时返回401，不进入内部接口
        if (!tokenValid) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json; charset=utf-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"未授权\",\"data\":null}");
            return;
        }

        // 4. 标记本次请求已认证，供后续拦截器判断
        httpRequest.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        chain.doFilter(request, response);
    }
}
