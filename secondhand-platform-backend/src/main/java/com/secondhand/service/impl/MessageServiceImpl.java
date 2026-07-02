package com.secondhand.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Message;
import com.secondhand.entity.User;
import com.secondhand.mapper.MessageMapper;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 私信服务实现类。
 */
@Service  // 标识为Service层组件
public class
MessageServiceImpl implements MessageService {

    @Autowired  // 注入私信Mapper
    private MessageMapper messageMapper;
    @Autowired  // 注入用户Mapper（查对方昵称/头像、校验接收者存在）
    private UserMapper userMapper;
    @Autowired  // 注入WebSocket处理器，用于实时推送
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 发送私信
     */
    @Override
    public Result send(Long fromUserId, Long toUserId, String content, Long productId) {
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
        msg.setContent(content.trim());  // 内容
        msg.setIsRead(false);  // 默认未读
        msg.setCreateTime(new Date());  // 创建时间
        messageMapper.insert(msg);  // 插入（回填自增id）

        // 实时推送给接收方（在线才推，离线跳过；统一payload结构 {type,data}）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "chat");  // 前端按type分发
        payload.put("data", msg);  // 消息本体
        chatWebSocketHandler.sendToUser(toUserId, JSONUtil.toJsonStr(payload));

        return Result.success(msg);
    }

    /**
     * 查询我的会话列表
     */
    @Override
    public Result getConversations(Long userId) {
        // 1. 聚合查询会话摘要：对方id、最后消息id、未读数
        List<Map<String, Object>> summaries = messageMapper.selectConversationSummaries(userId);
        if (summaries.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 2. 收集对方id与最后消息id，便于批量查询
        List<Long> peerIds = new ArrayList<>();
        List<Long> lastIds = new ArrayList<>();
        for (Map<String, Object> s : summaries) {
            // 聚合结果为Map，数值统一按Number取出再转换
            peerIds.add(((Number) s.get("peerId")).longValue());
            lastIds.add(((Number) s.get("lastId")).longValue());
        }
        // 批量查最后一条消息：id -> Message
        Map<Long, Message> lastMsgMap = messageMapper.selectBatchIds(lastIds).stream()
                .collect(Collectors.toMap(Message::getId, m -> m));
        // 批量查对方用户：id -> User
        Map<Long, User> userMap = userMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 3. 组装会话列表
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> s : summaries) {
            Long peerId = ((Number) s.get("peerId")).longValue();
            Long lastId = ((Number) s.get("lastId")).longValue();
            Number unread = (Number) s.get("unreadCount");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("peerId", peerId);  // 对方用户id
            // 对方昵称/头像
            User peer = userMap.get(peerId);
            item.put("peerNickname", peer != null ? peer.getNickname() : null);
            item.put("peerAvatar", peer != null ? peer.getAvatar() : null);
            // 最后一条消息预览
            Message last = lastMsgMap.get(lastId);
            if (last != null) {
                item.put("lastContent", last.getContent());
                item.put("lastTime", last.getCreateTime());
            }
            // 该会话未读数（SUM结果为BigDecimal，统一按Number处理）
            item.put("unreadCount", unread != null ? unread.intValue() : 0);
            list.add(item);
        }
        return Result.success(list);
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
}
