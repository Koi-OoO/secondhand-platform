package com.secondhand.service;

import com.secondhand.entity.Category;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.service.impl.CategoryServiceImpl;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CategoryCacheSwitchTest {
    @ParameterizedTest
    @ValueSource(strings = {"default", "true", "false"})
    void annotationHonorsSwitchForBothCacheReadsAndWrites(String setting) {
        CategoryMapper mapper = mock(CategoryMapper.class);
        Category parent = new Category();
        parent.setId(1);
        parent.setName("手机数码");
        when(mapper.selectTopCategories()).thenReturn(List.of(parent));
        when(mapper.selectByParentId(1)).thenReturn(List.of());
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(CacheTestConfig.class, CategoryServiceImpl.class)
                .withBean(CategoryMapper.class, () -> mapper);
        if (!"default".equals(setting)) {
            runner = runner.withPropertyValues("app.cache.category.enabled=" + setting);
        }
        runner.run(context -> {
            CategoryService service = context.getBean(CategoryService.class);
            assertThat(service.listAll().getCode()).isEqualTo(200);
            assertThat(service.listAll().getData()).isEqualTo(List.of(parent));
            boolean enabled = !"false".equals(setting);
            verify(mapper, times(enabled ? 1 : 2)).selectTopCategories();
            verify(mapper, times(enabled ? 1 : 2)).selectByParentId(1);
            assertThat(context.getBean(CacheManager.class).getCache("category").get("tree") != null)
                    .isEqualTo(enabled);
        });
    }

    // 使用本地缓存验证 Spring 注解代理及配置表达式，不要求启动 Redis。
    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("category");
        }
    }
}
