package com.secondhand.config;

import com.secondhand.util.FileUploadUtil;
import com.secondhand.util.UploadPathResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件上传配置类
 */
@Configuration  // 标识为配置类，Spring会扫描并加载其中的Bean定义
public class FileUploadConfig {

    // OSS服务地址，从配置文件读取，默认值为空字符串
    @Value("${app.oss.endpoint:}")
    private String endpoint;

    // OSS访问密钥ID，从配置文件读取
    @Value("${app.oss.access-key-id:}")
    private String accessKeyId;

    // OSS访问密钥，从配置文件读取
    @Value("${app.oss.access-key-secret:}")
    private String accessKeySecret;

    // OSS存储桶名称，从配置文件读取
    @Value("${app.oss.bucket-name:}")
    private String bucketName;

    // 本地存储路径，从配置文件读取，默认值为"uploads"
    @Value("${app.upload.local-path:uploads}")
    private String localStoragePath;

    /**
     * 创建文件上传工具实例
     */
    @Bean(initMethod = "init", destroyMethod = "destroy")  // 声明为Spring Bean，指定初始化和销毁方法
    public FileUploadUtil fileUploadUtil() {
        // 解析本地存储路径为绝对路径
        String resolvedLocalStoragePath = UploadPathResolver.resolve(localStoragePath).toString();

        // 检查OSS配置是否完整（四个参数都不为空）
        if (hasText(endpoint) && hasText(accessKeyId) && hasText(accessKeySecret) && hasText(bucketName)) {
            // OSS配置完整，创建支持OSS的上传工具
            return new FileUploadUtil(endpoint, accessKeyId, accessKeySecret, bucketName, resolvedLocalStoragePath);
        }
        // OSS配置不完整，回退到仅本地存储的上传工具
        return new FileUploadUtil(resolvedLocalStoragePath);
    }

    /**
     * 判断字符串是否有效（非空且非空白）
     */
    private boolean hasText(String value) {
        // 不为null，且去除首尾空格后不为空
        return value != null && !value.trim().isEmpty();
    }
}