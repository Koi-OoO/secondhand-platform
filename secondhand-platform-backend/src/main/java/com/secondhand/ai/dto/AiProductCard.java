package com.secondhand.ai.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Read-only product card exposed by the AI agent. */
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
}
