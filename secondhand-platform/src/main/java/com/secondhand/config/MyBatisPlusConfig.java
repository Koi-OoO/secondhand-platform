package com.secondhand.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus配置类
 */
@Configuration  // 标识为配置类
public class MyBatisPlusConfig {

    /**
     * 注册MyBatis-Plus拦截器，启用分页功能
     */
    @Bean  // 声明为Spring Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建MyBatis-Plus拦截器对象
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加MySQL分页插件（指定数据库类型为MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 返回配置好的拦截器
        return interceptor;
    }
}