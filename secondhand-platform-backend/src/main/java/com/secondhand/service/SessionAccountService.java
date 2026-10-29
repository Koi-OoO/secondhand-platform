package com.secondhand.service;

import com.secondhand.entity.User;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.Getter;

/** 当前浏览器会话内的账号授权、切换和身份校验接口。 */
public interface SessionAccountService {
    String GRANTS_KEY = SessionAccountService.class.getName() + ".grants";

    @Getter
    public static final class Grant implements Serializable {
        private static final long serialVersionUID = 1L;
        private final long expiresAt; // 本次授权的绝对到期时间
        private final String fingerprint; // 密码哈希的指纹，用于识别密码是否已变更
        public Grant(long expiresAt, String fingerprint) {
            this.expiresAt = expiresAt;
            this.fingerprint = fingerprint;
        }
    }

    @Getter
    public static final class Account {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final String avatar;
        private final boolean current;
        private final long expiresAt;
        public Account(User user, boolean current, long expiresAt) {
            userId = user.getId();
            username = user.getUsername();
            nickname = user.getNickname();
            avatar = user.getAvatar();
            this.current = current;
            this.expiresAt = expiresAt;
        }
    }

    @Getter
    public static final class State {
        private final Long currentUserId;
        private final String csrfToken;
        private final List<Account> accounts;
        public State(Long currentUserId, String csrfToken, List<Account> accounts) {
            this.currentUserId = currentUserId;
            this.csrfToken = csrfToken;
            this.accounts = accounts;
        }
    }

    @Getter
    public static final class AccountException extends RuntimeException {
        private final int status;
        public AccountException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /** 普通登录时建立授权并设置当前身份。 */
    void establishLogin(HttpServletRequest request, User user);

    /** 获取当前身份和有效账号列表。 */
    State state(HttpServletRequest request);

    /** 验证密码后添加账号，保持当前身份。 */
    State add(HttpServletRequest request, String username, String password);

    /** 切换到当前会话内已授权的账号。 */
    State switchAccount(HttpServletRequest request, Long userId);

    /** 移除会话授权，不删除数据库用户。 */
    State remove(HttpServletRequest request, Long userId);

    /** 撤销当前会话的全部账号授权。 */
    State logoutAll(HttpServletRequest request);

    /** 关闭连接并结束会话。 */
    void logout(HttpSession session);

    /** 检查当前授权，失效时不自动切换账号。 */
    User validateCurrent(HttpSession session);

    /** 校验账号管理请求的来源。 */
    void checkOrigin(HttpServletRequest request);

    public static boolean isManagerRoute(String method, String uri) {
        return ("GET".equals(method) && "/user/accounts".equals(uri)) ||
                ("POST".equals(method) && Set.of("/user/accounts/add", "/user/accounts/switch",
                        "/user/accounts/remove", "/user/accounts/logout-all").contains(uri));
    }

}
