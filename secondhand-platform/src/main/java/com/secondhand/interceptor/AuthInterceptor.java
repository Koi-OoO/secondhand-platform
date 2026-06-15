package com.secondhand.interceptor;

import com.secondhand.util.SessionUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 登录校验过滤器（拦截请求，验证用户是否已登录）
 */
@Component  // 将过滤器注册为Spring Bean
@Order(Ordered.LOWEST_PRECEDENCE)  // 设置过滤器执行顺序（低优先级，让其他过滤器先执行）
public class AuthInterceptor implements Filter {

    /**
     * 白名单路径（不需要登录即可访问的接口）
     */
    private static final List<String> EXCLUDE = Arrays.asList(
            "/user/login", "/user/register", "/user/public/", "/category/list",  // 用户登录注册、分类
            "/product/page", "/product/detail/", "/product/hot-keywords",  // 商品查询
            "/product/seller/", "/evaluation/user/",  // 卖家商品、评价查询
            "/uploads/", "/error", "/doc.html",  // 静态资源、错误页、Swagger文档
            "/v3/api-docs", "/webjars/"  // Swagger相关资源
    );

    /**
     * 核心拦截方法
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // 转换为HttpServletRequest和HttpServletResponse
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        // 预检请求（OPTIONS）直接放行，解决跨域问题
        if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            chain.doFilter(req, res);  // 放行
            return;
        }

        // 获取请求的URI
        String uri = httpReq.getRequestURI();
        // 遍历白名单
        for (String prefix : EXCLUDE) {
            // 如果当前请求匹配白名单前缀，直接放行
            if (uri.startsWith(prefix)) {
                chain.doFilter(req, res);  // 放行
                return;
            }
        }

        // 非白名单接口：需要验证登录
        HttpSession session = httpReq.getSession(false);  // 获取session，不自动创建
        // 检查session是否存在且用户已登录
        if (session == null || !SessionUtil.isLoggedIn(session)) {
            // 未登录：设置401状态码
            httpRes.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // 设置响应格式为JSON
            httpRes.setContentType("application/json; charset=utf-8");
            // 返回未登录的JSON响应
            httpRes.getWriter().write("{\"code\":500,\"message\":\"未登录\",\"data\":null}");
            return;
        }

        // 已登录，放行请求
        chain.doFilter(req, res);
    }

    /**
     * 过滤器初始化（无需额外逻辑）
     */
    @Override
    public void init(FilterConfig cfg) {
        // 空实现
    }

    /**
     * 过滤器销毁（无需额外逻辑）
     */
    @Override
    public void destroy() {
        // 空实现
    }
}