package com.secondhand.service;

import com.secondhand.util.Result;
import java.util.List;

/**
 * 私信服务接口。
 * 读写均走 REST，WebSocket 仅用于把新消息实时推给在线的接收方。
 */
public interface MessageService {

    /**
     * 发送私信：落库后实时推送给接收方（在线才推）。
     *
     * @param fromUserId 发送者用户 id（当前登录用户）
     * @param toUserId   接收者用户 id
     * @param content    消息内容
     * @param productId  关联商品 id，可空
     * @return 发送结果（含落库后的消息）
     */
    Result send(Long fromUserId, Long toUserId, String content, Long productId);

    /**
     * 创建或获取「我」与某人的会话；即使还没有消息，也会持久化到会话列表。
     */
    Result createOrGetConversation(Long userId, Long peerId, Long productId);

    /**
     * 查询「我」的会话列表：每个对方 + 最后一条消息 + 未读数 + 对方昵称/头像。
     */
    Result getConversations(Long userId);

    /**
     * 查询「我」与某人的聊天记录（分页），并把对方发给我的未读消息标记为已读。
     */
    Result getChat(Long userId, Long peerId, Integer page, Integer size);

    /**
     * 查询「我」收到的私信总未读数。
     */
    Result getUnreadCount(Long userId);

    /**
     * 批量查询用户当前是否有 WebSocket 在线连接。
     */
    Result getOnlineStatus(List<Long> userIds);
}
