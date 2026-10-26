package com.secondhand.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Notification;
import com.secondhand.mapper.NotificationMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.entity.User;
import com.secondhand.service.NotificationService;
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 通知服务实现类。
 */
@Service  // 标识为Service层组件
public class NotificationServiceImpl implements NotificationService {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired  // 注入通知Mapper
    private NotificationMapper notificationMapper;
    @Autowired  // 注入WebSocket处理器，用于实时推送
    private ChatWebSocketHandler chatWebSocketHandler;
    @Autowired
    private UserMapper userMapper;

    /**
     * 创建通知并实时推送
     */
    @Override
    public void notifyUser(Long userId, String type, String title, String content, Long relatedId) {
        sendNotification(userId, type, title, content, relatedId, null, null, null);
    }

    @Override
    public void notifyFavorite(Long sellerId, Long collectorId, Long productId, String productTitle) {
        try {
            User actor = userMapper.selectById(collectorId);
            if (actor == null) {
                notifyUser(sellerId, "favorite", "有人收藏了你的商品", "您的商品「" + productTitle + "」被收藏了", productId);
                return;
            }
            String nickname = actor.getNickname() == null ? "" : actor.getNickname().trim();
            if (nickname.isEmpty()) nickname = "校园用户";
            if (nickname.length() > 50) nickname = nickname.substring(0, 50);
            String avatar = actor.getAvatar();
            if (avatar != null && avatar.length() > 500) avatar = null;
            sendNotification(sellerId, "favorite", nickname + "收藏了你的商品",
                    nickname + "收藏了「" + productTitle + "」", productId, actor.getId(), nickname, avatar);
        } catch (Exception ex) {
            log.error("创建收藏通知失败：productId={}", productId, ex);
        }
    }

    private void sendNotification(Long userId, String type, String title, String content, Long relatedId,
                                  Long actorUserId, String actorNickname, String actorAvatar) {
        // 通知是业务事件的附属操作，整体兜底异常，绝不影响调用方（如下单事务）的主流程
        try {
            // 1. 通知落库
            Notification n = new Notification();
            n.setUserId(userId);  // 接收者
            n.setType(type);  // 类型
            n.setTitle(title);  // 标题
            n.setContent(content);  // 内容
            n.setRelatedId(relatedId);  // 关联业务ID
            n.setActorUserId(actorUserId);
            n.setActorNickname(actorNickname);
            n.setActorAvatar(actorAvatar);
            n.setIsRead(false);  // 默认未读
            n.setCreateTime(new Date());  // 创建时间
            notificationMapper.insert(n);  // 插入数据库（回填自增id）

            // 2. 实时推送（在线才推，离线跳过；统一payload结构 {type,data}）
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "notification");  // 前端按type分发
            payload.put("data", n);  // 通知本体
            // 用Hutool序列化为纯JSON（不带@class，避免RedisTemplate那套typing污染）
            String json = JSONUtil.toJsonStr(payload);
            // 通知记录参与原业务事务；仅提交成功后发送实时提醒。
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    && org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() { pushSafely(userId, json); }
                    });
            } else {
                pushSafely(userId, json);
            }
        } catch (Exception ex) {
            // 通知失败仅记日志，不向上抛出
            log.error("发送通知失败：userId={} type={}", userId, type, ex);
        }
    }

    private void pushSafely(Long userId, String json) {
        try {
            chatWebSocketHandler.sendToUser(userId, json);
        } catch (Exception ex) {
            log.error("实时通知推送失败：userId={}", userId, ex);
        }
    }

    /**
     * 分页查询我的通知
     */
    @Override
    public Result getMyNotifications(Long userId, Integer page, Integer size) {
        // 页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认10
        if (size == null || size < 1) {
            size = 10;
        }
        // 查询条件：本人通知，按创建时间倒序
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime).orderByDesc(Notification::getId);
        // 分页查询
        Page<Notification> pageObj = new Page<>(page, size);
        IPage<Notification> result = notificationMapper.selectPage(pageObj, wrapper);
        // 组装分页结果（与其他模块返回结构保持一致）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());  // 数据列表
        data.put("total", result.getTotal());  // 总记录数
        data.put("current", result.getCurrent());  // 当前页码
        data.put("pages", result.getPages());  // 总页数
        return Result.success(data);
    }

    /**
     * 查询我的未读通知数
     */
    @Override
    public Result getUnreadCount(Long userId) {
        // 统计条件：本人 + 未读
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);
        // 返回未读数量
        Long count = notificationMapper.selectCount(wrapper);
        return Result.success(count);
    }

    /**
     * 标记单条通知已读（限本人）
     */
    @Override
    public Result markRead(Long userId, Long id) {
        // 参数校验
        if (id == null) {
            return Result.error("参数错误");
        }
        // 仅更新属于自己的那条通知，避免越权
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, true);
        notificationMapper.update(null, wrapper);
        return Result.success("已读");
    }

    /**
     * 全部标记已读
     */
    @Override
    public Result markAllRead(Long userId) {
        // 把本人所有未读通知置为已读
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true);
        notificationMapper.update(null, wrapper);
        return Result.success("全部已读");
    }
}
