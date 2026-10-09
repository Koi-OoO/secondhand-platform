package com.secondhand.service.impl;

import com.secondhand.entity.PasswordResetConfirmRequest;
import com.secondhand.entity.PasswordResetRequest;
import com.secondhand.entity.PasswordResetVerifyRequest;
import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.EmailService;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.Result;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceImplTest {

    @Test
    void requestCodeSendsEmailOnlyWhenUsernameAndEmailMatch() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", "alice@example.com");
        user.setEmailVerified(1);
        when(userMapper.selectByUsernameAndEmail("alice", "alice@example.com")).thenReturn(user);

        PasswordResetServiceImpl service = service(userMapper, cacheUtil, emailService);
        PasswordResetRequest request = new PasswordResetRequest("alice", "alice@example.com");

        Result result = service.requestCode(request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(emailService).sendPasswordResetCode(anyString(), anyString());
        verify(cacheUtil, org.mockito.Mockito.times(2)).set(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void verifyCodeReturnsOneTimeResetTokenAndDeletesCode() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", "alice@example.com");
        user.setEmailVerified(1);
        when(userMapper.selectByUsernameAndEmail("alice", "alice@example.com")).thenReturn(user);
        when(cacheUtil.get("password:reset:code:alice")).thenReturn(PasswordUtil.encrypt("123456"));

        PasswordResetServiceImpl service = service(userMapper, cacheUtil, emailService);
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest("alice", "alice@example.com", "123456");

        Result result = service.verifyCode(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isInstanceOf(String.class);
        verify(cacheUtil).delete("password:reset:code:alice");
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        when(cacheUtil.get("password:reset:token:used-token")).thenReturn(null);

        PasswordResetServiceImpl service = service(userMapper, cacheUtil, emailService);
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("used-token", "new-password");

        Result result = service.resetPassword(request);

        assertThat(result.getCode()).isEqualTo(500);
        verify(userMapper, org.mockito.Mockito.never()).updateById(any(User.class));
    }

    @Test
    void requestCodeRejectsUnverifiedEmail() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", "alice@example.com");
        user.setEmailVerified(0);
        when(userMapper.selectByUsernameAndEmail("alice", "alice@example.com")).thenReturn(user);

        PasswordResetServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.requestCode(new PasswordResetRequest("alice", "alice@example.com"));

        assertThat(result.getCode()).isEqualTo(200);
        verify(emailService, org.mockito.Mockito.never()).sendPasswordResetCode(anyString(), anyString());
    }

    private PasswordResetServiceImpl service(UserMapper userMapper, CacheUtil cacheUtil, EmailService emailService) {
        PasswordResetServiceImpl service = new PasswordResetServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "cacheUtil", cacheUtil);
        ReflectionTestUtils.setField(service, "emailService", emailService);
        return service;
    }

    private User user(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(1);
        user.setPassword("old-password");
        return user;
    }
}
