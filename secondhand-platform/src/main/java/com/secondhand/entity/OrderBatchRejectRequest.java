package com.secondhand.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "批量拒绝发货请求")
public class OrderBatchRejectRequest {

    @Schema(description = "订单 ID 列表", required = true)
    private List<Long> orderIds;

    @Schema(description = "统一拒绝原因")
    private String reason;
}
