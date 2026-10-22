package com.secondhand.ai.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 商品搜索工具实际使用的条件，供前端继续浏览匹配商品。 */
@Data
@NoArgsConstructor
public class AiSearchFilters {
    /** 搜索关键词，未指定时为空。 */
    private String keyword;
    /** 商品分类 ID，未指定时为空。 */
    private Integer categoryId;
    /** 最低价格，允许为零，未指定时为空。 */
    private BigDecimal minPrice;
    /** 最高价格，允许为零，未指定时为空。 */
    private BigDecimal maxPrice;
    /** 商品成色等级，未指定时为空。 */
    private Integer conditionLevel;
    /** 工具执行时使用的排序方式。 */
    private String sort;
}
