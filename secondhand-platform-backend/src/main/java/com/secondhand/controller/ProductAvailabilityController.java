package com.secondhand.controller;

import com.secondhand.mapper.ProductMapper;
import com.secondhand.entity.Product;
import com.secondhand.util.Result;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

/** 只返回公开状态，直接读 MySQL，不经过详情缓存，不计浏览量。 */
@RestController
public class ProductAvailabilityController {
    private final ProductMapper products;
    public ProductAvailabilityController(ProductMapper products) { this.products=products; }
    public record Availability(Long id, int stock, int status) { }
    @GetMapping("/product/availability")
    public Result get(@RequestParam List<Long> ids) {
        if(ids.isEmpty() || ids.size()>50 || ids.stream().anyMatch(id -> id==null || id<=0)) return Result.error("请选择1至50个有效商品");
        Map<Long,Product> current=products.selectBatchIds(new LinkedHashSet<>(ids)).stream()
                .collect(Collectors.toMap(Product::getId,p -> p));
        List<Availability> result=new ArrayList<>();
        for(Long id:new LinkedHashSet<>(ids)) {
            Product p=current.get(id);
            boolean missing=p==null || Integer.valueOf(4).equals(p.getStatus());
            result.add(new Availability(id,missing ? 0 : Math.max(0,p.getStock()==null ? 0 : p.getStock()),missing ? 4 : (p.getStatus()==null ? 2 : p.getStatus())));
        }
        return Result.success(result);
    }
}
