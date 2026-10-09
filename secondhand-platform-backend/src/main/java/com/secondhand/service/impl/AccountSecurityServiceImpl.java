package com.secondhand.service.impl;

import com.secondhand.entity.AccountSecurityInfo;
import com.secondhand.entity.ChangePasswordRequest;
import com.secondhand.entity.EmailBindRequest;
import com.secondhand.entity.EmailBindVerifyRequest;
import com.secondhand.entity.EmailUnbindRequest;
import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.AccountSecurityService;
import com.secondhand.service.EmailService;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.Result;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountSecurityServiceImpl implements AccountSecurityService {

    private static final long CODE_TTL_SECONDS = 600;
    private static final long RESEND_TTL_SECONDS = 60;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CacheUtil cacheUtil;

    @Autowired
    private EmailService emailService;

    @Override
    public Result getSecurityInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(new AccountSecurityInfo(
                user.getUsername(), user.getEmail(), user.getEmailVerified(), user.getPassword() != null));
    }

    @Override
    public Result requestEmailBindCode(Long userId, EmailBindRequest request) {
        String email = normalizeEmail(request == null ? null : request.getEmail());
        if (!isValidEmail(email)) {
            return Result.error("请输入正确的邮箱地址");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (email.equalsIgnoreCase(user.getEmail()) && Integer.valueOf(1).equals(user.getEmailVerified())) {
            return Result.error("该邮箱已经绑定");
        }

        User owner = userMapper.selectByEmail(email);
        if (owner != null && !userId.equals(owner.getId())) {
            return Result.error("该邮箱已被其他账号绑定");
        }

        String resendKey = resendKey(userId);
        if (cacheUtil.get(resendKey) != null) {
            return Result.error("验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        cacheUtil.set(codeKey(userId), PasswordUtil.encrypt(code), CODE_TTL_SECONDS, 0);
        cacheUtil.set(emailKey(userId), email, CODE_TTL_SECONDS, 0);
        cacheUtil.set(resendKey, "1", RESEND_TTL_SECONDS, 0);
        emailService.sendEmailBindCode(email, code);
        return Result.success("验证码已发送");
    }

    @Override
    public Result verifyEmailBindCode(Long userId, EmailBindVerifyRequest request) {
        String email = normalizeEmail(request == null ? null : request.getEmail());
        String code = normalize(request == null ? null : request.getCode());
        Object expectedEmail = cacheUtil.get(emailKey(userId));
        Object encryptedCode = cacheUtil.get(codeKey(userId));
        if (!email.equals(expectedEmail) || !(encryptedCode instanceof String)
                || !code.matches("\\d{6}") || !PasswordUtil.verify(code, (String) encryptedCode)) {
            return Result.error("验证码错误或已过期");
        }

        User user = userMapper.selectById(userId);
        User owner = userMapper.selectByEmail(email);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (owner != null && !userId.equals(owner.getId())) {
            return Result.error("该邮箱已被其他账号绑定");
        }

        user.setEmail(email);
        user.setEmailVerified(1);
        if (userMapper.updateById(user) != 1) {
            return Result.error("邮箱绑定失败，请稍后再试");
        }
        clearEmailBindKeys(userId);
        return Result.success("邮箱绑定成功");
    }

    @Override
    public Result requestEmailUnbindCode(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getEmailVerified()) || !isValidEmail(normalizeEmail(user.getEmail()))) {
            return Result.error("当前没有已验证邮箱");
        }

        String resendKey = unbindResendKey(userId);
        if (cacheUtil.get(resendKey) != null) {
            return Result.error("验证码发送过于频繁，请稍后再试");
        }

        String email = normalizeEmail(user.getEmail());
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        cacheUtil.set(unbindCodeKey(userId), PasswordUtil.encrypt(code), CODE_TTL_SECONDS, 0);
        cacheUtil.set(unbindEmailKey(userId), email, CODE_TTL_SECONDS, 0);
        cacheUtil.set(resendKey, "1", RESEND_TTL_SECONDS, 0);
        emailService.sendEmailUnbindCode(email, code);
        return Result.success("验证码已发送");
    }

    @Override
    public Result unbindEmail(Long userId, EmailUnbindRequest request) {
        String code = normalize(request == null ? null : request.getCode());
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getEmailVerified()) || !isValidEmail(normalizeEmail(user.getEmail()))) {
            return Result.error("当前没有已验证邮箱");
        }
        Object expectedEmail = cacheUtil.get(unbindEmailKey(userId));
        Object encryptedCode = cacheUtil.get(unbindCodeKey(userId));
        String currentEmail = normalizeEmail(user.getEmail());
        if (!currentEmail.equals(expectedEmail) || !(encryptedCode instanceof String)
                || !code.matches("\\d{6}") || !PasswordUtil.verify(code, (String) encryptedCode)) {
            return Result.error("验证码错误或已过期");
        }

        // 显式 SQL 置空邮箱：MyBatis-Plus 的 updateById 会跳过 null 字段，无法清空邮箱列
        if (userMapper.clearEmailById(userId) != 1) {
            return Result.error("邮箱解绑失败，请稍后再试");
        }
        user.setEmail(null);
        user.setEmailVerified(0);
        clearEmailUnbindKeys(userId);
        return Result.success("邮箱解绑成功");
    }

    @Override
    public Result changePassword(Long userId, ChangePasswordRequest request) {
        String oldPassword = request == null ? null : request.getOldPassword();
        String newPassword = request == null ? null : request.getNewPassword();
        if (oldPassword == null || newPassword == null || newPassword.length() < 6 || newPassword.length() > 64) {
            return Result.error("新密码长度需为 6-64 位");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!PasswordUtil.verify(oldPassword, user.getPassword())) {
            return Result.error("当前密码错误");
        }

        user.setPassword(PasswordUtil.encrypt(newPassword));
        if (userMapper.updateById(user) != 1) {
            return Result.error("密码修改失败，请稍后再试");
        }
        return Result.success("密码修改成功");
    }

    private void clearEmailBindKeys(Long userId) {
        cacheUtil.delete(codeKey(userId));
        cacheUtil.delete(emailKey(userId));
        cacheUtil.delete(resendKey(userId));
    }

    private void clearEmailUnbindKeys(Long userId) {
        cacheUtil.delete(unbindCodeKey(userId));
        cacheUtil.delete(unbindEmailKey(userId));
        cacheUtil.delete(unbindResendKey(userId));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private String codeKey(Long userId) {
        return "account:email:bind:code:" + userId;
    }

    private String emailKey(Long userId) {
        return "account:email:bind:email:" + userId;
    }

    private String resendKey(Long userId) {
        return "account:email:bind:resend:" + userId;
    }

    private String unbindCodeKey(Long userId) {
        return "account:email:unbind:code:" + userId;
    }

    private String unbindEmailKey(Long userId) {
        return "account:email:unbind:email:" + userId;
    }

    private String unbindResendKey(Long userId) {
        return "account:email:unbind:resend:" + userId;
    }
}
