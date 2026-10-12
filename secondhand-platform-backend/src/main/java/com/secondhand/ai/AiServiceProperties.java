package com.secondhand.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 服务配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiServiceProperties {
    /** Java 内部查询接口使用的服务令牌。 */
    private String internalToken;
}
