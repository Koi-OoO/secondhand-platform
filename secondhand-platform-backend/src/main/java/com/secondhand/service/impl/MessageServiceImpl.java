package com.secondhand.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Conversation;
import com.secondhand.entity.Message;
import com.secondhand.entity.User;
import com.secondhand.mapper.ConversationMapper;
import com.secondhand.mapper.MessageMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.MessageService;
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 私信服务实现类。
 */
@Service  // 标识为Service层组件
public class MessageServiceImpl implements MessageService {

    @Autowired  // 注入私信Mapper
    private MessageMapper messageMapper;
    @Autowired private com.secondhand.mapper.MeetupProposalMapper meetupProposalMapper;
    @Autowired private com.secondhand.mapper.OrderMapper orderMapper;
    @Autowired  // 注入会话Mapper
    private ConversationMapper conversationMapper;
    @Autowired  // 注入用户Mapper（查对方昵称/头像、校验接收者存在）
    private UserMapper userMapper;
    @Autowired  // 注入WebSocket处理器，用于实时推送
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 发送私信
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public Result send(Long fromUserId, Long toUserId, String content, Long productId) {
        return sendInternal(fromUserId, toUserId, content, productId, null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void sendMeetupProposal(com.secondhand.entity.MeetupProposal proposal, Long productId) {
        Result result = sendInternal(proposal.getSellerId(), proposal.getBuyerId(), "面交时间确认 · " + proposal.getOrderNo(), productId, proposal);
        if (result.getCode() != 200) throw new IllegalStateException("预约卡片发送失败");
    }

    private Result sendInternal(Long fromUserId, Long toUserId, String content, Long productId,
                                com.secondhand.entity.MeetupProposal proposal) {
        // 校验接收者
        if (toUserId == null) {
            return Result.error("请选择接收者");
        }
        // 不能给自己发消息
        if (toUserId.equals(fromUserId)) {
            return Result.error("不能给自己发消息");
        }
        // 校验内容非空
        if (content == null || content.trim().isEmpty()) {
            return Result.error("消息内容不能为空");
        }
        // 接收者必须存在
        User toUser = userMapper.selectById(toUserId);
        if (toUser == null) {
            return Result.error("接收者不存在");
        }

        // 消息落库
        Message msg = new Message();
        msg.setFromUserId(fromUserId);  // 发送者
        msg.setToUserId(toUserId);  // 接收者
        msg.setProductId(productId);  // 关联商品（可空）
        if (proposal != null) {
            msg.setMeetupProposalId(proposal.getId()); msg.setMeetupProposal(proposal);
        }
        msg.setContent(content.trim());  // 内容
        msg.setIsRead(false);  // 默认未读
        msg.setCreateTime(new Date());  // 创建时间
        messageMapper.insert(msg);  // 插入（回填自增id）
        upsertConversationAfterMessage(fromUserId, toUserId, productId, msg);

        // 实时推送给接收方（在线才推，离线跳过；统一payload结构 {type,data}）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "chat");  // 前端按type分发
        payload.put("data", msg);  // 消息本体
        String json = JSONUtil.toJsonStr(payload);
        Runnable push = () -> {
            try {
                chatWebSocketHandler.sendToUser(toUserId, json);
                if (proposal != null) chatWebSocketHandler.sendToUser(fromUserId, json);
            } catch (Exception ignored) { /* 消息已保存，离线或推送失败可重新读取。 */ }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { push.run(); }
                });
        } else push.run();

        return Result.success(msg);
    }

    /**
     * 创建或获取会话。
     */
    @Override
    public Result createOrGetConversation(Long userId, Long peerId, Long productId) {
        if (peerId == null) {
            return Result.error("请选择联系人");
        }
        if (peerId.equals(userId)) {
            return Result.error("不能给自己发送私信");
        }

        User peer = userMapper.selectById(peerId);
        if (peer == null) {
            return Result.error("联系人不存在");
        }

        Conversation conversation = findConversation(userId, peerId);
        if (conversation == null) {
            conversation = new Conversation();
            UserPair pair = normalizePair(userId, peerId);
            Date now = new Date();
            conversation.setUser1Id(pair.user1Id);
            conversation.setUser2Id(pair.user2Id);
            conversation.setProductId(productId);
            conversation.setLastTime(now);
            conversation.setCreateTime(now);
            conversation.setUpdateTime(now);
            conversationMapper.insert(conversation);
        } else if (conversation.getProductId() == null && productId != null) {
            conversation.setProductId(productId);
            conversation.setUpdateTime(new Date());
            conversationMapper.updateById(conversation);
        }

        return Result.success(toConversationItem(conversation, peer, null, 0));
    }

    /**
     * 查询我的会话列表
     */
    @Override
    public Result getConversations(Long userId) {
        List<Map<String, Object>> summaries = conversationMapper.selectConversationSummaries(userId);
        return Result.success(summaries != null ? summaries : Collections.emptyList());
    }

