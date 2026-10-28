package com.secondhand.ai.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Python 商品查询助手返回的响应。 */
@Data
@NoArgsConstructor
public class AiChatResponse {
    /** 本轮对话所属的会话ID。 */
    private String conversationId;

    /** AI 返回的自然语言回答。 */
    private String answer;

    /** AI 推荐的商品卡片。 */
    private List<AiProductCard> items = new ArrayList<>();

    /** 成功商品搜索实际使用的筛选条件；未搜索时允许为空。 */
    private AiSearchFilters searchFilters;

    /** 本次调用的链路追踪ID。 */
    private String traceId;
}
