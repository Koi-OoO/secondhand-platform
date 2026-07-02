package com.secondhand.controller;

import com.secondhand.entity.User;
import com.secondhand.service.UserService;
import com.secondhand.util.Result;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void publicProfileReturnsUserWithoutPassword() {
        UserService userService = mock(UserService.class);
        User user = new User();
        user.setId(6L);
        user.setUsername("seller01");
        user.setNickname("卖家01");
        user.setPassword("hashed-password");
        user.setPhone("13800138000");
        user.setEmail("seller@example.com");
        user.setCreditScore(96);

        when(userService.getUserById(6L)).thenReturn(user);

        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);

        Result result = controller.publicProfile(6L);

        assertThat(result.getCode()).isEqualTo(200);
        User data = (User) result.getData();
        assertThat(data.getId()).isEqualTo(6L);
        assertThat(data.getNickname()).isEqualTo("卖家01");
        assertThat(data.getPassword()).isNull();
        assertThat(data.getPhone()).isNull();
        assertThat(data.getEmail()).isNull();
    }
}
