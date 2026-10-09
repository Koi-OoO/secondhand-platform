package com.secondhand.service.impl;

import com.secondhand.entity.ChangePasswordRequest;
import com.secondhand.entity.EmailBindRequest;
import com.secondhand.entity.EmailBindVerifyRequest;
import com.secondhand.entity.EmailUnbindRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityServiceImplTest {

    @Test
    void requestEmailBindCodeSendsCodeToNewEmail() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", null);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(null);

        AccountSecurityServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.requestEmailBindCode(7L, new EmailBindRequest("alice@example.com"));

        assertThat(result.getCode()).isEqualTo(200);
        verify(emailService).sendEmailBindCode(anyString(), anyString());
        verify(cacheUtil, org.mockito.Mockito.times(3)).set(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void verifyEmailBindCodeMarksEmailAsVerified() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", null);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(null);
        when(cacheUtil.get("account:email:bind:code:7")).thenReturn(PasswordUtil.encrypt("123456"));
        when(cacheUtil.get("account:email:bind:email:7")).thenReturn("alice@example.com");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        AccountSecurityServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.verifyEmailBindCode(7L,
                new EmailBindVerifyRequest("alice@example.com", "123456"));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getEmailVerified()).isEqualTo(1);
        verify(cacheUtil).delete("account:email:bind:code:7");
    }

    @Test
    void requestEmailBindCodeRejectsEmailOwnedByAnotherUser() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        when(userMapper.selectById(7L)).thenReturn(user(7L, "alice", null));
        when(userMapper.selectByEmail("taken@example.com")).thenReturn(user(8L, "bob", "taken@example.com"));

        AccountSecurityServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.requestEmailBindCode(7L, new EmailBindRequest("taken@example.com"));

        assertThat(result.getCode()).isEqualTo(500);
        verify(emailService, never()).sendEmailBindCode(anyString(), anyString());
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", null);
        user.setPassword(PasswordUtil.encrypt("old-password"));
        when(userMapper.selectById(7L)).thenReturn(user);

        AccountSecurityServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.changePassword(7L,
                new ChangePasswordRequest("wrong-password", "new-password"));

        assertThat(result.getCode()).isEqualTo(500);
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void requestEmailUnbindCodeSendsCodeToCurrentVerifiedEmail() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", "alice@example.com");
        when(userMapper.selectById(7L)).thenReturn(user);

        AccountSecurityServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.requestEmailUnbindCode(7L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(emailService).sendEmailUnbindCode(anyString(), anyString());
        verify(cacheUtil, org.mockito.Mockito.times(3)).set(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void unbindEmailClearsEmailAfterPasswordAndCodeVerification() {
        UserMapper userMapper = mock(UserMapper.class);
        CacheUtil cacheUtil = mock(CacheUtil.class);
        EmailService emailService = mock(EmailService.class);
        User user = user(7L, "alice", "alice@example.com");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(cacheUtil.get("account:email:unbind:code:7")).thenReturn(PasswordUtil.encrypt("123456"));
        when(cacheUtil.get("account:email:unbind:email:7")).thenReturn("alice@example.com");
        when(userMapper.clearEmailById(7L)).thenReturn(1);

        AccountSecurityServiceImpl service = service(userMapper, cacheUtil, emailService);
        Result result = service.unbindEmail(7L, new EmailUnbindRequest("123456"));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getEmailVerified()).isEqualTo(0);
        verify(userMapper).clearEmailById(7L);
        verify(cacheUtil).delete("account:email:unbind:code:7");
    }

    private AccountSecurityServiceImpl service(UserMapper userMapper, CacheUtil cacheUtil, EmailService emailService) {
        AccountSecurityServiceImpl service = new AccountSecurityServiceImpl();
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
        user.setEmailVerified(email == null ? 0 : 1);
        return user;
    }
}