    /**
     * 查询我与某人的聊天记录（分页），并把对方发给我的未读消息标记已读
     */
    @Override
    public Result getChat(Long userId, Long peerId, Integer page, Integer size) {
        // 参数校验
        if (peerId == null) {
            return Result.error("参数错误");
        }
        // 页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认20
        if (size == null || size < 1) {
            size = 20;
        }

        // 1. 打开会话即已读：把对方发给我、仍未读的消息标记为已读
        LambdaUpdateWrapper<Message> readWrapper = new LambdaUpdateWrapper<>();
        readWrapper.eq(Message::getFromUserId, peerId)
                .eq(Message::getToUserId, userId)
                .eq(Message::getIsRead, false)
                .set(Message::getIsRead, true);
        messageMapper.update(null, readWrapper);

        // 2. 分页查询双方往来消息：(我->对方) 或 (对方->我)，按id倒序（最新在前）
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.nested(w -> w.eq(Message::getFromUserId, userId).eq(Message::getToUserId, peerId))
                .or()
                .nested(w -> w.eq(Message::getFromUserId, peerId).eq(Message::getToUserId, userId))
                .orderByDesc(Message::getId);
        Page<Message> pageObj = new Page<>(page, size);
        IPage<Message> result = messageMapper.selectPage(pageObj, wrapper);

        // 批量附带可信卡片数据，避免每条消息单独查询。
        List<Long> proposalIds = result.getRecords().stream().map(Message::getMeetupProposalId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (!proposalIds.isEmpty()) {
            Map<Long, com.secondhand.entity.MeetupProposal> proposalMap = meetupProposalMapper.selectBatchIds(proposalIds).stream()
                    .collect(Collectors.toMap(com.secondhand.entity.MeetupProposal::getId, p -> p));
            List<Long> orderIds = proposalMap.values().stream().map(com.secondhand.entity.MeetupProposal::getOrderId).distinct().collect(Collectors.toList());
            Map<Long, com.secondhand.entity.Order> orderMap = orderIds.isEmpty() ? Collections.emptyMap() : orderMapper.selectBatchIds(orderIds).stream()
                    .collect(Collectors.toMap(com.secondhand.entity.Order::getId, o -> o));
            for (Message msg : result.getRecords()) {
                com.secondhand.entity.MeetupProposal p = proposalMap.get(msg.getMeetupProposalId());
                if (p == null) continue;
                com.secondhand.entity.Order order = orderMap.get(p.getOrderId());
                p.setOrderStatus(order == null ? null : order.getStatus());
                p.setCurrentMeetupTime(order == null ? null : order.getMeetupTime());
                msg.setMeetupProposal(p);
            }
        }

        // 3. 组装分页结果
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());  // 消息列表
        data.put("total", result.getTotal());  // 总条数
        data.put("current", result.getCurrent());  // 当前页
        data.put("pages", result.getPages());  // 总页数
        return Result.success(data);
    }

    /**
     * 查询我收到的私信总未读数
     */
    @Override
    public Result getUnreadCount(Long userId) {
        // 统计条件：接收者是我 + 未读
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getToUserId, userId)
                .eq(Message::getIsRead, false);
        Long count = messageMapper.selectCount(wrapper);
        return Result.success(count);
    }

    /**
     * 批量查询用户在线状态。
     */
    @Override
    public Result getOnlineStatus(List<Long> userIds) {
        Map<Long, Map<String, Object>> statusMap = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return Result.success(statusMap);
        }

        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .collect(Collectors.toList());
        for (Long id : ids) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("online", chatWebSocketHandler.isOnline(id));
            status.put("lastActiveTime", chatWebSocketHandler.getLastActiveTime(id));
            statusMap.put(id, status);
        }
        return Result.success(statusMap);
    }

    private void upsertConversationAfterMessage(Long fromUserId, Long toUserId, Long productId, Message message) {
        Conversation conversation = findConversation(fromUserId, toUserId);
        if (conversation == null) {
            conversation = new Conversation();
            UserPair pair = normalizePair(fromUserId, toUserId);
            Date now = new Date();
            conversation.setUser1Id(pair.user1Id);
            conversation.setUser2Id(pair.user2Id);
            conversation.setProductId(productId);
            conversation.setLastMessageId(message.getId());
            conversation.setLastTime(message.getCreateTime());
            conversation.setCreateTime(now);
            conversation.setUpdateTime(now);
            conversationMapper.insert(conversation);
            return;
        }

        conversation.setLastMessageId(message.getId());
        conversation.setLastTime(message.getCreateTime());
        conversation.setUpdateTime(new Date());
        if (conversation.getProductId() == null && productId != null) {
            conversation.setProductId(productId);
        }
        conversationMapper.updateById(conversation);
    }

    private Conversation findConversation(Long userId, Long peerId) {
        UserPair pair = normalizePair(userId, peerId);
        return conversationMapper.selectByPair(pair.user1Id, pair.user2Id);
    }

    private UserPair normalizePair(Long userId, Long peerId) {
        if (userId < peerId) {
            return new UserPair(userId, peerId);
        }
        return new UserPair(peerId, userId);
    }

    private Map<String, Object> toConversationItem(Conversation conversation, User peer, Message last, int unreadCount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("conversationId", conversation.getId());
        item.put("peerId", peer.getId());
        item.put("peerNickname", peer.getNickname());
        item.put("peerUsername", peer.getUsername());
        item.put("peerAvatar", peer.getAvatar());
        item.put("productId", conversation.getProductId());
        item.put("lastId", last != null ? last.getId() : conversation.getLastMessageId());
        item.put("lastContent", last != null ? last.getContent() : null);
        item.put("lastTime", last != null ? last.getCreateTime() : conversation.getLastTime());
        item.put("unreadCount", unreadCount);
        return item;
    }

    private static class UserPair {
        private final Long user1Id;
        private final Long user2Id;

        private UserPair(Long user1Id, Long user2Id) {
            this.user1Id = user1Id;
            this.user2Id = user2Id;
        }
    }
}
