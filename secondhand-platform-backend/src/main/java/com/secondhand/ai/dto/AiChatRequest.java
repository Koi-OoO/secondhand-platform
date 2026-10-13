package com.secondhand.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Browser-facing request for one AI conversation turn. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String conversationId;
    private String message;
}
