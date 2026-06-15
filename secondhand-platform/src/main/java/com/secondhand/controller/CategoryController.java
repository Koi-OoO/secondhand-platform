package com.secondhand.controller;

import com.secondhand.service.CategoryService;
import com.secondhand.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分类模块控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/category")  // 请求路径前缀为 /category
@Tag(name = "分类管理", description = "提供商品分类树的只读查询接口")  // Swagger文档标签
public class CategoryController {

    @Autowired  // 自动注入CategoryService
    private CategoryService categoryService;

    /**
     * 查询分类树
     */
    @GetMapping("/list")  // 处理GET请求，路径为 /category/list
    @Operation(summary = "查询分类树", description = "公开接口，返回商品分类树结构")  // Swagger接口说明
    public Result list() {
        // 调用service层查询所有分类树
        return categoryService.listAll();
    }
}