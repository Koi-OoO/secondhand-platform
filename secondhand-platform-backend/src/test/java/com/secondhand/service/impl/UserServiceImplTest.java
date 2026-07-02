package com.secondhand.service.impl;

import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.util.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @Test
    void registerGeneratesDefaultNicknameWhenMissing() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectByUsername("new_user")).thenReturn(null);

        User user = new User();
        user.setUsername("new_user");
        user.setPassword("123456");
        user.setNickname("  ");

        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);

        Result result = service.register(user);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getNickname())
                .isNotBlank()
                .matches(".*\\d{6}");
    }
}
