package com.secondhand.service;

import com.secondhand.entity.User;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.SessionUtil;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 管理当前浏览器会话内已通过密码验证的账号，不保存密码，也不创建长期设备凭证。 */
@Service
public class SessionAccountService {
    public static final String GRANTS_KEY = SessionAccountService.class.getName() + ".grants";
    private static final String TOKEN_KEY = SessionAccountService.class.getName() + ".csrf";
    private final UserService userService;
    private final ChatWebSocketHandler chatWebSocketHandler;
    @Value("${app.accounts.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    public SessionAccountService(UserService userService, ChatWebSocketHandler chatWebSocketHandler) {
        this.userService = userService;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Getter
    public static final class Grant implements Serializable {
        private static final long serialVersionUID = 1L;
        private final long expiresAt; // 本次授权的绝对到期时间
        private final String fingerprint; // 密码哈希的指纹，用于识别密码是否已变更
        Grant(long expiresAt, String fingerprint) {
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
        Account(User user, boolean current, long expiresAt) {
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
        State(Long currentUserId, String csrfToken, List<Account> accounts) {
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

    public void establishLogin(HttpServletRequest request, User user) {
        // 1. 校验请求来源和账号状态
        checkOrigin(request);
        if (!enabled(user) || user.getPassword() == null) {
            throw new AccountException(401, "账号或密码错误");
        }

        // 2. 在当前会话内建立新的授权列表
        HttpSession session = request.getSession();
        synchronized (session) {
            Map<Long, Grant> grants = grants(session);
            // 普通登录建立新的授权列表；追加账号走专门的添加接口，避免意外切换当前身份。
            grants.clear();
            grants.put(user.getId(), grant(session, user));

            // 3. 保存授权并设置当前登录用户
            save(session, grants);
            SessionUtil.login(session, user);

            // 4. 更换SessionID和管理令牌，关闭旧会话连接
            rotate(request);
        }
    }

    public State state(HttpServletRequest request) {
        // 1. 校验来源并获取已有会话
        checkOrigin(request);
        HttpSession session = requireSession(request);
        synchronized (session) {
            // 2. 兼容旧登录态，清理失效授权并组装账号列表
            migrateLegacy(session);
            if (session.getAttribute(GRANTS_KEY) == null) {
                throw new AccountException(401, "未登录");
            }
            State state = describe(session);
            if (state.getAccounts().isEmpty()) {
                throw new AccountException(401, "登录会话已失效，请重新登录");
            }

            // 3. 校验页面展示的身份，返回当前账号列表
            checkExpectedIdentity(request, session);
            return state;
        }
    }

    public State add(HttpServletRequest request, String username, String password) {
        // 1. 获取已有会话，校验账号管理权限
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);

            // 2. 验证待添加账号的密码，并检查账号数量上限
            Map<Long, Grant> grants = grants(session);
            if (username == null || username.isBlank() || password == null || password.isEmpty()) {
                throw new AccountException(400, "请输入账号和密码");
            }
            User user = userService.getUserByUsername(username);
            if (!enabled(user) || !PasswordUtil.verify(password, user.getPassword())) {
                throw new AccountException(400, "账号或密码错误");
            }
            if (!grants.containsKey(user.getId()) && grants.size() >= 5) {
                throw new AccountException(400, "当前会话最多保存5个账号");
            }

            // 3. 保存账号授权，保持当前登录身份
            grants.put(user.getId(), grant(session, user));
            save(session, grants);

            // 4. 更换会话标识和管理令牌，返回账号列表
            rotate(request);
            return describe(session);
        }
    }

    public State switchAccount(HttpServletRequest request, Long userId) {
        // 1. 获取已有会话，校验账号管理权限
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);

            // 2. 校验目标账号的授权期限、启用状态和密码指纹
            Grant grant = grants(session).get(userId);
            User user = valid(userId, grant);
            if (user == null) {
                throw new AccountException(403, "账号授权已失效，请重新添加");
            }

            // 3. 切换当前身份，更换会话标识并关闭旧连接
            SessionUtil.login(session, user);
            rotate(request);

            // 4. 返回切换后的账号列表
            return describe(session);
        }
    }

    public State remove(HttpServletRequest request, Long userId) {
        // 1. 获取已有会话，校验账号管理权限
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);

            // 2. 移除目标账号的本次会话授权
            Map<Long, Grant> grants = grants(session);
            if (grants.remove(userId) == null) {
                throw new AccountException(400, "账号未授权");
            }
            boolean active = Objects.equals(rawId(session), userId);
            save(session, grants);

            // 3. 没有剩余授权时结束整个会话
            if (grants.isEmpty()) {
                invalidate(session);
                return new State(null, null, Collections.emptyList());
            }

            // 4. 移除的是当前账号时，尝试选择一个剩余账号
            if (active) {
                Long next = grants.keySet().iterator().next();
                User user = valid(next, grants.get(next));
                if (user != null) {
                    SessionUtil.login(session, user);
                } else {
                    SessionUtil.clearUser(session);
                }
            }

            // 5. 更换会话标识并返回剩余账号列表
            rotate(request);
            return describe(session);
        }
    }

    public State logoutAll(HttpServletRequest request) {
        // 1. 获取已有会话并校验管理权限
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);

            // 2. 关闭会话连接，撤销本次会话的全部授权
            invalidate(session);
        }

        // 3. 返回空账号列表
        return new State(null, null, Collections.emptyList());
    }

    public void logout(HttpSession session) {
        synchronized (session) {
            invalidate(session);
        }
    }

    /** 每次受保护请求重新检查当前授权；失效时返回未登录，不能悄悄换成另一个账号执行操作。 */
    public User validateCurrent(HttpSession session) {
        synchronized (session) {
            // 1. 兼容旧登录态，校验当前账号授权
            migrateLegacy(session);
            Long id = rawId(session);
            User user = valid(id, grants(session).get(id));

            // 2. 授权失效时清理会话身份，不自动切换到其他账号
            if (user == null) {
                clean(session);
                return null;
            }

            // 3. 返回不含敏感字段的用户副本
            return SessionUtil.safeCopy(user);
        }
    }

    public static boolean isManagerRoute(String method, String uri) {
        return ("GET".equals(method) && "/user/accounts".equals(uri)) ||
                ("POST".equals(method) && Set.of("/user/accounts/add", "/user/accounts/switch",
                        "/user/accounts/remove", "/user/accounts/logout-all").contains(uri));
    }

    private HttpSession requireManager(HttpServletRequest request) {
        // 1. 校验来源，要求已存在登录会话
        checkOrigin(request);
        HttpSession session = requireSession(request);
        synchronized (session) {
            // 2. 兼容旧会话并清理失效授权
            // 只有已有可信会话和管理令牌才能变更授权，不能仅凭前端传来的用户ID建立登录态。
            migrateLegacy(session);
            clean(session);

            // 3. 核对账号管理令牌，使用恒定时间比较
            String token = (String) session.getAttribute(TOKEN_KEY);
            String supplied = request.getHeader("X-Account-Management-Token");
            if (token == null || supplied == null
                    || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                throw new AccountException(403, "账号管理验证已失效，请刷新后重试");
            }

            // 4. 要求仍有有效授权，并核对页面身份
            if (grants(session).isEmpty()) {
                throw new AccountException(401, "未登录");
            }
            checkExpectedIdentity(request, session);
            return session;
        }
    }

    private void checkExpectedIdentity(HttpServletRequest request, HttpSession session) {
        String expected = request.getHeader("X-Active-User-Id");
        Long current = rawId(session);
        if (current != null && expected != null && !expected.equals(current.toString())) {
            throw new AccountException(409, "当前账号已在其他页面切换，请刷新");
        }
    }

    private State describe(HttpSession session) {
        // 1. 清理已失效授权，获取当前身份
        clean(session);
        Long current = rawId(session);

        // 2. 组装可展示的账号信息，不返回密码或指纹
        List<Account> accounts = new ArrayList<>();
        grants(session).forEach((id, grant) -> {
            User user = valid(id, grant);
            if (user != null) {
                accounts.add(new Account(user, Objects.equals(id, current), grant.expiresAt));
            }
        });

        // 3. 返回当前身份、管理令牌和账号列表
        return new State(current, (String)session.getAttribute(TOKEN_KEY), accounts);
    }

    private void clean(HttpSession session) {
        // 1. 移除过期、停用或密码已变更的账号授权
        Map<Long, Grant> grants = grants(session);
        grants.entrySet().removeIf(e -> valid(e.getKey(), e.getValue()) == null);
        save(session, grants);

        // 2. 当前账号失效时清理身份并关闭连接
        Long current = rawId(session);
        if (current != null && !grants.containsKey(current)) {
            SessionUtil.clearUser(session);
            chatWebSocketHandler.closeSessionConnections(session.getId());
        }
    }

    private void migrateLegacy(HttpSession session) {
        // 1. 已有账号授权列表时无需迁移
        if (session.getAttribute(GRANTS_KEY) != null) {
            return;
        }

        // 2. 从原有登录态查询当前账号的最新信息
        User trusted = SessionUtil.getSessionUser(session);
        if (trusted == null) {
            return;
        }
        User latest = userService.getUserById(trusted.getId());

        // 3. 为仍有效的账号建立授权，保存列表和管理令牌
        Map<Long, Grant> grants = new LinkedHashMap<>();
        if (enabled(latest) && latest.getPassword() != null) {
            grants.put(latest.getId(), grant(session, latest));
        }
        save(session, grants);
        newToken(session);
    }

    private Grant grant(HttpSession session, User user) {
        // 1. 按会话超时配置计算授权期限，最多7天
        long timeout = session.getMaxInactiveInterval();
        if (timeout <= 0 || timeout > 7 * 86400) {
            timeout = 7 * 86400;
        }

        // 2. 保存绝对到期时间和密码哈希指纹
        return new Grant(System.currentTimeMillis() + timeout * 1000L, fingerprint(user.getPassword()));
    }

    private User valid(Long id, Grant grant) {
        // 1. 校验账号ID、授权是否存在及到期时间
        if (id == null || grant == null || grant.expiresAt <= System.currentTimeMillis()) {
            return null;
        }

        // 2. 查询最新账号，校验启用状态和密码指纹
        User user = userService.getUserById(id);
        if (!enabled(user) || user.getPassword() == null) {
            return null;
        }
        if (!grant.fingerprint.equals(fingerprint(user.getPassword()))) {
            return null;
        }

        // 3. 返回仍获授权的账号
        return user;
    }

    private boolean enabled(User user) {
        return user != null && user.getId() != null && Integer.valueOf(1).equals(user.getStatus());
    }

    private String fingerprint(String hash) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
    @SuppressWarnings("unchecked")
    private Map<Long, Grant> grants(HttpSession session) {
        Object value = session.getAttribute(GRANTS_KEY);
        return value instanceof Map ? new LinkedHashMap<>((Map<Long, Grant>)value) : new LinkedHashMap<>();
    }
    private void save(HttpSession session, Map<Long, Grant> grants) {
        session.setAttribute(GRANTS_KEY, new LinkedHashMap<>(grants));
    }

    private Long rawId(HttpSession session) {
        User user = SessionUtil.getSessionUser(session);
        return user == null ? null : user.getId();
    }
    private HttpSession requireSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AccountException(401, "未登录");
        }
        return session;
    }
    private void rotate(HttpServletRequest request) {
        // 1. 更换SessionID，并重新生成管理令牌
        HttpSession session = request.getSession(false);
        String oldId = session.getId();
        request.changeSessionId();
        newToken(session);

        // 2. 关闭旧SessionID的连接，避免旧连接继续接收消息
        // 添加账号也会更换会话ID，需关闭旧ID的连接，避免下一次切换时遗漏这些连接。
        chatWebSocketHandler.closeSessionConnections(oldId);
    }
    private void newToken(HttpSession session) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        session.setAttribute(TOKEN_KEY, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    private void invalidate(HttpSession session) {
        chatWebSocketHandler.closeSessionConnections(session.getId());
        session.invalidate();
    }

    public void checkOrigin(HttpServletRequest request) {
        // 1. 没有Origin头时沿用原有放行规则
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return;
        }
        try {
            // 2. 校验来源地址格式
            URI parsed = URI.create(origin);
            if (parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getQuery() != null
                    || parsed.getFragment() != null || (parsed.getPath() != null && !parsed.getPath().isEmpty())) {
                throw new IllegalArgumentException();
            }

            // 3. 只允许当前服务同源地址或配置的白名单
            String same = request.getScheme() + "://" + request.getServerName();
            int port = request.getServerPort();
            if (!("http".equals(request.getScheme()) && port == 80)
                    && !("https".equals(request.getScheme()) && port == 443)) {
                same += ":" + port;
            }
            if (origin.equals(same) || Arrays.stream(allowedOrigins.split(",")).map(String::trim).anyMatch(origin::equals)) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // 来源格式不合法时，统一进入下方拒绝分支。
        }

        // 4. 其他来源统一拒绝
        throw new AccountException(403, "不允许此来源访问账号管理");
    }
}
