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
        // 1. 组装并校验搜索条件
        ProductSearchCriteria criteria = new ProductSearchCriteria(page, size, keyword, categoryId,
                minPrice, maxPrice, conditionLevel, sort);

        // 2. ES 未启用时明确返回 503，避免误报为空结果
        ProductSearchService availableService = service.getIfAvailable();
        if (availableService == null) {
            return ResponseEntity.status(503).body(Result.error("商品全文搜索尚未启用"));
        }

        // 3. 执行搜索并返回结果
        return ResponseEntity.ok(Result.success(availableService.search(criteria)));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result> invalidQuery(Exception exception) {
        // 参数类型错误统一提示格式问题，业务校验错误沿用具体原因
        String message = exception instanceof MethodArgumentTypeMismatchException
                ? "搜索参数格式不正确" : exception.getMessage();
        return ResponseEntity.badRequest().body(Result.error(message));
    }

    @ExceptionHandler(ProductSearchUnavailableException.class)
    public ResponseEntity<Result> unavailable(ProductSearchUnavailableException exception) {
        return ResponseEntity.status(503).body(Result.error(exception.getMessage()));
    }
}
