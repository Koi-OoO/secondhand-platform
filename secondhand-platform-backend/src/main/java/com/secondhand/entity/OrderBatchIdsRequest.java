package com.secondhand.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "批量订单 ID 请求")
public class OrderBatchIdsRequest {

    @Schema(description = "订单 ID 列表", required = true)
    private List<Long> orderIds;
}
