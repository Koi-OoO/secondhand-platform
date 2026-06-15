package com.secondhand.handler;

import com.secondhand.util.Result;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleExceptionReturnsUnifiedErrorResult() {
        Result result = handler.handleException(new RuntimeException("boom"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("服务器繁忙，请稍后再试");
        assertThat(result.getData()).isNull();
    }

    @Test
    void handleMissingParamMentionsParameterName() {
        Result result = handler.handleMissingParam(
                new MissingServletRequestParameterException("productId", "Long"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("productId");
    }
}
