package com.secondhand.ai.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 查询助手对外展示的只读商品卡片。 */
@Data
@NoArgsConstructor
public class AiProductCard {
    /** 商品ID。 */
    private Long productId;

    /** 商品标题。 */
    private String title;

    /** 商品价格，单位元。 */
    private BigDecimal price;

    /** 当前库存。 */
    private Integer stock;

    /** 成色等级。 */
    private Integer conditionLevel;

    /** 交易地点。 */
    private String tradeLocation;

    /** 前端商品详情页路径。 */
    private String detailPath;

    /** 真实商品封面地址；旧版本响应或缺少图片时允许为空。 */
    private String coverUrl;
}
