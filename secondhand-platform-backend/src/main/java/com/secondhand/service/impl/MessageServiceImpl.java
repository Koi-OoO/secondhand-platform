package com.secondhand.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Conversation;
import com.secondhand.entity.MeetupProposal;
import com.secondhand.entity.Message;
import com.secondhand.entity.Order;
import com.secondhand.entity.User;
import com.secondhand.mapper.ConversationMapper;
import com.secondhand.mapper.MeetupProposalMapper;
import com.secondhand.mapper.MessageMapper;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.MessageService;
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 私信服务实现类。
 */
@Service  // 标识为Service层组件
public class MessageServiceImpl implements MessageService {

    @Autowired  // 注入私信Mapper
    private MessageMapper messageMapper;
    @Autowired  // 注入预约Mapper（聊天记录附带预约卡片）
    private MeetupProposalMapper meetupProposalMapper;
    @Autowired  // 注入订单Mapper（预约卡片展示当前订单状态）
    private OrderMapper orderMapper;
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
    @Transactional
    public Result send(Long fromUserId, Long toUserId, String content, Long productId) {
        // 普通私信不允许携带预约卡片
        return sendInternal(fromUserId, toUserId, content, productId, null);
    }

    @Override
    @Transactional
    public void sendMeetupProposal(MeetupProposal proposal, Long productId) {
        // 1. 以卖家身份向买家发送预约卡片
        Result result = sendInternal(proposal.getSellerId(), proposal.getBuyerId(),
                "面交时间确认 · " + proposal.getOrderNo(), productId, proposal);

        // 2. 卡片发送失败时抛出异常，让预约事务回滚
        if (result.getCode() != 200) {
            throw new IllegalStateException("预约卡片发送失败");
        }
    }

    private Result sendInternal(Long fromUserId, Long toUserId, String content, Long productId,
                                MeetupProposal proposal) {
        // 1. 校验接收者和消息内容
        if (toUserId == null) {
            return Result.error("请选择接收者");
        }
        // 不能给自己发消息
        if (toUserId.equals(fromUserId)) {
            return Result.error("不能给自己发消息");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error("消息内容不能为空");
        }

        // 2. 确认接收者存在
        User toUser = userMapper.selectById(toUserId);
        if (toUser == null) {
            return Result.error("接收者不存在");
        }

        // 3. 保存消息；预约卡片同时保存预约引用
        Message message = new Message();
        message.setFromUserId(fromUserId);  // 发送者
        message.setToUserId(toUserId);  // 接收者
        message.setProductId(productId);  // 关联商品（可空）
        if (proposal != null) {
            message.setMeetupProposalId(proposal.getId());
            message.setMeetupProposal(proposal);
        }
        message.setContent(content.trim());  // 内容
        message.setIsRead(false);  // 默认未读
        message.setCreateTime(new Date());  // 创建时间
        messageMapper.insert(message);  // 插入（回填自增id）
        upsertConversationAfterMessage(fromUserId, toUserId, productId, message);

        // 4. 事务提交后实时推送；接收方在线才推，预约卡片也回推给发送方
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "chat");  // 前端按type分发
        payload.put("data", message);  // 消息本体
        String json = JSONUtil.toJsonStr(payload);
        Runnable push = () -> {
            try {
                chatWebSocketHandler.sendToUser(toUserId, json);
                if (proposal != null) {
                    chatWebSocketHandler.sendToUser(fromUserId, json);
                }
            } catch (Exception ignored) {
                // 消息已保存，离线或推送失败可重新读取
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }

        // 5. 返回落库后的消息
        return Result.success(message);
    }

    /**
     * 创建或获取会话。
     */
    @Override
    @Transactional
    public Result createOrGetConversation(Long userId, Long peerId, Long productId) {
        // 1. 校验联系人
        if (peerId == null) {
            return Result.error("请选择联系人");
        }
        if (peerId.equals(userId)) {
            return Result.error("不能给自己发送私信");
        }

        // 2. 确认联系人存在
        User peer = userMapper.selectById(peerId);
        if (peer == null) {
            return Result.error("联系人不存在");
        }

        // 3. 原子创建或复用会话，唯一键处理并发创建
        Conversation candidate = newConversation(userId, peerId, productId, new Date());
        conversationMapper.ensureExists(candidate);
        Conversation conversation = findConversation(userId, peerId);
        if (conversation == null) {
            throw new IllegalStateException("会话创建后未能读取");
        }

        // 4. 返回会话摘要
        return Result.success(toConversationItem(conversation, peer, null, 0));
    }

    /**
     * 查询我的会话列表
     */
    @Override
    public Result getConversations(Long userId) {
        // 1. 查询包含空会话的会话摘要
        List<Map<String, Object>> summaries = conversationMapper.selectConversationSummaries(userId);

        // 2. 没有会话时返回空列表
        return Result.success(summaries != null ? summaries : Collections.emptyList());
    }

