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
        // 1. 查询当前用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 2. 返回账号、邮箱和密码设置状态，不返回密码内容
        return Result.success(new AccountSecurityInfo(
                user.getUsername(), user.getEmail(), user.getEmailVerified(), user.getPassword() != null));
    }

    @Override
    public Result requestEmailBindCode(Long userId, EmailBindRequest request) {
        // 1. 规范化并校验待绑定邮箱
        String email = normalizeEmail(request == null ? null : request.getEmail());
        if (!isValidEmail(email)) {
            return Result.error("请输入正确的邮箱地址");
        }

        // 2. 校验用户及邮箱占用情况
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

        // 3. 检查发送间隔，防止频繁发送
        String resendKey = resendKey(userId);
        if (cacheUtil.get(resendKey) != null) {
            return Result.error("验证码发送过于频繁，请稍后再试");
        }

        // 4. 生成验证码，保存验证码哈希和待验证邮箱
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        cacheUtil.set(codeKey(userId), PasswordUtil.encrypt(code), CODE_TTL_SECONDS, 0);
        cacheUtil.set(emailKey(userId), email, CODE_TTL_SECONDS, 0);
        cacheUtil.set(resendKey, "1", RESEND_TTL_SECONDS, 0);

        // 5. 发送验证码并返回结果
        emailService.sendEmailBindCode(email, code);
        return Result.success("验证码已发送");
    }

    @Override
    public Result verifyEmailBindCode(Long userId, EmailBindVerifyRequest request) {
        // 1. 校验邮箱、验证码格式及有效性
        String email = normalizeEmail(request == null ? null : request.getEmail());
        String code = normalize(request == null ? null : request.getCode());
        Object expectedEmail = cacheUtil.get(emailKey(userId));
        Object encryptedCode = cacheUtil.get(codeKey(userId));
        if (!email.equals(expectedEmail) || !(encryptedCode instanceof String)
                || !code.matches("\\d{6}") || !PasswordUtil.verify(code, (String) encryptedCode)) {
            return Result.error("验证码错误或已过期");
        }

        // 2. 再次确认用户和邮箱归属，避免绑定到已被占用的邮箱
        User user = userMapper.selectById(userId);
        User owner = userMapper.selectByEmail(email);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (owner != null && !userId.equals(owner.getId())) {
            return Result.error("该邮箱已被其他账号绑定");
        }

        // 3. 保存邮箱，将邮箱标记为已验证
        user.setEmail(email);
        user.setEmailVerified(1);
        if (userMapper.updateById(user) != 1) {
            return Result.error("邮箱绑定失败，请稍后再试");
        }

        // 4. 清理本次验证数据并返回结果
        clearEmailBindKeys(userId);
        return Result.success("邮箱绑定成功");
    }

    @Override
    public Result requestEmailUnbindCode(Long userId) {
        // 1. 确认用户存在且有已验证邮箱
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getEmailVerified()) || !isValidEmail(normalizeEmail(user.getEmail()))) {
            return Result.error("当前没有已验证邮箱");
        }

        // 2. 检查解绑验证码的发送间隔
        String resendKey = unbindResendKey(userId);
        if (cacheUtil.get(resendKey) != null) {
            return Result.error("验证码发送过于频繁，请稍后再试");
        }

        // 3. 生成验证码，保存验证码哈希和当前邮箱
        String email = normalizeEmail(user.getEmail());
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        cacheUtil.set(unbindCodeKey(userId), PasswordUtil.encrypt(code), CODE_TTL_SECONDS, 0);
        cacheUtil.set(unbindEmailKey(userId), email, CODE_TTL_SECONDS, 0);
        cacheUtil.set(resendKey, "1", RESEND_TTL_SECONDS, 0);

        // 4. 向当前邮箱发送验证码
        emailService.sendEmailUnbindCode(email, code);
        return Result.success("验证码已发送");
    }

    @Override
    public Result unbindEmail(Long userId, EmailUnbindRequest request) {
        // 1. 获取验证码，确认当前已验证邮箱
        String code = normalize(request == null ? null : request.getCode());
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getEmailVerified()) || !isValidEmail(normalizeEmail(user.getEmail()))) {
            return Result.error("当前没有已验证邮箱");
        }

        // 2. 核对验证码及邮箱，防止使用其他邮箱的验证码
        Object expectedEmail = cacheUtil.get(unbindEmailKey(userId));
        Object encryptedCode = cacheUtil.get(unbindCodeKey(userId));
        String currentEmail = normalizeEmail(user.getEmail());
        if (!currentEmail.equals(expectedEmail) || !(encryptedCode instanceof String)
                || !code.matches("\\d{6}") || !PasswordUtil.verify(code, (String) encryptedCode)) {
            return Result.error("验证码错误或已过期");
        }

        // 3. 清空邮箱并取消验证标记
        // 显式 SQL 置空邮箱：MyBatis-Plus 的 updateById 会跳过 null 字段，无法清空邮箱列
        if (userMapper.clearEmailById(userId) != 1) {
            return Result.error("邮箱解绑失败，请稍后再试");
        }
        user.setEmail(null);
        user.setEmailVerified(0);

        // 4. 清理本次验证数据并返回结果
        clearEmailUnbindKeys(userId);
        return Result.success("邮箱解绑成功");
    }

    @Override
    public Result changePassword(Long userId, ChangePasswordRequest request) {
        // 1. 获取旧密码和新密码，校验新密码长度
        String oldPassword = request == null ? null : request.getOldPassword();
        String newPassword = request == null ? null : request.getNewPassword();
        if (oldPassword == null || newPassword == null || newPassword.length() < 6 || newPassword.length() > 64) {
            return Result.error("新密码长度需为 6-64 位");
        }

        // 2. 查询用户并核对旧密码
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!PasswordUtil.verify(oldPassword, user.getPassword())) {
            return Result.error("当前密码错误");
        }

        // 3. 哈希处理新密码并保存
        user.setPassword(PasswordUtil.encrypt(newPassword));
        if (userMapper.updateById(user) != 1) {
            return Result.error("密码修改失败，请稍后再试");
        }

        // 4. 返回修改结果
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
