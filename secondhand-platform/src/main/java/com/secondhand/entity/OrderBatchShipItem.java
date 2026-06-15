package com.secondhand.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单条批量发货项")
public class OrderBatchShipItem {

    @Schema(description = "订单 ID", required = true)
    private Long orderId;

    @Schema(description = "快递单号", required = true)
    private String expressNo;
}
