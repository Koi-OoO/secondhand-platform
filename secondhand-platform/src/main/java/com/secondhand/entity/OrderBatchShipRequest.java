package com.secondhand.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "批量发货请求")
public class OrderBatchShipRequest {

    @Schema(description = "发货项列表", required = true)
    private List<OrderBatchShipItem> items;
}
