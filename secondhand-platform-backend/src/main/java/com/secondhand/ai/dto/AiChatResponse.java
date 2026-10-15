package com.secondhand.ai.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Python 商品查询助手返回的响应。 */
@Data
@NoArgsConstructor
public class AiChatResponse {
    private String conversationId;
    private String answer;
    private List<AiProductCard> items = new ArrayList<>();
    /** 成功商品搜索实际使用的筛选条件；未搜索时允许为空。 */
    private AiSearchFilters searchFilters;
    private String traceId;
}
