package com.secondhand.service.impl;

import com.secondhand.entity.PasswordResetConfirmRequest;
import com.secondhand.entity.PasswordResetRequest;
import com.secondhand.entity.PasswordResetVerifyRequest;
import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.EmailService;
import com.secondhand.service.PasswordResetService;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.Result;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final long CODE_TTL_SECONDS = 600;
    private static final long RESEND_TTL_SECONDS = 60;
    private static final long TOKEN_TTL_SECONDS = 600;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CacheUtil cacheUtil;

    @Autowired
    private EmailService emailService;

    @Override
    public Result requestCode(PasswordResetRequest request) {
        // 1. 查询用户名和邮箱匹配的账号
        String username = normalize(request == null ? null : request.getUsername());
        String email = normalizeEmail(request == null ? null : request.getEmail());
        User user = userMapper.selectByUsernameAndEmail(username, email);
        String genericMessage = "如果账号绑定了该邮箱，验证码已发送";

        // 2. 无匹配账号或未验证邮箱时也返回统一提示，避免泄露账号信息
        if (user == null || email.isEmpty() || !Integer.valueOf(1).equals(user.getEmailVerified())) {
            return Result.success(genericMessage);
        }

        // 3. 检查验证码发送间隔
        String resendKey = resendKey(username);
        if (cacheUtil.get(resendKey) != null) {
            return Result.error("验证码发送过于频繁，请稍后再试");
        }

        // 4. 生成验证码并保存哈希，再向绑定邮箱发送
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        cacheUtil.set(codeKey(username), PasswordUtil.encrypt(code), CODE_TTL_SECONDS, 0);
        cacheUtil.set(resendKey, "1", RESEND_TTL_SECONDS, 0);
        emailService.sendPasswordResetCode(email, code);
        return Result.success(genericMessage);
    }

    @Override
    public Result verifyCode(PasswordResetVerifyRequest request) {
        // 1. 查询账号，校验验证码格式
        String username = normalize(request == null ? null : request.getUsername());
        String email = normalizeEmail(request == null ? null : request.getEmail());
        String code = normalize(request == null ? null : request.getCode());
        User user = userMapper.selectByUsernameAndEmail(username, email);
        if (user == null || !code.matches("\\d{6}")) {
            return Result.error("验证码错误或已过期");
        }

        // 2. 核对验证码，错误时累计失败次数，达到上限删除验证码
        String codeKey = codeKey(username);
        Object encryptedCode = cacheUtil.get(codeKey);
        if (!(encryptedCode instanceof String) || !PasswordUtil.verify(code, (String) encryptedCode)) {
            String attemptsKey = attemptsKey(username);
            Object attempts = cacheUtil.get(attemptsKey);
            int count = attempts instanceof Number ? ((Number) attempts).intValue() : 0;
            count++;
            cacheUtil.set(attemptsKey, count, CODE_TTL_SECONDS, 0);
            if (count >= MAX_VERIFY_ATTEMPTS) {
                cacheUtil.delete(codeKey);
            }
            return Result.error("验证码错误或已过期");
        }

        // 3. 生成一次性重置凭证并保存对应用户ID
        String token = UUID.randomUUID().toString().replace("-", "");
        cacheUtil.set(tokenKey(token), user.getId().toString(), TOKEN_TTL_SECONDS, 0);

        // 4. 清理已使用的验证码和失败计数，返回重置凭证
        cacheUtil.delete(codeKey);
        cacheUtil.delete(attemptsKey(username));
        return Result.success(token);
    }

    @Override
    public Result resetPassword(PasswordResetConfirmRequest request) {
        // 1. 校验重置凭证和新密码长度
        String token = normalize(request == null ? null : request.getResetToken());
        String newPassword = request == null ? null : request.getNewPassword();
        if (token.isEmpty() || newPassword == null || newPassword.length() < 6 || newPassword.length() > 64) {
            return Result.error("新密码长度需为 6-64 位");
        }

        // 2. 读取凭证对应的用户ID，凭证不存在则提示失效
        Object userIdValue = cacheUtil.get(tokenKey(token));
        if (userIdValue == null) {
            return Result.error("重置链接已失效，请重新申请");
        }

        // 3. 查询用户，对新密码进行哈希处理后保存
        User user = userMapper.selectById(Long.valueOf(String.valueOf(userIdValue)));
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(PasswordUtil.encrypt(newPassword));
        if (userMapper.updateById(user) != 1) {
            return Result.error("密码更新失败，请稍后再试");
        }

        // 4. 删除已使用的重置凭证，返回结果
        cacheUtil.delete(tokenKey(token));
        return Result.success("密码重置成功，请重新登录");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private String codeKey(String username) {
        return "password:reset:code:" + username;
    }

    private String attemptsKey(String username) {
        return "password:reset:attempts:" + username;
    }

    private String resendKey(String username) {
        return "password:reset:resend:" + username;
    }

    private String tokenKey(String token) {
        return "password:reset:token:" + token;
    }
}
