package com.secondhand.search;

import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 将数据库商品转换成搜索文档；只读转换，不修改原商品或猜测分类。 */
public class ProductSearchDocumentFactory {

    public Map<String, Object> build(Product product, List<ProductImage> images, Map<Integer, Category> categories) {
        if (product.getId() == null || product.getId() <= 0 || product.getPrice() == null
                || product.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("导入商品需要有效 ID 和正数价格");
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", product.getId());
        doc.put("sellerId", product.getSellerId());
        doc.put("title", product.getTitle());
        doc.put("description", product.getDescription());
        doc.put("categoryId", product.getCategoryId());
        doc.put("categoryPathIds", categoryPath(product.getCategoryId(), categories));
        doc.put("priceCents", cents(product.getPrice()));
        doc.put("originalPriceCents", cents(product.getOriginalPrice()));
        doc.put("stock", product.getStock());
        doc.put("conditionLevel", product.getConditionLevel());
        doc.put("status", product.getStatus());
        doc.put("viewCount", product.getViewCount());
        doc.put("likeCount", product.getLikeCount());
        doc.put("createTime", product.getCreateTime() == null ? null : product.getCreateTime().getTime());
        doc.put("updateTime", product.getUpdateTime() == null ? null : product.getUpdateTime().getTime());
        List<ProductImage> ordered = orderedImages(product.getId(), images);
        doc.put("coverUrl", ordered.isEmpty() ? null : ordered.get(0).getUrl());
        doc.put("tradeLocation", product.getTradeLocation());
        return doc;
    }

    /** 保留旧的大类商品；空分类无路径，断链或循环明确报错。 */
    public List<Integer> categoryPath(Integer categoryId, Map<Integer, Category> categories) {
        List<Integer> path = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Integer current = categoryId;
        while (current != null && current != 0) {
            Category category = categories.get(current);
            if (category == null || !visited.add(current)) {
                throw new IllegalArgumentException("商品分类不存在、父分类缺失或分类路径存在循环");
            }
            path.add(current);
            current = category.getParentId();
        }
        Collections.reverse(path);
        return path;
    }

    /** 以 sort、图片 ID 排序；不改变 mapper 返回的原列表，过滤空地址和其他商品图片。 */
    public List<ProductImage> orderedImages(Long productId, List<ProductImage> images) {
        if (images == null) return List.of();
        return images.stream().filter(Objects::nonNull)
                .filter(image -> Objects.equals(productId, image.getProductId()))
                .filter(image -> image.getUrl() != null && !image.getUrl().isBlank())
                .sorted(Comparator.comparing(ProductImage::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductImage::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private Long cents(BigDecimal yuan) {
        if (yuan == null) return null;
        try {
            return yuan.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("商品价格超出分单位整数范围或包含小于一分的金额", ex);
        }
    }
}
