package com.secondhand.service;

import com.secondhand.util.Result;

/**
 * 通知服务接口。
 * 对内提供「产生通知并实时推送」能力，对外提供查询/已读等 REST 能力。
 */
public interface NotificationService {

    /**
     * 创建一条通知并实时推送给接收者（在线才推，离线仅落库）。
     * 该方法为业务事件的附属操作，内部自行兜底异常，不会影响调用方的主流程。
     *
     * @param userId    接收者用户 ID
     * @param type      通知类型：order/favorite/system
     * @param title     标题
     * @param content   内容
     * @param relatedId 关联业务 ID（如订单 ID），可空
     */
    void notifyUser(Long userId, String type, String title, String content, Long relatedId);

    /**
     * 分页查询我的通知（按时间倒序）。
     */
    Result getMyNotifications(Long userId, Integer page, Integer size);

    /**
     * 查询我的未读通知数。
     */
    Result getUnreadCount(Long userId);

    /**
     * 标记单条通知为已读（仅限本人的通知）。
     */
    Result markRead(Long userId, Long id);

    /**
     * 把我的全部未读通知标记为已读。
     */
    Result markAllRead(Long userId);
}
