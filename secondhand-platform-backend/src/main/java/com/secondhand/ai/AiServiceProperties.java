package com.secondhand.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 服务配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiServiceProperties {
    /** Python AI 服务的基础地址。 */
    private String baseUrl = "http://localhost:8000";

    /** Java 内部查询接口使用的服务令牌。 */
    private String internalToken;

    /** HTTP 连接超时，单位毫秒。 */
    private int connectTimeoutMs = 1000;

    /** HTTP 读取超时，单位毫秒。 */
    private int readTimeoutMs = 30000;
}
