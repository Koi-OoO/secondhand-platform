package com.secondhand.controller;

import com.secondhand.search.ProductSearchCriteria;
import com.secondhand.search.ProductSearchService;
import com.secondhand.search.ProductSearchUnavailableException;
import com.secondhand.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 独立只读搜索入口；旧商品广场接口继续工作，ES 关闭时明确返回 503。 */
@RestController
@Tag(name = "商品全文搜索")
public class ProductSearchController {
    private final ObjectProvider<ProductSearchService> service;

    public ProductSearchController(ObjectProvider<ProductSearchService> service) {
        this.service = service;
    }

    @GetMapping("/product/search")
    @Operation(summary = "ES 商品全文搜索", description = "公开接口，金额单位为元，默认每页20条；total为索引近实时总数")
    public ResponseEntity<Result> search(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Integer conditionLevel,
            @RequestParam(defaultValue = "relevance") String sort) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(page, size, keyword, categoryId,
                minPrice, maxPrice, conditionLevel, sort);
        ProductSearchService available = service.getIfAvailable();
        if (available == null) return ResponseEntity.status(503).body(Result.error("商品全文搜索尚未启用"));
        return ResponseEntity.ok(Result.success(available.search(criteria)));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result> invalidQuery(Exception ex) {
        String message = ex instanceof MethodArgumentTypeMismatchException ? "搜索参数格式不正确" : ex.getMessage();
        return ResponseEntity.badRequest().body(Result.error(message));
    }

    @ExceptionHandler(ProductSearchUnavailableException.class)
    public ResponseEntity<Result> unavailable(ProductSearchUnavailableException ex) {
        return ResponseEntity.status(503).body(Result.error(ex.getMessage()));
    }
}
