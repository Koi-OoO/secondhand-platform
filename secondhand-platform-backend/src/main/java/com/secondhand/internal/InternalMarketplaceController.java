package com.secondhand.internal;

import com.secondhand.service.ProductService;
import com.secondhand.util.Result;
import java.math.BigDecimal;
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
        if (page == null || page < 1 || size == null || size < 1 || size > 20
                || (maxPrice != null && maxPrice.signum() < 0)) {
            return Result.error("查询参数不合法");
        }
        return productService.pageProductsForAi(page, size, categoryId, keyword,
                minPrice, maxPrice, conditionLevel, sort);
    }

    /** 查询单个商品详情。 */
    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) {
        if (id == null || id < 1) {
            return Result.error("商品ID不合法");
        }
        return productService.getDetailForAi(id);
    }
}
