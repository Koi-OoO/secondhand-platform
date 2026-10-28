package com.secondhand.internal;

import com.secondhand.search.ProductSearchUnavailableException;
import com.secondhand.service.ProductService;
import com.secondhand.util.Result;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 面向内部 AI 服务的只读商品查询接口。 */
@RestController
@RequestMapping("/internal/marketplace/listings")
public class InternalMarketplaceController {
    private final ProductService productService;

    public InternalMarketplaceController(ProductService productService) {
        this.productService = productService;
    }

    /** 查询商品列表。 */
    @GetMapping("/search")
    public Result search(@RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer size,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Integer categoryId,
                         @RequestParam(required = false) BigDecimal minPrice,
                         @RequestParam(required = false) BigDecimal maxPrice,
                         @RequestParam(required = false) Integer conditionLevel,
                         @RequestParam(defaultValue = "latest") String sort) {
        // 1. 校验分页、分类、成色和价格区间
        if (page == null || page < 1
                || size == null || size < 1 || size > 20
                || (categoryId != null && categoryId < 1)
                || (conditionLevel != null && (conditionLevel < 1 || conditionLevel > 4))
                || (minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && maxPrice.compareTo(minPrice) < 0)) {
            return Result.error("查询参数不合法");
        }

        // 2. 调用商品服务，执行 AI 专用商品查询
        return productService.pageProductsForAi(page, size, categoryId, keyword,
                minPrice, maxPrice, conditionLevel, sort);
    }

    /** 查询单个商品详情。 */
    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) {
        // 1. 校验商品ID
        if (id == null || id < 1) {
            return Result.error("商品ID不合法");
        }

        // 2. 调用商品服务，查询 AI 可用的商品详情
        return productService.getDetailForAi(id);
    }

    /** 搜索失败保留明确状态，Python 据此提示用户重试，不改用旧 MySQL 搜索。 */
    @ExceptionHandler(ProductSearchUnavailableException.class)
    public ResponseEntity<Result> unavailable(ProductSearchUnavailableException exception) {
        return ResponseEntity.status(503).body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> invalidSearch(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Result.error(exception.getMessage()));
    }
}
