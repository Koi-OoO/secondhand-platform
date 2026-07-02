package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.secondhand.entity.Notification;
import com.secondhand.mapper.NotificationMapper;
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private NotificationMapper notificationMapper;
    private ChatWebSocketHandler chatWebSocketHandler;
    private NotificationServiceImpl service;

    // 纯单元测试不走 Spring/MyBatis 启动流程，需手动初始化实体的 TableInfo（含 Lambda 列缓存），
    // 否则 markRead/markAllRead 里的 LambdaUpdateWrapper.set(...) 解析列名时会抛 "can not find lambda cache"。
    @BeforeAll
    static void initMybatisTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
    }

    @BeforeEach
    void setUp() {
        // 手动mock依赖并通过反射注入（与项目现有Service测试一致）
        notificationMapper = mock(NotificationMapper.class);
        chatWebSocketHandler = mock(ChatWebSocketHandler.class);
        service = new NotificationServiceImpl();
        ReflectionTestUtils.setField(service, "notificationMapper", notificationMapper);
        ReflectionTestUtils.setField(service, "chatWebSocketHandler", chatWebSocketHandler);
    }

    @Test
    void notifyUserPersistsNotificationAndPushes() {
        service.notifyUser(5L, "order", "新订单", "请尽快发货", 100L);

        // 校验落库内容
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(5L);
        assertThat(n.getType()).isEqualTo("order");
        assertThat(n.getTitle()).isEqualTo("新订单");
        assertThat(n.getContent()).isEqualTo("请尽快发货");
        assertThat(n.getRelatedId()).isEqualTo(100L);
        assertThat(n.getIsRead()).isFalse();
        assertThat(n.getCreateTime()).isNotNull();
        // 落库后实时推送给接收者
        verify(chatWebSocketHandler).sendToUser(eq(5L), anyString());
    }

    @Test
    void notifyUserSwallowsExceptionToProtectCaller() {
        // 落库失败时不应向上抛出，避免连累调用方（如下单事务）
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new RuntimeException("db down"));

        service.notifyUser(5L, "order", "标题", "内容", 1L);

        // 落库已失败，不会触发推送；方法也未抛出异常
        verify(chatWebSocketHandler, never()).sendToUser(any(), anyString());
    }

    @Test
    void getUnreadCountReturnsMapperCount() {
        when(notificationMapper.selectCount(any())).thenReturn(3L);

        Result result = service.getUnreadCount(5L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(3L);
    }

    @Test
    void markReadRejectsNullId() {
        Result result = service.markRead(5L, null);

        assertThat(result.getCode()).isEqualTo(500);
        verify(notificationMapper, never()).update(any(), any());
    }

    @Test
    void markReadUpdatesNotification() {
        Result result = service.markRead(5L, 9L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(notificationMapper).update(any(), any());
    }

    @Test
    void markAllReadUpdatesUnread() {
        Result result = service.markAllRead(5L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(notificationMapper).update(any(), any());
    }
}