    /**
     * 查询我与某人的聊天记录（分页），并把对方发给我的未读消息标记已读
     */
    @Override
    public Result getChat(Long userId, Long peerId, Integer page, Integer size) {
        // 1. 校验对方用户ID，规范页码和每页条数
        if (peerId == null) {
            return Result.error("参数错误");
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        // 2. 打开会话即已读：把对方发给我、仍未读的消息标记为已读
        LambdaUpdateWrapper<Message> readWrapper = new LambdaUpdateWrapper<>();
        readWrapper.eq(Message::getFromUserId, peerId)
                .eq(Message::getToUserId, userId)
                .eq(Message::getIsRead, false)
                .set(Message::getIsRead, true);
        messageMapper.update(null, readWrapper);

        // 3. 分页查询双方往来消息：(我->对方) 或 (对方->我)，按id倒序
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.nested(condition -> condition.eq(Message::getFromUserId, userId)
                        .eq(Message::getToUserId, peerId))
                .or()
                .nested(condition -> condition.eq(Message::getFromUserId, peerId)
                        .eq(Message::getToUserId, userId))
                .orderByDesc(Message::getId);
        Page<Message> pageRequest = new Page<>(page, size);
        IPage<Message> pageResult = messageMapper.selectPage(pageRequest, wrapper);

        // 4. 批量查询消息引用的预约卡片，并补充订单状态
        List<Long> proposalIds = new ArrayList<>();
        for (Message message : pageResult.getRecords()) {
            Long proposalId = message.getMeetupProposalId();
            if (proposalId != null && !proposalIds.contains(proposalId)) {
                proposalIds.add(proposalId);
            }
        }
        if (!proposalIds.isEmpty()) {
            List<MeetupProposal> proposals = meetupProposalMapper.selectBatchIds(proposalIds);
            Map<Long, MeetupProposal> proposalMap = new LinkedHashMap<>();
            for (MeetupProposal proposal : proposals) {
                proposalMap.put(proposal.getId(), proposal);
            }

            List<Long> orderIds = new ArrayList<>();
            for (MeetupProposal proposal : proposals) {
                if (!orderIds.contains(proposal.getOrderId())) {
                    orderIds.add(proposal.getOrderId());
                }
            }
            Map<Long, Order> orderMap = new LinkedHashMap<>();
            if (!orderIds.isEmpty()) {
                List<Order> orders = orderMapper.selectBatchIds(orderIds);
                for (Order order : orders) {
                    orderMap.put(order.getId(), order);
                }
            }

            for (Message message : pageResult.getRecords()) {
                MeetupProposal proposal = proposalMap.get(message.getMeetupProposalId());
                if (proposal == null) {
                    continue;
                }
                Order order = orderMap.get(proposal.getOrderId());
                if (order == null) {
                    proposal.setOrderStatus(null);
                    proposal.setCurrentMeetupTime(null);
                } else {
                    proposal.setOrderStatus(order.getStatus());
                    proposal.setCurrentMeetupTime(order.getMeetupTime());
                }
                message.setMeetupProposal(proposal);
            }
        }

        // 5. 组装分页结果
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", pageResult.getRecords());  // 消息列表
        data.put("total", pageResult.getTotal());  // 总条数
        data.put("current", pageResult.getCurrent());  // 当前页
        data.put("pages", pageResult.getPages());  // 总页数
        return Result.success(data);
    }

    /**
     * 查询我收到的私信总未读数
     */
    @Override
    public Result getUnreadCount(Long userId) {
        // 1. 统计接收者是我且未读的消息
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getToUserId, userId)
                .eq(Message::getIsRead, false);

        // 2. 返回未读总数
        Long count = messageMapper.selectCount(wrapper);
        return Result.success(count);
    }

    /**
     * 批量查询用户在线状态。
     */
    @Override
    public Result getOnlineStatus(List<Long> userIds) {
        // 1. 没有查询条件时返回空结果
        Map<Long, Map<String, Object>> statusMap = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return Result.success(statusMap);
        }

        // 2. 过滤空值和非正数，并按传入顺序去重
        List<Long> validUserIds = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0 && !validUserIds.contains(userId)) {
                validUserIds.add(userId);
            }
        }

        // 3. 逐个查询在线状态和最后活跃时间
        for (Long userId : validUserIds) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("online", chatWebSocketHandler.isOnline(userId));
            status.put("lastActiveTime", chatWebSocketHandler.getLastActiveTime(userId));
            statusMap.put(userId, status);
        }
        return Result.success(statusMap);
    }

    private void upsertConversationAfterMessage(Long fromUserId, Long toUserId, Long productId, Message message) {
        // 1. 按双方用户和商品构造会话摘要
        Conversation conversation = newConversation(fromUserId, toUserId, productId, message.getCreateTime());
        conversation.setLastMessageId(message.getId());

        // 2. 原子写入；并发时只保留更大的消息ID
        conversationMapper.upsertAfterMessage(conversation);
    }

    private Conversation newConversation(Long userId, Long peerId, Long productId, Date lastTime) {
        // 1. 规范化双方用户顺序，保证同一对用户只有一条会话
        UserPair pair = normalizePair(userId, peerId);

        // 2. 组装会话的初始字段
        Date now = new Date();
        Conversation conversation = new Conversation();
        conversation.setUser1Id(pair.user1Id);
        conversation.setUser2Id(pair.user2Id);
        conversation.setProductId(productId);
        conversation.setLastTime(lastTime);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        return conversation;
    }

    private Conversation findConversation(Long userId, Long peerId) {
        UserPair pair = normalizePair(userId, peerId);
        return conversationMapper.selectByPair(pair.user1Id, pair.user2Id);
    }

    private UserPair normalizePair(Long userId, Long peerId) {
        // 较小的用户ID固定放在 user1Id，避免同一对用户生成两条会话
        if (userId < peerId) {
            return new UserPair(userId, peerId);
        }
        return new UserPair(peerId, userId);
    }

    private Map<String, Object> toConversationItem(Conversation conversation, User peer, Message lastMessage,
                                                   int unreadCount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("conversationId", conversation.getId());
        item.put("peerId", peer.getId());
        item.put("peerNickname", peer.getNickname());
        item.put("peerUsername", peer.getUsername());
        item.put("peerAvatar", peer.getAvatar());
        item.put("productId", conversation.getProductId());
        item.put("lastId", lastMessage != null ? lastMessage.getId() : conversation.getLastMessageId());
        item.put("lastContent", lastMessage != null ? lastMessage.getContent() : null);
        item.put("lastTime", lastMessage != null ? lastMessage.getCreateTime() : conversation.getLastTime());
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
