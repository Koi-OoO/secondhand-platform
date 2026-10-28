package com.secondhand.service.impl;

import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.UserService;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.Result;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类。
 * 负责封装用户模块的核心业务逻辑，包括登录校验、注册入库、用户查询和资料更新。
 */
@Service
public class UserServiceImpl implements UserService {

    /**
     * 用户数据访问对象。
     * 由 MyBatis Plus 注入，用于执行数据库操作。
     */
    @Autowired
    private UserMapper userMapper;

    /**
     * 校验用户名和密码，返回登录结果。
     */
    @Override
    public Result login(String username, String password) {
        // 1. 校验账号和密码是否填写
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return Result.error("账号或密码错误");
        }

        // 2. 查询用户，校验账号状态和密码
        // 先按用户名查出用户，再校验密码是否正确
        User user = userMapper.selectByUsername(username);

        // 用户不存在或密码错误统一返回同一提示，避免暴露账号是否已注册（防枚举）
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || !PasswordUtil.verify(password, user.getPassword())) {
            return Result.error("账号或密码错误");
        }

        // 3. 返回登录校验结果，由控制器建立会话
        // 校验通过，返回登录成功
        return Result.success("登录成功");
    }

    /**
     * 按用户名查询用户信息。
     *
     * 常用于登录后刷新用户资料、编辑资料前回显等场景。
     */
    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /**
     * 注册新用户，并补齐默认昵称与加密密码。
     */
    @Override
    public Result register(User user) {
        // 1. 检查用户名是否已注册
        // 先检查用户名是否已存在，避免重复注册
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            return Result.error("用户名已存在");
        }

        // 2. 规范化邮箱，校验格式和占用情况
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                return Result.error("邮箱格式不正确");
            }
            if (userMapper.selectByEmail(email) != null) {
                return Result.error("邮箱已被绑定");
            }
            user.setEmail(email);
        } else {
            user.setEmail(null);
        }

        // 3. 对密码进行哈希处理，不保存明文密码
        // 注册时统一对密码加密后再落库
        user.setPassword(PasswordUtil.encrypt(user.getPassword()));

        // 4. 整理昵称，未填写时生成默认昵称
        // 处理昵称：未填写时生成默认昵称，避免前端展示空值
        if (user.getNickname() == null || user.getNickname().trim().isEmpty()) {
            user.setNickname(generateDefaultNickname());
        } else {
            // 去掉昵称首尾空白，避免脏数据入库
            user.setNickname(user.getNickname().trim());
        }

        // 5. 设置账号状态，邮箱仍需通过安全流程验证
        // 新用户默认设置为启用状态
        user.setStatus(1);

        // 邮箱即使填写，也必须通过账号安全流程验证
        user.setEmailVerified(0);

        // 6. 保存用户并返回注册结果
        // 写入用户数据到数据库
        userMapper.insert(user);

        return Result.success("注册成功");
    }

    /**
     * 根据用户 ID 查询用户信息。
     */
    @Override
    public User getUserById(Long userId) {
        // 1. 校验用户ID
        // 参数校验：用户 ID 不能为空
        if (userId == null) {
            return null;
        }

        // 2. 查询用户基础信息
        return userMapper.selectById(userId);
    }

    /**
     * 用户未填写昵称时生成默认昵称。
     *
     * 规则：固定前缀“闲置用户” + 6 位随机数字。
     */
    private String generateDefaultNickname() {
        // 生成 100000-999999 之间的随机数作为后缀
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "闲置用户" + suffix;
    }

    /**
     * 根据主键更新用户资料。
     */
    @Override
    public boolean updateById(User user) {
        // 1. 校验用户对象和ID
        // 参数校验：用户对象和用户 ID 不能为空
        if (user == null || user.getId() == null) {
            return false;
        }

        // 2. 更新资料，根据受影响行数返回结果
        // 返回受影响行数，大于 0 说明更新成功
        return userMapper.updateById(user) > 0;
    }
}
