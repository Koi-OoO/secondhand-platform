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
import java.util.*;
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
    private final UserService users;
    private final ChatWebSocketHandler sockets;
    @Value("${app.accounts.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    public SessionAccountService(UserService users, ChatWebSocketHandler sockets) {
        this.users = users; this.sockets = sockets;
    }

    @Getter
    public static final class Grant implements Serializable {
        private static final long serialVersionUID = 1L;
        private final long expiresAt;
        private final String fingerprint;
        Grant(long expiresAt, String fingerprint) { this.expiresAt = expiresAt; this.fingerprint = fingerprint; }
    }

    @Getter
    public static final class Account {
        private final Long userId;
        private final String username, nickname, avatar;
        private final boolean current;
        private final long expiresAt;
        Account(User u, boolean current, long expiresAt) {
            userId = u.getId(); username = u.getUsername(); nickname = u.getNickname(); avatar = u.getAvatar();
            this.current = current; this.expiresAt = expiresAt;
        }
    }

    @Getter
    public static final class State {
        private final Long currentUserId;
        private final String csrfToken;
        private final List<Account> accounts;
        State(Long id, String token, List<Account> accounts) { currentUserId = id; csrfToken = token; this.accounts = accounts; }
    }

    @Getter
    public static final class AccountException extends RuntimeException {
        private final int status;
        public AccountException(int status, String message) { super(message); this.status = status; }
    }

    public void establishLogin(HttpServletRequest request, User user) {
        checkOrigin(request);
        if (!enabled(user) || user.getPassword() == null) throw new AccountException(401, "账号或密码错误");
        HttpSession session = request.getSession();
        synchronized (session) {
            Map<Long, Grant> grants = grants(session);
            // 普通登录建立新的授权列表；追加账号走专门的添加接口，避免意外切换当前身份。
            grants.clear(); grants.put(user.getId(), grant(session, user));
            save(session, grants); SessionUtil.login(session, user);
            rotate(request, true);
        }
    }

    public State state(HttpServletRequest request) {
        checkOrigin(request);
        HttpSession session = requireSession(request);
        synchronized (session) {
            migrateLegacy(session);
            if (session.getAttribute(GRANTS_KEY) == null) throw new AccountException(401, "未登录");
            State state = describe(session);
            if (state.getAccounts().isEmpty()) throw new AccountException(401, "登录会话已失效，请重新登录");
            checkExpectedIdentity(request, session);
            return state;
        }
    }

    public State add(HttpServletRequest request, String username, String password) {
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);
            Map<Long, Grant> grants = grants(session);
            if (username == null || username.isBlank() || password == null || password.isEmpty())
                throw new AccountException(400, "请输入账号和密码");
            User user = users.getUserByUsername(username);
            if (!enabled(user) || !PasswordUtil.verify(password, user.getPassword()))
                throw new AccountException(400, "账号或密码错误");
            if (!grants.containsKey(user.getId()) && grants.size() >= 5)
                throw new AccountException(400, "当前会话最多保存5个账号");
            grants.put(user.getId(), grant(session, user)); save(session, grants);
            rotate(request, false);
            return describe(session);
        }
    }

    public State switchAccount(HttpServletRequest request, Long userId) {
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);
            Grant grant = grants(session).get(userId);
            User user = valid(userId, grant);
            if (user == null) throw new AccountException(403, "账号授权已失效，请重新添加");
            SessionUtil.login(session, user); rotate(request, true);
            return describe(session);
        }
    }

    public State remove(HttpServletRequest request, Long userId) {
        HttpSession session = requireSession(request);
        synchronized (session) {
            requireManager(request);
            Map<Long, Grant> grants = grants(session);
            if (grants.remove(userId) == null) throw new AccountException(400, "账号未授权");
            boolean active = Objects.equals(rawId(session), userId);
            save(session, grants);
            if (grants.isEmpty()) { invalidate(session); return new State(null, null, Collections.emptyList()); }
            if (active) {
                Long next = grants.keySet().iterator().next();
                User user = valid(next, grants.get(next));
                if (user != null) SessionUtil.login(session, user); else SessionUtil.clearUser(session);
            }
            rotate(request, active);
            return describe(session);
        }
    }

    public State logoutAll(HttpServletRequest request) {
        HttpSession session = requireSession(request);
        synchronized (session) { requireManager(request); invalidate(session); }
        return new State(null, null, Collections.emptyList());
    }

    public void logout(HttpSession session) { synchronized (session) { invalidate(session); } }

    /** 每次受保护请求重新检查当前授权；失效时返回未登录，不能悄悄换成另一个账号执行操作。 */
    public User validateCurrent(HttpSession session) {
        synchronized (session) {
            migrateLegacy(session);
            Long id = rawId(session);
            User user = valid(id, grants(session).get(id));
            if (user == null) { clean(session); return null; }
            return SessionUtil.safeCopy(user);
        }
    }

    public static boolean isManagerRoute(String method, String uri) {
        return ("GET".equals(method) && "/user/accounts".equals(uri)) ||
                ("POST".equals(method) && Set.of("/user/accounts/add", "/user/accounts/switch",
                        "/user/accounts/remove", "/user/accounts/logout-all").contains(uri));
    }

    private HttpSession requireManager(HttpServletRequest request) {
        checkOrigin(request);
        HttpSession session = requireSession(request);
        synchronized (session) {
            // 只有已有可信会话和管理令牌才能变更授权，不能仅凭前端传来的用户ID建立登录态。
            migrateLegacy(session); clean(session);
            String token = (String) session.getAttribute(TOKEN_KEY);
            String supplied = request.getHeader("X-Account-Management-Token");
            if (token == null || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
                throw new AccountException(403, "账号管理验证已失效，请刷新后重试");
            if (grants(session).isEmpty()) throw new AccountException(401, "未登录");
            checkExpectedIdentity(request, session);
            return session;
        }
    }

    private void checkExpectedIdentity(HttpServletRequest request, HttpSession session) {
        String expected = request.getHeader("X-Active-User-Id");
        Long current = rawId(session);
        if (current != null && expected != null && !expected.equals(current.toString()))
            throw new AccountException(409, "当前账号已在其他页面切换，请刷新");
    }

    private State describe(HttpSession session) {
        clean(session);
        Long current = rawId(session);
        List<Account> accounts = new ArrayList<>();
        grants(session).forEach((id, grant) -> {
            User user = valid(id, grant);
            if (user != null) accounts.add(new Account(user, Objects.equals(id, current), grant.expiresAt));
        });
        return new State(current, (String)session.getAttribute(TOKEN_KEY), accounts);
    }

    private void clean(HttpSession session) {
        Map<Long, Grant> grants = grants(session);
        grants.entrySet().removeIf(e -> valid(e.getKey(), e.getValue()) == null);
        save(session, grants);
        Long current = rawId(session);
        if (current != null && !grants.containsKey(current)) {
            SessionUtil.clearUser(session); sockets.closeSessionConnections(session.getId());
        }
    }

    private void migrateLegacy(HttpSession session) {
        if (session.getAttribute(GRANTS_KEY) != null) return;
        User trusted = SessionUtil.getSessionUser(session);
        if (trusted == null) return;
        User latest = users.getUserById(trusted.getId());
        Map<Long, Grant> grants = new LinkedHashMap<>();
        if (enabled(latest) && latest.getPassword() != null) grants.put(latest.getId(), grant(session, latest));
        save(session, grants); newToken(session);
    }

    private Grant grant(HttpSession session, User user) {
        long timeout = session.getMaxInactiveInterval();
        if (timeout <= 0 || timeout > 7 * 86400) timeout = 7 * 86400;
        return new Grant(System.currentTimeMillis() + timeout * 1000L, fingerprint(user.getPassword()));
    }

    private User valid(Long id, Grant grant) {
        if (id == null || grant == null || grant.expiresAt <= System.currentTimeMillis()) return null;
        User user = users.getUserById(id);
        return enabled(user) && user.getPassword() != null && grant.fingerprint.equals(fingerprint(user.getPassword())) ? user : null;
    }

    private boolean enabled(User user) { return user != null && user.getId() != null && Integer.valueOf(1).equals(user.getStatus()); }
    private String fingerprint(String hash) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked")
    private Map<Long, Grant> grants(HttpSession session) {
        Object value = session.getAttribute(GRANTS_KEY);
        return value instanceof Map ? new LinkedHashMap<>((Map<Long, Grant>)value) : new LinkedHashMap<>();
    }
    private void save(HttpSession session, Map<Long, Grant> grants) { session.setAttribute(GRANTS_KEY, new LinkedHashMap<>(grants)); }
    private Long rawId(HttpSession session) { User u = SessionUtil.getSessionUser(session); return u == null ? null : u.getId(); }
    private HttpSession requireSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new AccountException(401, "未登录");
        return session;
    }
    private void rotate(HttpServletRequest request, boolean closeSockets) {
        HttpSession session = request.getSession(false);
        String oldId = session.getId();
        request.changeSessionId(); newToken(session);
        // 添加账号也会更换会话ID，需关闭旧ID的连接，避免下一次切换时遗漏这些连接。
        sockets.closeSessionConnections(oldId);
    }
    private void newToken(HttpSession session) { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); session.setAttribute(TOKEN_KEY, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)); }
    private void invalidate(HttpSession session) { sockets.closeSessionConnections(session.getId()); session.invalidate(); }

    public void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) return;
        try {
            URI parsed = URI.create(origin);
            if (parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null || (parsed.getPath() != null && !parsed.getPath().isEmpty()))
                throw new IllegalArgumentException();
            String same = request.getScheme() + "://" + request.getServerName();
            int port = request.getServerPort();
            if (!("http".equals(request.getScheme()) && port == 80) && !("https".equals(request.getScheme()) && port == 443)) same += ":" + port;
            if (origin.equals(same) || Arrays.stream(allowedOrigins.split(",")).map(String::trim).anyMatch(origin::equals)) return;
        } catch (IllegalArgumentException ignored) { }
        throw new AccountException(403, "不允许此来源访问账号管理");
    }
}
