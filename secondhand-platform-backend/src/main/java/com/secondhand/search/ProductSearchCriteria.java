package com.secondhand.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** 不可变搜索条件；外部金额仍用元，构造时校验，查询时换算为分。 */
public record ProductSearchCriteria(int page, int size, String keyword, Integer categoryId,
        BigDecimal minPrice, BigDecimal maxPrice, Integer conditionLevel, String sort) {

    public ProductSearchCriteria {
        keyword = keyword == null ? "" : keyword.trim();
        sort = sort == null || sort.isBlank() ? "relevance" : sort;
        if (page < 1 || size < 1 || size > 50 || (long) page * size > 10000) {
            throw new IllegalArgumentException("页码需从1开始，每页1至50条，查询范围不能超过10000条");
        }
        if (keyword.length() > 200) throw new IllegalArgumentException("搜索关键词最多200个字符");
        if (categoryId != null && categoryId <= 0) throw new IllegalArgumentException("分类 ID 必须大于0");
        if (conditionLevel != null && (conditionLevel < 1 || conditionLevel > 4)) {
            throw new IllegalArgumentException("成色等级必须为1至4");
        }
        if (!Set.of("relevance", "latest", "hottest", "price_asc", "price_desc").contains(sort)) {
            throw new IllegalArgumentException("不支持的搜索排序方式");
        }
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new IllegalArgumentException("价格不能为负数，最低价不能高于最高价");
        }
        toCents(minPrice, RoundingMode.CEILING);
        toCents(maxPrice, RoundingMode.FLOOR);
    }

    public int offset() { return (page - 1) * size; }
    public Long minCents() { return toCents(minPrice, RoundingMode.CEILING); }
    public Long maxCents() { return toCents(maxPrice, RoundingMode.FLOOR); }

    private static Long toCents(BigDecimal price, RoundingMode rounding) {
        if (price == null) return null;
        try {
            return price.movePointRight(2).setScale(0, rounding).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("价格超出支持范围");
        }
    }
}
