package com.secondhand.service;

import com.secondhand.util.Result;

/**
 * 评价服务接口
 * 定义评价模块的核心业务能力，
 * 包括订单评价提交和用户收到评价分页查询。
 */
public interface EvaluationService {

    /**
     * 提交订单评价
     *
     * @param fromUserId 评价人用户 ID
     * @param orderId 订单 ID
     * @param rating 评分
     * @param content 评价内容
     * @param anonymous 是否匿名
     * @return Result 评价结果
     */
    Result evaluate(Long fromUserId, Long orderId, Integer rating, String content, Boolean anonymous);

    /**
     * 分页查询某个用户收到的评价列表
     *
     * @param userId 被评价用户 ID
     * @param page 页码
     * @param size 每页条数
     * @return Result 评价分页结果
     */
    Result getUserEvaluations(Long userId, Integer page, Integer size);
}
