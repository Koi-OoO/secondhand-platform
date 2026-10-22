package com.secondhand.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 服务配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiServiceProperties {
    /** Base URL of the Python AI service. */
    private String baseUrl = "http://localhost:8000";

    /** Java 内部查询接口使用的服务令牌。 */
    private String internalToken;

    /** HTTP connection timeout in milliseconds. */
    private int connectTimeoutMs = 1000;

    /** HTTP read timeout in milliseconds. */
    private int readTimeoutMs = 30000;
}
