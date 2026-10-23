package com.secondhand.service.impl;

import com.secondhand.entity.Category;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.service.CategoryService;
import com.secondhand.util.Result;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 分类服务实现类
 */
@Service  // 标识为Service层组件，被Spring管理
public class CategoryServiceImpl implements CategoryService {

    @Autowired  // 自动注入CategoryMapper
    private CategoryMapper categoryMapper;

    /**
     * 查询分类树（带缓存）
     */
    @Override
    @Cacheable(value = "category", key = "'tree'", unless = "#result.data == null",
            condition = "@environment.getProperty('app.cache.category.enabled', T(java.lang.Boolean), true)")
    // 默认启用缓存；对照压测时关闭开关，会同时绕过缓存读取和写入，保留相同数据库查询逻辑。
    public Result listAll() {
        // 查询所有顶级分类（parent_id为0）
        List<Category> topCategories = categoryMapper.selectTopCategories();

        // 遍历每个顶级分类
        for (Category top : topCategories) {
            // 查询该顶级分类下的子分类
            List<Category> children = categoryMapper.selectByParentId(top.getId());
            // 设置子分类列表（如果为空则设为null）
            top.setChildren(children.isEmpty() ? null : children);
        }
        // 返回成功响应，包含分类树数据
        return Result.success(topCategories);
    }
}
