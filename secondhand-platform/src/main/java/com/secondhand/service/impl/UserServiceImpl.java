package com.secondhand.service.impl;

import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.UserService;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.Result;
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
     *
     * 业务流程：
     * 1. 根据用户名查询用户
     * 2. 判断用户是否存在
     * 3. 校验密码是否正确
     * 4. 返回对应的登录结果
     */
    @Override
    public Result login(String username, String password) {
        // 先按用户名查出用户，再校验密码是否正确
        User user = userMapper.selectByUsername(username);

        // 用户不存在或密码错误统一返回同一提示，避免暴露账号是否已注册（防枚举）
        if (user == null || !PasswordUtil.verify(password, user.getPassword())) {
            return Result.error("账号或密码错误");
        }

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
     *
     * 业务流程：
     * 1. 先检查用户名是否已存在
     * 2. 对密码进行加密
     * 3. 如果没有填写昵称，则自动生成默认昵称
     * 4. 设置用户启用状态
     * 5. 写入数据库
     */
    @Override
    public Result register(User user) {
        // 先检查用户名是否已存在，避免重复注册
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            return Result.error("用户名已存在");
        }

        // 注册时统一对密码加密后再落库
        user.setPassword(PasswordUtil.encrypt(user.getPassword()));

        // 处理昵称：未填写时生成默认昵称，避免前端展示空值
        if (user.getNickname() == null || user.getNickname().trim().isEmpty()) {
            user.setNickname(generateDefaultNickname());
        } else {
            // 去掉昵称首尾空白，避免脏数据入库
            user.setNickname(user.getNickname().trim());
        }

        // 新用户默认设置为启用状态
        user.setStatus(1);

        // 写入用户数据到数据库
        userMapper.insert(user);

        return Result.success("注册成功");
    }

    /**
     * 根据用户 ID 查询用户信息。
     */
    @Override
    public User getUserById(Long userId) {
        // 参数校验：用户 ID 不能为空
        if (userId == null) {
            return null;
        }
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
        // 参数校验：用户对象和用户 ID 不能为空
        if (user == null || user.getId() == null) {
            return false;
        }

        // 返回受影响行数，大于 0 说明更新成功
        return userMapper.updateById(user) > 0;
    }
}
