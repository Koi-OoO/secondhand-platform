package com.secondhand.service;

import com.secondhand.entity.User;
import com.secondhand.util.Result;

/**
 * 用户服务接口
 * 定义用户模块对外提供的核心业务能力，
 * 供控制器层调用，具体实现由 `UserServiceImpl` 完成。
 */
public interface UserService {

    /**
     * 校验用户名和密码
     *
     * 说明：该方法只负责账号合法性验证，不直接写入 Session，
     * 登录态由控制器在校验成功后统一管理。
     *
     * @param username 用户名
     * @param password 原始密码
     * @return Result 登录校验结果
     */
    Result login(String username, String password);

    /**
     * 根据用户名查询用户信息
     *
     * @param username 用户名
     * @return User 用户实体，不存在时返回 null
     */
    User getUserByUsername(String username);

    /**
     * 注册新用户
     *
     * @param user 用户注册信息
     * @return Result 注册结果
     */
    Result register(User user);

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param userId 用户 ID
     * @return User 用户实体，不存在时返回 null
     */
    User getUserById(Long userId);

    /**
     * 根据主键更新用户信息
     *
     * @param user 待更新的用户对象
     * @return boolean 更新成功返回 true，否则返回 false
     */
    boolean updateById(User user);
}
