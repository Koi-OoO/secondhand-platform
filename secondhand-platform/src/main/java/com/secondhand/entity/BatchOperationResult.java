package com.secondhand.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 批量操作结果实体类
 */
@Data  // Lombok注解，自动生成getter/setter/toString等方法
@Schema(description = "批量操作结果")  // Swagger文档说明
public class BatchOperationResult {

    @Schema(description = "成功的订单 ID 列表")  // 成功列表的Swagger说明
    private List<Long> successIds = new ArrayList<>();  // 存储成功处理的订单ID列表

    @Schema(description = "失败详情列表")  // 失败列表的Swagger说明
    private List<BatchOperationFailure> failures = new ArrayList<>();  // 存储失败详情列表

    /**
     * 获取成功数量
     */
    @Schema(description = "成功数量")  // Swagger说明
    public int getSuccessCount() {
        // 返回成功列表的大小
        return successIds.size();
    }

    /**
     * 获取失败数量
     */
    @Schema(description = "失败数量")  // Swagger说明
    public int getFailCount() {
        // 返回失败列表的大小
        return failures.size();
    }

    /**
     * 添加一条成功记录
     */
    public void addSuccess(Long orderId) {  // 订单ID参数
        // 将成功的订单ID添加到成功列表
        successIds.add(orderId);
    }

    /**
     * 添加一条失败记录
     */
    public void addFailure(Long orderId, String reason) {  // 订单ID和失败原因
        // 创建失败详情对象并添加到失败列表
        failures.add(new BatchOperationFailure(orderId, reason));
    }

    /**
     * 单条失败详情内部类
     */
    @Data  // Lombok注解
    @Schema(description = "单条失败详情")  // Swagger说明
    public static class BatchOperationFailure {
        @Schema(description = "订单 ID")  // 订单ID字段的Swagger说明
        private Long orderId;  // 失败的订单ID

        @Schema(description = "失败原因")  // 失败原因字段的Swagger说明
        private String reason;  // 失败原因

        /**
         * 构造失败详情
         */
        public BatchOperationFailure(Long orderId, String reason) {  // 构造函数
            this.orderId = orderId;  // 设置订单ID
            this.reason = reason;  // 设置失败原因
        }
    }
}