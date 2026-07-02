package com.secondhand.controller;

import com.secondhand.entity.User;
import com.secondhand.service.UserService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块控制器
 */
@RestController  // 标识为REST控制器，所有方法返回JSON格式数据
@RequestMapping("/user")  // 该控制器的请求路径前缀为 /user
@Tag(name = "用户管理", description = "提供注册、登录、退出和个人资料维护等接口")  // Swagger文档分组标签
public class UserController {

    @Autowired  // 自动注入UserService
    private UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")  // 处理POST请求，路径为 /user/login
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，成功后创建会话")  // Swagger接口说明
    public Result login(@RequestBody User user, HttpSession session) {
        // 调用service层校验登录
        Result r = userService.login(user.getUsername(), user.getPassword());
        // 登录成功（code=200）
        if (r.getCode() == 200) {
            // 获取完整用户信息
            User u = userService.getUserByUsername(user.getUsername());
            // 将用户信息存入session
            SessionUtil.login(session, u);
        }
        // 返回登录结果
        return r;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")  // 处理POST请求，路径为 /user/register
    @Operation(summary = "用户注册", description = "创建普通用户账号")
    public Result register(@RequestBody User user) {  // @RequestBody接收JSON请求体
        // 直接调用service层注册
        return userService.register(user);
    }

    /**
     * 查询当前登录用户资料
     */
    @GetMapping("/profile")  // 处理GET请求，路径为 /user/profile
    @Operation(summary = "查询当前用户资料", description = "需要登录，返回当前用户最新的个人资料")
    public Result profile(HttpSession session) {
        // 从session中获取当前用户
        User user = SessionUtil.getUser(session);
        // 未登录
        if (user == null) {
            return Result.error("未登录");
        }
        // 查询最新用户信息（防止session中的信息已过期）
        User latest = userService.getUserById(user.getId());
        // 用户不存在
        if (latest == null) {
            return Result.error("用户不存在");
        }
        // 清空密码，防止返回前端
        latest.setPassword(null);
        // 返回用户信息
        return Result.success(latest);
    }

    /**
     * 查询公开用户资料（用于商品详情页、用户主页等）
     */
    @GetMapping("/public/{userId}")  // 处理GET请求，路径为 /user/public/{userId}
    @Operation(summary = "查询公开用户资料", description = "公开接口，返回用户主页展示所需的资料信息")
    public Result publicProfile(@PathVariable Long userId) {  // @PathVariable从路径中获取userId
        // 校验userId是否有效
        if (userId == null || userId <= 0) {
            return Result.error("用户 ID 无效");
        }
        // 查询用户
        User user = userService.getUserById(userId);
        // 用户不存在
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 清空敏感信息（密码、手机、邮箱）
        user.setPassword(null);
        user.setPhone(null);
        user.setEmail(null);
        return Result.success(user);
    }

    /**
     * 更新当前登录用户资料
     */
    @PutMapping("/update")  // 处理PUT请求，路径为 /user/update
    @Operation(summary = "更新个人资料", description = "需要登录，支持更新昵称、头像、手机号、邮箱、地址等字段")
    public Result update(@RequestBody User user, HttpSession session) {
        // 从session中获取用户ID（不信任前端传入的ID，防止越权）
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 设置用户ID（从session获取的才是真实的）
        user.setId(userId);
        // 防止前端传入密码或用户名，强制设为null
        user.setPassword(null);
        user.setUsername(null);
        // 调用service更新
        boolean ok = userService.updateById(user);
        // 更新成功
        if (ok) {
            // 查询最新用户信息
            User latest = userService.getUserById(userId);
            // 更新session中的用户信息
            SessionUtil.login(session, latest);
            return Result.success("更新成功");
        }
        // 更新失败
        return Result.error("更新失败");
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")  // 处理POST请求，路径为 /user/logout
    @Operation(summary = "退出登录", description = "需要登录，清空当前会话")
    public Result logout(HttpSession session) {
        // 清空session中的用户信息
        SessionUtil.logout(session);
        // 返回成功
        return Result.success("已退出");
    }
}