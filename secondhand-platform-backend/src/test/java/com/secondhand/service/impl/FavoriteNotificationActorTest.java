package com.secondhand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Notification;
import com.secondhand.entity.User;
import com.secondhand.mapper.NotificationMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.websocket.ChatWebSocketHandler;
import cn.hutool.json.JSONUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteNotificationActorTest {
    final NotificationMapper notifications=mock(NotificationMapper.class);
    final UserMapper users=mock(UserMapper.class);
    final ChatWebSocketHandler socket=mock(ChatWebSocketHandler.class);
    final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withBean(NotificationMapper.class,()->notifications)
            .withBean(UserMapper.class,()->users)
            .withBean(ChatWebSocketHandler.class,()->socket)
            .withUserConfiguration(NotificationServiceImpl.class);

    private void send(NotificationServiceImpl service) {
        // 反射使尚未实现该方法时表现为缺失能力，而不阻止其他测试编译。
        ReflectionTestUtils.invokeMethod(service,"notifyFavorite",99L,7L,10L,"台灯");
    }

    @Test
    void favoritePersistsPublicActorSnapshotAndPushesSameDataWithoutSensitiveUserFields() {
        User user=new User();user.setId(7L);user.setNickname("小明");user.setAvatar("/uploads/avatar.png");
        user.setPassword("private-password-fixture");user.setPhone("private-phone-fixture");user.setEmail("private-email-fixture");
        when(users.selectById(7L)).thenReturn(user);
        runner.run(context->send(context.getBean(NotificationServiceImpl.class)));
        ArgumentCaptor<Notification> saved=ArgumentCaptor.forClass(Notification.class);
        verify(notifications).insert(saved.capture());
        Notification n=saved.getValue();
        assertThat(n.getUserId()).isEqualTo(99L);
        assertThat(n.getRelatedId()).isEqualTo(10L);
        assertThat(n.getTitle()).isEqualTo("小明收藏了你的商品");
        assertThat(n.getContent()).isEqualTo("小明收藏了「台灯」");
        assertThat(ReflectionTestUtils.getField(n,"actorUserId")).isEqualTo(7L);
        assertThat(ReflectionTestUtils.getField(n,"actorNickname")).isEqualTo("小明");
        assertThat(ReflectionTestUtils.getField(n,"actorAvatar")).isEqualTo("/uploads/avatar.png");
        ArgumentCaptor<String> payload=ArgumentCaptor.forClass(String.class);
        verify(socket).sendToUser(eq(99L),payload.capture());
        var data=JSONUtil.parseObj(payload.getValue()).getJSONObject("data");
        assertThat(data.getLong("actorUserId")).isEqualTo(7L);
        assertThat(data.getStr("actorNickname")).isEqualTo("小明");
        assertThat(payload.getValue()).doesNotContain("private-password-fixture","private-phone-fixture","private-email-fixture");
    }

    @Test
    void missingUserFallsBackToGenericHistoricalNotificationWithoutFakeProfileLink() {
        runner.run(context->send(context.getBean(NotificationServiceImpl.class)));
        ArgumentCaptor<Notification> saved=ArgumentCaptor.forClass(Notification.class);
        verify(notifications).insert(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("有人收藏了你的商品");
        assertThat(ReflectionTestUtils.getField(saved.getValue(),"actorUserId")).isNull();
    }

    @Test
    void nicknameFallbackDoesNotExposeLoginUsername() {
        User user=new User();user.setId(7L);user.setNickname(" ");user.setUsername("private-login-name");
        when(users.selectById(7L)).thenReturn(user);
        runner.run(context->send(context.getBean(NotificationServiceImpl.class)));
        ArgumentCaptor<Notification> saved=ArgumentCaptor.forClass(Notification.class);
        verify(notifications).insert(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("校园用户收藏了你的商品");
    }

    @Test
    void historicalNotificationWithoutActorStillLoadsNormally() {
        Notification old=new Notification();old.setId(1L);old.setType("favorite");old.setTitle("有人收藏了你的商品");
        Page<Notification> page=new Page<>(1,10);page.setTotal(1);page.setRecords(List.of(old));
        when(notifications.selectPage(any(),any())).thenReturn(page);
        runner.run(context->{
            var result=context.getBean(NotificationServiceImpl.class).getMyNotifications(99L,1,10);
            assertThat(result.getCode()).isEqualTo(200);
            assertThat(ReflectionTestUtils.getField(old,"actorUserId")).isNull();
        });
        verifyNoInteractions(users);
    }
}
