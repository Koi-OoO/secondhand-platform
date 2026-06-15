package com.secondhand.util;

import com.secondhand.entity.User;
import javax.servlet.http.HttpSession;

/**
 * Session工具类（封装用户登录会话操作）
 */
public class SessionUtil {

    // Session中存储当前用户的key
    private static final String USER_KEY = "currentUser";

    /**
     * 用户登录：将用户信息存入Session
     */
    public static void login(HttpSession session, User user) {
        // 清空密码，避免敏感信息存入Session
        user.setPassword(null);
        // 将用户对象存入Session
        session.setAttribute(USER_KEY, user);
    }

    /**
     * 用户退出：销毁Session
     */
    public static void logout(HttpSession session) {
        // 使Session失效
        session.invalidate();
    }

    /**
     * 获取当前登录用户对象
     */
    public static User getUser(HttpSession session) {
        // 从Session中取出用户对象并返回
        return (User) session.getAttribute(USER_KEY);
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getUserId(HttpSession session) {
        // 获取用户对象
        User user = getUser(session);
        // 如果用户存在返回ID，否则返回null
        return user != null ? user.getId() : null;
    }

    /**
     * 判断用户是否已登录
     */
    public static boolean isLoggedIn(HttpSession session) {
        // Session中有用户对象即为已登录
        return getUser(session) != null;
    }
}