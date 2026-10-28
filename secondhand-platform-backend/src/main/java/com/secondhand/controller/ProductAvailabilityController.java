package com.secondhand.controller;

import com.secondhand.mapper.ProductMapper;
import com.secondhand.entity.Product;
import com.secondhand.util.Result;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 只返回公开状态，直接读 MySQL，不经过详情缓存，不计浏览量。 */
@RestController
public class ProductAvailabilityController {
    private final ProductMapper productMapper;

    public ProductAvailabilityController(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /** 仅返回商品ID、库存和状态，不携带用户或订单信息。 */
    public record Availability(Long id, int stock, int status) {
    }

    @GetMapping("/product/availability")
    public Result get(@RequestParam List<Long> ids) {
        // 1. 校验商品ID，每次最多查询50个
        if (ids.isEmpty() || ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            return Result.error("请选择1至50个有效商品");
        }

        // 2. 按首次出现的顺序去重，一次查询最新商品信息
        Set<Long> productIds = new LinkedHashSet<>(ids);
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        // 3. 按请求顺序组装公开状态，不存在或已删除的商品统一标记为不可用
        List<Availability> result = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product == null || Integer.valueOf(4).equals(product.getStatus())) {
                result.add(new Availability(productId, 0, 4));
                continue;
            }

            // 空库存和负库存按0返回；缺少状态时沿用已下架（2）的默认值。
            int stock = product.getStock() == null ? 0 : Math.max(0, product.getStock());
            int status = product.getStatus() == null ? 2 : product.getStatus();
            result.add(new Availability(productId, stock, status));
        }

        // 4. 返回库存和状态，不读取详情缓存，也不增加浏览量
        return Result.success(result);
    }
}
