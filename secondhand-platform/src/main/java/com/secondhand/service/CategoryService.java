package com.secondhand.service;

import com.secondhand.util.Result;

/**
 * 分类服务接口
 * 定义分类模块对外提供的查询能力，
 * 主要用于读取商品分类树结构。
 */
public interface CategoryService {

    /**
     * 查询分类树
     *
     * @return Result 带有 children 结构的分类树结果
     */
    Result listAll();
}
