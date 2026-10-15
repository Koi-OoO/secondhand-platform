package com.secondhand.ai.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 查询助手对外展示的只读商品卡片。 */
@Data
@NoArgsConstructor
public class AiProductCard {
    private Long productId;
    private String title;
    private BigDecimal price;
    private Integer stock;
    private Integer conditionLevel;
    private String tradeLocation;
    private String detailPath;
    /** 真实商品封面地址；旧版本响应或缺少图片时允许为空。 */
    private String coverUrl;
}
