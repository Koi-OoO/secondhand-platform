package com.secondhand.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 负责初始化项目中使用的 Redis 基础设施，
 * 包括统一的 RedisTemplate 序列化方案以及 Spring Cache 缓存策略。
 */
@Configuration  // 标记这是一个Spring配置类，会被Spring容器扫描并加载
@EnableCaching  // 启用Spring缓存管理功能，开启基于注解的缓存（如@Cacheable、@CacheEvict等）
public class RedisConfig {

    /**
     * 创建 RedisTemplate
     *
     * 业务逻辑：
     * 1. 使用字符串序列化 key，便于排查 Redis 中的数据
     * 2. 使用 JSON 序列化 value，支持对象结构化存储
     * 3. 注册时间模块和类型信息，保证复杂对象能正确序列化与反序列化
     *
     * @param factory Redis 连接工厂（由Spring Boot自动配置提供）
     * @return RedisTemplate<String, Object> Redis 操作模板
     */
    @Bean  // 将该方法的返回值注册为Spring容器中的一个Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // 1. 创建RedisTemplate实例并设置连接工厂
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 2. 配置Jackson对象映射器（用于JSON序列化和反序列化）
        ObjectMapper objectMapper = new ObjectMapper();

        // 2.1 注册Java时间模块支持（处理LocalDateTime、LocalDate等Java 8时间类型）
        objectMapper.registerModule(new JavaTimeModule());

        // 2.2 激活默认类型信息存储（解决JSON反序列化时的类型擦除问题）
        // 对于非final类型的属性，会在JSON中增加@class字段记录原始类型
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,  // 宽松的子类型验证器（允许任意类型）
                ObjectMapper.DefaultTyping.NON_FINAL,    // 仅为非final类型添加类型信息
                JsonTypeInfo.As.PROPERTY                 // 类型信息作为JSON属性存储
        );

        // 3. 创建序列化器
        // JSON序列化器：用于将对象序列化为JSON字符串，并从中反序列化
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        // 字符串序列化器：用于将字符串直接存储（不额外添加引号或转义）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 4. 配置RedisTemplate的序列化策略
        // Key和HashKey使用字符串序列化（便于Redis客户端查看和调试）
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // Value和HashValue使用JSON序列化（支持复杂对象结构）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 5. 初始化模板（应用上述配置）
        template.afterPropertiesSet();

        return template;
    }

    /**
     * 创建缓存管理器
     *
     * 业务逻辑：
     * 1. 配置 Spring Cache 默认缓存策略
     * 2. 默认缓存 5 分钟
     * 3. 对分类树这类变更频率低的数据单独延长缓存时长到 30 分钟
     *
     * @param factory Redis 连接工厂
     * @return CacheManager 缓存管理器实例
     */
    @Bean  // 将该方法的返回值注册为Spring容器中的一个Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // 1. 创建默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 配置Key使用字符串序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // 配置Value使用JSON序列化
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                // 设置默认过期时间为5分钟
                .entryTtl(Duration.ofMinutes(5))
                // 禁止缓存null值（避免缓存穿透）
                .disableCachingNullValues();

        // 2. 为特定缓存名称配置独立的策略（覆盖默认配置）
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // category缓存：过期时间改为30分钟（因为分类树数据变更频率低）
        cacheConfigs.put("category", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 3. 构建并返回缓存管理器
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)                     // 设置默认配置
                .withInitialCacheConfigurations(cacheConfigs)     // 设置特定缓存的配置
                .build();
    }
}
