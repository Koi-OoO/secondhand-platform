package com.secondhand.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Notification;
import com.secondhand.entity.User;
import com.secondhand.mapper.NotificationMapper;
import com.secondhand.mapper.UserMapper;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    @Autowired  // 注入用户Mapper，用于查询收藏者公开信息
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
            // 1. 查询收藏者；查不到时退回匿名通知
            User actor = userMapper.selectById(collectorId);
            if (actor == null) {
                notifyUser(sellerId, "favorite", "有人收藏了你的商品",
                        "您的商品「" + productTitle + "」被收藏了", productId);
                return;
            }

            // 2. 规范化收藏者昵称和头像，限制对外展示长度
            String nickname = actor.getNickname() == null ? "" : actor.getNickname().trim();
            if (nickname.isEmpty()) {
                nickname = "校园用户";
            }
            if (nickname.length() > 50) {
                nickname = nickname.substring(0, 50);
            }
            String avatar = actor.getAvatar();
            if (avatar != null && avatar.length() > 500) {
                avatar = null;
            }

            // 3. 创建带收藏者公开信息的通知
            sendNotification(sellerId, "favorite", nickname + "收藏了你的商品",
                    nickname + "收藏了「" + productTitle + "」", productId, actor.getId(), nickname, avatar);
        } catch (Exception exception) {
            log.error("创建收藏通知失败：productId={}", productId, exception);
        }
    }

    private void sendNotification(Long userId, String type, String title, String content, Long relatedId,
                                  Long actorUserId, String actorNickname, String actorAvatar) {
        // 通知是业务事件的附属操作，整体兜底异常，绝不影响调用方（如下单事务）的主流程
        try {
            // 1. 保存通知记录
            Notification notification = new Notification();
            notification.setUserId(userId);  // 接收者
            notification.setType(type);  // 类型
            notification.setTitle(title);  // 标题
            notification.setContent(content);  // 内容
            notification.setRelatedId(relatedId);  // 关联业务ID
            notification.setActorUserId(actorUserId);
            notification.setActorNickname(actorNickname);
            notification.setActorAvatar(actorAvatar);
            notification.setIsRead(false);  // 默认未读
            notification.setCreateTime(new Date());  // 创建时间
            notificationMapper.insert(notification);  // 插入数据库（回填自增id）

            // 2. 组装实时推送内容，统一为 {type,data} 结构
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "notification");  // 前端按type分发
            payload.put("data", notification);  // 通知本体
            // 用Hutool序列化为纯JSON（不带@class，避免RedisTemplate那套typing污染）
            String json = JSONUtil.toJsonStr(payload);

            // 3. 通知参与原业务事务时，只在提交成功后推送；没有事务则立即推送
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pushSafely(userId, json);
                    }
                });
            } else {
                pushSafely(userId, json);
            }
        } catch (Exception exception) {
            // 通知失败仅记日志，不向上抛出
            log.error("发送通知失败：userId={} type={}", userId, type, exception);
        }
    }

    private void pushSafely(Long userId, String json) {
        try {
            chatWebSocketHandler.sendToUser(userId, json);
        } catch (Exception exception) {
            log.error("实时通知推送失败：userId={}", userId, exception);
        }
    }

    /**
     * 分页查询我的通知
     */
    @Override
    public Result getMyNotifications(Long userId, Integer page, Integer size) {
        // 1. 规范页码和每页条数
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }

        // 2. 查询本人通知，按创建时间和ID倒序
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId);
        Page<Notification> pageRequest = new Page<>(page, size);
        IPage<Notification> pageResult = notificationMapper.selectPage(pageRequest, wrapper);

        // 3. 组装分页结果，保持与其他模块一致的返回结构
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", pageResult.getRecords());  // 数据列表
        data.put("total", pageResult.getTotal());  // 总记录数
        data.put("current", pageResult.getCurrent());  // 当前页码
        data.put("pages", pageResult.getPages());  // 总页数
        return Result.success(data);
    }

    /**
     * 查询我的未读通知数
     */
    @Override
    public Result getUnreadCount(Long userId) {
        // 1. 统计本人未读通知
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);

        // 2. 返回未读数量
        Long count = notificationMapper.selectCount(wrapper);
        return Result.success(count);
    }

    /**
     * 标记单条通知已读（限本人）
     */
    @Override
    public Result markRead(Long userId, Long id) {
        // 1. 校验通知ID
        if (id == null) {
            return Result.error("参数错误");
        }

        // 2. 仅更新属于自己的那条通知，避免越权
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
