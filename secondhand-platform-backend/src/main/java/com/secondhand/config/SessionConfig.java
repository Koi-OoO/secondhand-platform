package com.secondhand.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 将 Servlet Session 托管给 Spring Session，并持久化到 Redis。
 * 这样多台应用实例共享同一个 Redis 时，也能共享登录态。
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 604800)
public class SessionConfig {
}
