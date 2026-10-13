package com.secondhand.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.ai.dto.AiChatRequest;
import com.secondhand.ai.dto.AiChatResponse;
import com.secondhand.entity.User;
import com.secondhand.interceptor.AuthInterceptor;
import com.secondhand.util.SessionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiChatControllerTest {

    private AiServiceClient aiServiceClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiServiceClient = org.mockito.Mockito.mock(AiServiceClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiChatController(aiServiceClient))
                .addFilters(new AuthInterceptor())
                .build();
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"c-1\",\"message\":\"找教材\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankMessageReturnsBusinessErrorWithoutCallingPython() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/api/ai/chat")
                        .session(session)
                        .contentType("application/json")
                        .content("{\"conversationId\":\"c-1\",\"message\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("消息不能为空"));

        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void validRequestForwardsSessionUserContext() throws Exception {
        MockHttpSession session = loggedInSession();
        AiChatResponse response = new AiChatResponse();
        response.setConversationId("c-1");
        response.setAnswer("找到 1 件商品");
        response.setTraceId("trace-1");
        when(aiServiceClient.chat(any(AiChatRequest.class), any(User.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/ai/chat")
                        .session(session)
                        .contentType("application/json")
                        .content("{\"conversationId\":\"c-1\",\"message\":\"找教材\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("找到 1 件商品"));

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(aiServiceClient).chat(requestCaptor.capture(), userCaptor.capture(), anyString());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().getConversationId()).isEqualTo("c-1");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().getMessage()).isEqualTo("找教材");
        org.assertj.core.api.Assertions.assertThat(userCaptor.getValue().getId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(userCaptor.getValue().getCampus()).isEqualTo("东校区");
        org.assertj.core.api.Assertions.assertThat(userCaptor.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    void unavailablePythonReturnsStableBusinessError() throws Exception {
        MockHttpSession session = loggedInSession();
        when(aiServiceClient.chat(any(AiChatRequest.class), any(User.class), anyString()))
                .thenThrow(new AiServiceClient.AiServiceUnavailableException());

        mockMvc.perform(post("/api/ai/chat")
                        .session(session)
                        .contentType("application/json")
                        .content("{\"conversationId\":\"c-1\",\"message\":\"找教材\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("AI 服务暂时不可用"));
    }

    private MockHttpSession loggedInSession() {
        User user = new User();
        user.setId(7L);
        user.setCampus("东校区");
        user.setRole("USER");
        MockHttpSession session = new MockHttpSession();
        SessionUtil.login(session, user);
        return session;
    }
}
