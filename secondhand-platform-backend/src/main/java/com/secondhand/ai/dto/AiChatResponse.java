package com.secondhand.ai.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response returned by the Python marketplace agent. */
@Data
@NoArgsConstructor
public class AiChatResponse {
    private String conversationId;
    private String answer;
    private List<AiProductCard> items = new ArrayList<>();
    private String traceId;
}
