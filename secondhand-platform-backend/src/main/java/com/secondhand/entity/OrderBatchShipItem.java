package com.secondhand.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单条批量面交交付项（保留原DTO名称兼容旧接口）")
public class OrderBatchShipItem {

    @Schema(description = "订单 ID", required = true)
    private Long orderId;

    @Schema(description = "旧接口兼容字段，面交不使用")
    private String expressNo;
}
