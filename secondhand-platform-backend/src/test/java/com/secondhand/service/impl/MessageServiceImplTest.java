package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Message;
import com.secondhand.entity.User;
import com.secondhand.mapper.MessageMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.util.Collections;
import java.util.List;
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

class MessageServiceImplTest {

    private MessageMapper messageMapper;
    private UserMapper userMapper;
    private ChatWebSocketHandler chatWebSocketHandler;
    private MessageServiceImpl service;

    // 纯单元测试不走 Spring/MyBatis 启动流程，需手动初始化实体的 TableInfo（含 Lambda 列缓存），
    // 否则 getChat 里的 LambdaUpdateWrapper.set(...) 解析列名时会抛 "can not find lambda cache"。
    @BeforeAll
    static void initMybatisTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Message.class);
    }

    @BeforeEach
    void setUp() {
        // 手动mock依赖并通过反射注入（与项目现有Service测试一致）
        messageMapper = mock(MessageMapper.class);
        userMapper = mock(UserMapper.class);
        chatWebSocketHandler = mock(ChatWebSocketHandler.class);
        service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "chatWebSocketHandler", chatWebSocketHandler);
    }

    @Test
    void sendRejectsInvalidArgumentsBeforeQuerying() {
        // 接收者为空、发给自己、内容为空，均应在查库前被拦截
        assertThat(service.send(1L, null, "hi", null).getMessage()).isEqualTo("请选择接收者");
        assertThat(service.send(1L, 1L, "hi", null).getMessage()).isEqualTo("不能给自己发消息");
        assertThat(service.send(1L, 2L, "   ", null).getMessage()).isEqualTo("消息内容不能为空");

        verify(messageMapper, never()).insert(any(Message.class));
        verify(chatWebSocketHandler, never()).sendToUser(any(), anyString());
    }

    @Test
    void sendRejectsWhenReceiverNotFound() {
        when(userMapper.selectById(2L)).thenReturn(null);

        Result result = service.send(1L, 2L, "你好", null);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("接收者不存在");
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void sendPersistsMessageAndPushesToReceiver() {
        User receiver = new User();
        receiver.setId(2L);
        when(userMapper.selectById(2L)).thenReturn(receiver);

        Result result = service.send(1L, 2L, "你好", 100L);

        assertThat(result.getCode()).isEqualTo(200);
        // 校验落库内容
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(captor.capture());
        Message msg = captor.getValue();
        assertThat(msg.getFromUserId()).isEqualTo(1L);
        assertThat(msg.getToUserId()).isEqualTo(2L);
        assertThat(msg.getProductId()).isEqualTo(100L);
        assertThat(msg.getContent()).isEqualTo("你好");
        assertThat(msg.getIsRead()).isFalse();
        assertThat(msg.getCreateTime()).isNotNull();
        // 接收方在线则实时推送
        verify(chatWebSocketHandler).sendToUser(eq(2L), anyString());
    }

    @Test
    void getChatMarksPeerMessagesReadThenPages() {
        when(messageMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        Result result = service.getChat(1L, 2L, 1, 20);

        assertThat(result.getCode()).isEqualTo(200);
        // 打开会话即已读：先把对方->我的未读消息更新为已读
        verify(messageMapper).update(any(), any());
        // 再分页查询双方往来消息
        verify(messageMapper).selectPage(any(), any());
    }

    @Test
    void getChatRejectsNullPeer() {
        Result result = service.getChat(1L, null, 1, 20);

        assertThat(result.getCode()).isEqualTo(500);
        verify(messageMapper, never()).selectPage(any(), any());
    }

    @Test
    void getUnreadCountReturnsMapperCount() {
        when(messageMapper.selectCount(any())).thenReturn(2L);

        Result result = service.getUnreadCount(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(2L);
    }

    @Test
    void getConversationsReturnsEmptyWhenNoMessages() {
        when(messageMapper.selectConversationSummaries(1L)).thenReturn(Collections.emptyList());

        Result result = service.getConversations(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat((List<?>) result.getData()).isEmpty();
    }
}
