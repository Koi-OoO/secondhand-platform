package com.secondhand.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.entity.User;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.SessionUtil;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.io.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionAccountServiceTest {
    UserService users;
    SessionAccountService service;
    MockHttpServletRequest request;
    User a, b;

    @BeforeEach void setup() {
        users = mock(UserService.class);
        service = new SessionAccountService(users, new ChatWebSocketHandler());
        request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        a = user(1L); b = user(2L);
        service.establishLogin(request, a);
        request.addHeader("X-Account-Management-Token", service.state(request).getCsrfToken());
    }

    User user(long id) {
        User u = new User(); u.setId(id); u.setUsername("user" + id);
        u.setPassword(PasswordUtil.encrypt("secret")); u.setStatus(1);
        u.setEmail("private@example.com"); u.setPhone("private-phone");
        when(users.getUserById(id)).thenReturn(u);
        when(users.getUserByUsername(u.getUsername())).thenReturn(u);
        return u;
    }

    @Test void addsWithoutSwitchingAndOnlyAuthorizedAccountsCanSwitch() {
        assertThatThrownBy(() -> service.switchAccount(request, 2L)).isInstanceOf(SessionAccountService.AccountException.class);
        service.add(request, "user2", "secret");
        refreshToken();
        assertThat(service.state(request).getCurrentUserId()).isEqualTo(1L);
        assertThat(service.switchAccount(request, 2L).getCurrentUserId()).isEqualTo(2L);
        assertThat(a.getPassword()).isNotNull();
    }

    @Test void rejectsMissingWrongTokenAndForeignOriginIncludingReads() {
        request.removeHeader("X-Account-Management-Token");
        assertThatThrownBy(() -> service.add(request, "user2", "secret")).isInstanceOf(SessionAccountService.AccountException.class);
        request.addHeader("X-Account-Management-Token", "wrong");
        assertThatThrownBy(() -> service.remove(request, 1L)).isInstanceOf(SessionAccountService.AccountException.class);
        request.addHeader("Origin", "https://evil.example");
        assertThatThrownBy(() -> service.state(request)).isInstanceOf(SessionAccountService.AccountException.class);
    }

    @Test void wrongPasswordDoesNotChangeCurrentAndMaximumIsFive() {
        assertThatThrownBy(() -> service.add(request, "user2", "bad")).isInstanceOf(SessionAccountService.AccountException.class);
        assertThat(service.state(request).getCurrentUserId()).isEqualTo(1L);
        for (long id = 2; id <= 6; id++) user(id);
        for (long id = 2; id <= 5; id++) { service.add(request, "user" + id, "secret"); refreshToken(); }
        assertThatThrownBy(() -> service.add(request, "user6", "secret")).isInstanceOf(SessionAccountService.AccountException.class);
        assertThat(service.state(request).getAccounts()).hasSize(5);
    }

    @Test void passwordChangeAndDisabledCurrentCannotFallBackToAnotherIdentity() {
        service.add(request, "user2", "secret"); refreshToken();
        a.setPassword(PasswordUtil.encrypt("new"));
        assertThat(service.validateCurrent(request.getSession())).isNull();
        assertThat(service.state(request).getCurrentUserId()).isNull();
        assertThat(service.state(request).getAccounts()).hasSize(1);
        service.switchAccount(request, 2L);
        b.setStatus(0);
        assertThat(service.validateCurrent(request.getSession())).isNull();
    }

    @Test void removalOfCurrentChoosesRemainingAndLastRemovalInvalidates() {
        service.add(request, "user2", "secret"); refreshToken();
        assertThat(service.remove(request, 1L).getCurrentUserId()).isEqualTo(2L); refreshToken();
        MockHttpSession session = (MockHttpSession)request.getSession(false);
        assertThat(service.remove(request, 2L).getAccounts()).isEmpty();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void changedTargetPasswordRevokesOnlyThatGrantAndAllowsPasswordReverification() {
        service.add(request, "user2", "secret"); refreshToken();
        b.setPassword(PasswordUtil.encrypt("changed"));
        assertThatThrownBy(() -> service.switchAccount(request, 2L))
                .isInstanceOf(SessionAccountService.AccountException.class).extracting("status").isEqualTo(403);
        assertThat(service.state(request).getCurrentUserId()).isEqualTo(1L);
        assertThat(service.state(request).getAccounts()).hasSize(1);
        service.add(request, "user2", "changed"); refreshToken();
        assertThat(service.switchAccount(request, 2L).getCurrentUserId()).isEqualTo(2L);
    }

    @Test void logoutDestroysTheEntireSession() {
        MockHttpSession session = (MockHttpSession) request.getSession();
        service.logoutAll(request);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void publicStateHidesSecretsAndSessionAttributesAreSerializable() throws Exception {
        String json = new ObjectMapper().writeValueAsString(service.state(request));
        assertThat(json).doesNotContain("password", "fingerprint", "email", "phone", a.getPassword());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            java.util.Enumeration<String> names = request.getSession().getAttributeNames();
            while (names.hasMoreElements()) out.writeObject(request.getSession().getAttribute(names.nextElement()));
        }
        assertThat(bytes.size()).isPositive();
    }

    @Test void staleExpectedIdentityIsConflictAndOriginAllowlistWorks() {
        request.addHeader("Origin", "http://localhost:5173");
        assertThat(service.state(request).getCurrentUserId()).isEqualTo(1L);
        request.addHeader("X-Active-User-Id", "2");
        assertThatThrownBy(() -> service.remove(request, 1L))
                .isInstanceOf(SessionAccountService.AccountException.class)
                .extracting("status").isEqualTo(409);
    }

    @Test void absoluteGrantExpiresEvenWhileSessionContinues() {
        MockHttpServletRequest shortRequest = new MockHttpServletRequest();
        shortRequest.setSession(new MockHttpSession()); shortRequest.getSession().setMaxInactiveInterval(1);
        service.establishLogin(shortRequest, b);
        SessionAccountService.Grant grant = ((java.util.Map<Long, SessionAccountService.Grant>)shortRequest.getSession().getAttribute(SessionAccountService.GRANTS_KEY)).get(2L);
        org.springframework.test.util.ReflectionTestUtils.setField(grant, "expiresAt", System.currentTimeMillis() - 1);
        assertThat(service.validateCurrent(shortRequest.getSession())).isNull();
        assertThatThrownBy(() -> service.state(shortRequest)).isInstanceOf(SessionAccountService.AccountException.class)
                .extracting("status").isEqualTo(401);
    }

    void refreshToken() { request.removeHeader("X-Account-Management-Token"); request.addHeader("X-Account-Management-Token", service.state(request).getCsrfToken()); }
}
