package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceImplTest {

    private MessageMapper messageMapper;
    private MeetupProposalMapper meetupProposalMapper;
    private OrderMapper orderMapper;
    private ConversationMapper conversationMapper;
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
        meetupProposalMapper = mock(MeetupProposalMapper.class);
        orderMapper = mock(OrderMapper.class);
        conversationMapper = mock(ConversationMapper.class);
        userMapper = mock(UserMapper.class);
        chatWebSocketHandler = mock(ChatWebSocketHandler.class);
        service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "meetupProposalMapper", meetupProposalMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "chatWebSocketHandler", chatWebSocketHandler);
    }

    @Test
    void firstMessageSucceedsWhenAnotherRequestAlreadyCreatedTheSameConversation() {
        User receiver = new User(); receiver.setId(2L);
        when(userMapper.selectById(2L)).thenReturn(receiver);
        // 普通查询未看见对方刚提交的会话，旧的“先查后插”会命中唯一键冲突。
        when(conversationMapper.selectByPair(1L, 2L)).thenReturn(null);
        doThrow(new org.springframework.dao.DuplicateKeyException("conversation.uk_user_pair"))
                .when(conversationMapper).insert(any(Conversation.class));
        doAnswer(invocation -> { ((Message) invocation.getArgument(0)).setId(99L); return 1; })
                .when(messageMapper).insert(any(Message.class));

        Result result = service.send(1L, 2L, "第一条消息", 100L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(((Message) result.getData()).getId()).isEqualTo(99L);
        verify(messageMapper).insert(any(Message.class));
        verify(chatWebSocketHandler).sendToUser(eq(2L), anyString());
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
    void createOrGetConversationCreatesEmptyConversationForValidPeer() {
        User peer = new User();
        peer.setId(2L);
        peer.setNickname("卖家");
        peer.setAvatar("avatar.png");
        when(userMapper.selectById(2L)).thenReturn(peer);
        Conversation[] stored = new Conversation[1];
        when(conversationMapper.selectByPair(1L, 2L)).thenAnswer(invocation -> stored[0]);
        doAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(10L);
            stored[0] = conversation;
            return 1;
        }).when(conversationMapper).ensureExists(any(Conversation.class));

        Result result = service.createOrGetConversation(1L, 2L, 100L);

        assertThat(result.getCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data)
                .containsEntry("conversationId", 10L)
                .containsEntry("peerId", 2L)
                .containsEntry("peerNickname", "卖家")
                .containsEntry("peerAvatar", "avatar.png")
                .containsEntry("productId", 100L)
                .containsEntry("lastContent", null)
                .containsEntry("unreadCount", 0);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).ensureExists(captor.capture());
        Conversation saved = captor.getValue();
        assertThat(saved.getUser1Id()).isEqualTo(1L);
        assertThat(saved.getUser2Id()).isEqualTo(2L);
        assertThat(saved.getProductId()).isEqualTo(100L);
        assertThat(saved.getLastMessageId()).isNull();
        assertThat(saved.getLastTime()).isNotNull();
    }

    @Test
    void createOrGetConversationRejectsSelfAndMissingPeer() {
        assertThat(service.createOrGetConversation(1L, null, 100L).getMessage()).isEqualTo("请选择联系人");
        assertThat(service.createOrGetConversation(1L, 1L, 100L).getMessage()).isEqualTo("不能给自己发送私信");

        when(userMapper.selectById(99L)).thenReturn(null);
        Result missingPeer = service.createOrGetConversation(1L, 99L, 100L);

        assertThat(missingPeer.getCode()).isEqualTo(500);
        assertThat(missingPeer.getMessage()).isEqualTo("联系人不存在");
        verify(conversationMapper, never()).ensureExists(any(Conversation.class));
    }

    @Test
    void createOrGetConversationKeepsExistingConversationAndFillsMissingProduct() {
        User peer = new User();
        peer.setId(2L);
        peer.setUsername("seller");
        Conversation existing = new Conversation();
        existing.setId(11L);
        existing.setUser1Id(1L);
        existing.setUser2Id(2L);
        existing.setLastTime(new Date());

        when(userMapper.selectById(2L)).thenReturn(peer);
        when(conversationMapper.selectByPair(1L, 2L)).thenReturn(existing);
        doAnswer(invocation -> {
            if (existing.getProductId() == null) existing.setProductId(((Conversation) invocation.getArgument(0)).getProductId());
            return 1;
        }).when(conversationMapper).ensureExists(any(Conversation.class));

        Result result = service.createOrGetConversation(1L, 2L, 100L);

        assertThat(result.getCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data)
                .containsEntry("conversationId", 11L)
                .containsEntry("peerId", 2L)
                .containsEntry("peerNickname", null)
                .containsEntry("peerUsername", "seller")
                .containsEntry("productId", 100L);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).ensureExists(captor.capture());
        assertThat(captor.getValue().getUser1Id()).isEqualTo(1L);
        assertThat(captor.getValue().getProductId()).isEqualTo(100L);
    }

    @Test
    void sendUpdatesConversationLastMessage() {
        User receiver = new User();
        receiver.setId(2L);
        Conversation existing = new Conversation();
        existing.setId(7L);
        existing.setUser1Id(1L);
        existing.setUser2Id(2L);
        when(userMapper.selectById(2L)).thenReturn(receiver);
        when(conversationMapper.selectByPair(1L, 2L)).thenReturn(existing);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(99L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));

        Result result = service.send(1L, 2L, "你好", 100L);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).upsertAfterMessage(captor.capture());
        Conversation updated = captor.getValue();
        assertThat(updated.getUser1Id()).isEqualTo(1L);
        assertThat(updated.getUser2Id()).isEqualTo(2L);
        assertThat(updated.getLastMessageId()).isEqualTo(99L);
        assertThat(updated.getProductId()).isEqualTo(100L);
        assertThat(updated.getLastTime()).isNotNull();
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
    void getChatAttachesMeetupProposalWithCurrentOrderStatus() {
        Date meetupTime = new Date(1700000000000L);
        Message message = new Message();
        message.setId(30L);
        message.setMeetupProposalId(5L);
        Page<Message> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(message));

        MeetupProposal proposal = new MeetupProposal();
        proposal.setId(5L);
        proposal.setOrderId(10L);
        Order order = new Order();
        order.setId(10L);
        order.setStatus(3);
        order.setMeetupTime(meetupTime);

        when(messageMapper.selectPage(any(), any())).thenReturn(page);
        when(meetupProposalMapper.selectBatchIds(Collections.singletonList(5L)))
                .thenReturn(Collections.singletonList(proposal));
        when(orderMapper.selectBatchIds(Collections.singletonList(10L)))
                .thenReturn(Collections.singletonList(order));

        Result result = service.getChat(1L, 2L, 1, 20);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(message.getMeetupProposal()).isSameAs(proposal);
        assertThat(proposal.getOrderStatus()).isEqualTo(3);
        assertThat(proposal.getCurrentMeetupTime()).isEqualTo(meetupTime);
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
    void getOnlineStatusReturnsDeduplicatedStatusMap() {
        Date activeAt = new Date(1700000000000L);
        when(chatWebSocketHandler.isOnline(2L)).thenReturn(true);
        when(chatWebSocketHandler.isOnline(3L)).thenReturn(false);
        when(chatWebSocketHandler.getLastActiveTime(2L)).thenReturn(activeAt);

        Result result = service.getOnlineStatus(Arrays.asList(2L, 3L, 2L, null, -1L));

        assertThat(result.getCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<Long, Map<String, Object>> data = (Map<Long, Map<String, Object>>) result.getData();
        assertThat(data.get(2L)).containsEntry("online", true)
                .containsEntry("lastActiveTime", activeAt);
        assertThat(data.get(3L)).containsEntry("online", false)
                .containsEntry("lastActiveTime", null);
        assertThat(data).hasSize(2);
    }

    @Test
    void getConversationsReturnsRowsFromConversationMapper() {
        List<Map<String, Object>> summaries = Collections.singletonList(Collections.singletonMap("peerId", 2L));
        when(conversationMapper.selectConversationSummaries(1L)).thenReturn(summaries);

        Result result = service.getConversations(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(summaries);
        verify(messageMapper, never()).selectConversationSummaries(1L);
    }
}
