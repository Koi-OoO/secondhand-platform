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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Autowired;
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
        this(properties.getInternalToken());
        if ((environment.acceptsProfiles("prod", "production"))
                && "dev-ai-internal-token".equals(this.internalToken)) {
            throw new IllegalStateException("生产环境必须配置 AI_INTERNAL_TOKEN，不能使用默认内部服务令牌");
        }
    }

    public InternalServiceAuthFilter(String internalToken) {
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!httpRequest.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        String provided = httpRequest.getHeader(TOKEN_HEADER);
        boolean valid = provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), internalToken.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json; charset=utf-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"未授权\",\"data\":null}");
            return;
        }
        httpRequest.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        chain.doFilter(request, response);
    }
}
