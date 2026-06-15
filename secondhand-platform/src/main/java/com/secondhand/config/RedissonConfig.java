package com.secondhand.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson客户端配置类（用于分布式锁）
 */
@Configuration  // 标识为配置类
public class RedissonConfig {

    // Redis主机地址，从配置文件读取
    @Value("${spring.redis.host}")
    private String redisHost;

    // Redis端口号，从配置文件读取
    @Value("${spring.redis.port}")
    private int redisPort;

    // Redis数据库索引，从配置文件读取，默认0
    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * 创建Redisson客户端
     */
    @Bean(destroyMethod = "shutdown")  // 声明为Bean，销毁时调用shutdown方法关闭连接
    public RedissonClient redissonClient() {
        // 创建Redisson配置对象
        Config config = new Config();
        // 配置单机模式
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)  // 设置Redis地址
                .setDatabase(database)  // 设置数据库索引
                .setConnectionPoolSize(64)  // 连接池大小
                .setConnectionMinimumIdleSize(16)  // 最小空闲连接数
                .setRetryAttempts(3)  // 重试次数
                .setRetryInterval(500);  // 重试间隔（毫秒）
        // 创建并返回Redisson客户端
        return Redisson.create(config);
    }
}