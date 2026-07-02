package com.secondhand.config;

import com.secondhand.util.UploadPathResolver;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类（跨域、静态资源映射）
 */
@Configuration  // 标识为配置类
public class WebMvcConfig implements WebMvcConfigurer {

    // 本地存储路径，从配置文件读取，默认uploads
    @Value("${app.upload.local-path:uploads}")
    private String localStoragePath;

    /**
     * 配置跨域规则（解决前后端分离跨域问题）
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // 对所有路径生效
                .allowedOriginPatterns("*")  // 允许所有来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的HTTP方法
                .allowedHeaders("*")  // 允许所有请求头
                .allowCredentials(true)  // 允许携带cookie
                .maxAge(3600);  // 预检请求缓存时间（秒）
    }

    /**
     * 配置静态资源映射（将上传目录映射为可访问的URL）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 解析本地存储路径为绝对路径
        Path resolvedLocalPath = UploadPathResolver.resolve(localStoragePath);
        registry.addResourceHandler("/uploads/**")  // URL访问路径
                .addResourceLocations(toResourceLocation(resolvedLocalPath))  // 映射到本地目录
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic().mustRevalidate());  // 设置浏览器缓存1天
    }

    /**
     * 将文件路径转换为Spring资源位置字符串
     */
    private String toResourceLocation(Path path) {
        // 转换为URI字符串格式（如 file:/绝对路径/）
        String location = path.toUri().toString();
        // 确保以斜杠结尾，符合Spring资源路径规范
        return location.endsWith("/") ? location : location + "/";
    }
}