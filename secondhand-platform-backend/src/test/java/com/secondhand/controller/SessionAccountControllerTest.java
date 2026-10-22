package com.secondhand.controller;

import com.secondhand.entity.User;
import com.secondhand.handler.GlobalExceptionHandler;
import com.secondhand.interceptor.AuthInterceptor;
import com.secondhand.service.SessionAccountService;
import com.secondhand.service.UserService;
import com.secondhand.util.PasswordUtil;
import com.secondhand.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

class SessionAccountControllerTest {
    MockMvc mvc;
    MockHttpSession session;
    String token;
    UserService users;
    User a;
    @BeforeEach void setup() {
        users = mock(UserService.class);
        a = new User(); a.setId(1L); a.setUsername("a"); a.setStatus(1); a.setPassword(PasswordUtil.encrypt("secret"));
        when(users.getUserById(1L)).thenReturn(a);
        SessionAccountService accounts = new SessionAccountService(users, new ChatWebSocketHandler());
        MockHttpServletRequest request = new MockHttpServletRequest(); session = new MockHttpSession(); request.setSession(session);
        accounts.establishLogin(request, a); token = accounts.state(request).getCsrfToken();
        AuthInterceptor filter = new AuthInterceptor(); ReflectionTestUtils.setField(filter, "sessionAccountService", accounts);
        UserController login = new UserController(); ReflectionTestUtils.setField(login, "userService", users);
        ReflectionTestUtils.setField(login, "sessionAccountService", accounts);
        mvc = MockMvcBuilders.standaloneSetup(new SessionAccountController(accounts), login).setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }
    @Test void exactManagerRoutesStillEnforceSessionOriginAndToken() throws Exception {
        mvc.perform(get("/user/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/accounts").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentUserId").value(1));
        mvc.perform(get("/user/accounts").session(session).header("Origin", "https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(post("/user/accounts/switch").session(session).contentType("application/json").content("{\"userId\":99}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/accounts/switch").session(session).header("X-Account-Management-Token", token).contentType("application/json").content("{\"userId\":99}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/accounts/remove").session(session).header("X-Account-Management-Token", token).header("X-Active-User-Id", "2").contentType("application/json").content("{\"userId\":1}"))
                .andExpect(status().isConflict()).andExpect(header().string("X-Account-Changed", "true"));
    }
    @Test void getterRejectsStaleTabAndBadAddCredentialsPreserveUsableSession() throws Exception {
        mvc.perform(get("/user/accounts").session(session).header("X-Active-User-Id", "2"))
                .andExpect(status().isConflict()).andExpect(header().string("X-Account-Changed", "true"));
        mvc.perform(post("/user/accounts/add").session(session).header("X-Account-Management-Token", token).contentType("application/json").content("{\"username\":\"a\",\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/user/accounts").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentUserId").value(1));
    }
    @Test void nonManagerSuffixAndWrongVerbDoNotBypassAuthentication() throws Exception {
        mvc.perform(get("/user/accounts/anything")).andExpect(status().isUnauthorized());
        mvc.perform(post("/user/accounts")).andExpect(status().isUnauthorized());
    }
    @Test void loginAndLogoutRejectForeignOriginsBeforeChangingSession() throws Exception {
        when(users.login("a", "secret")).thenReturn(com.secondhand.util.Result.success("ok"));
        when(users.getUserByUsername("a")).thenReturn(a);
        mvc.perform(post("/user/login").session(session).header("Origin", "https://evil.example").contentType("application/json").content("{\"username\":\"a\",\"password\":\"secret\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/logout").session(session).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/user/accounts").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentUserId").value(1));
    }

    @Test void failedNormalLoginPreservesExistingPoolAndSuccessfulLoginRotatesSession() throws Exception {
        String oldId = session.getId();
        when(users.login("a", "bad")).thenReturn(com.secondhand.util.Result.error("bad"));
        mvc.perform(post("/user/login").session(session).contentType("application/json").content("{\"username\":\"a\",\"password\":\"bad\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        org.assertj.core.api.Assertions.assertThat(session.getId()).isEqualTo(oldId);
        when(users.login("a", "secret")).thenReturn(com.secondhand.util.Result.success("ok")); when(users.getUserByUsername("a")).thenReturn(a);
        mvc.perform(post("/user/login").session(session).contentType("application/json").content("{\"username\":\"a\",\"password\":\"secret\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        org.assertj.core.api.Assertions.assertThat(session.getId()).isNotEqualTo(oldId);
    }
}
