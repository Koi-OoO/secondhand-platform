"""AI 商品查询 Agent 对外提供的 HTTP 路由。"""

from fastapi import APIRouter, Depends, HTTPException, status

from app.agent.runtime import AgentUnavailableError, run_agent
from app.agent.state import AgentChatRequest, AgentChatResponse
from app.core.security import require_internal_token

# 1. 设置这组接口的公共路径，下面的 /chat 最终是 POST /internal/agent/chat。
router = APIRouter(prefix="/internal/agent", tags=["agent"])


@router.post("/chat", response_model=AgentChatResponse)
async def chat(
    # FastAPI 将 Java 发来的 JSON 转成请求对象，并按 AgentChatRequest 的字段规则校验。
    request: AgentChatRequest,
    # Depends 先执行鉴权函数；_ 表示这里只关心是否校验通过，不需要使用返回值。
    _: None = Depends(require_internal_token),
) -> AgentChatResponse:
    """执行一次经过鉴权的只读商品查询。

    依赖项会在进入函数前校验 Java 到 Python 的服务令牌。
    模型配置失败时统一返回 503，避免把内部异常细节暴露给调用方。
    """
    try:
        # 2. 把请求交给 Agent 主流程，等待模型及所需工具执行结束。
        result = await run_agent(request)
    except AgentUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI 服务暂时不可用",
        ) from exc
    # 3. 转换成约定好的响应结构，保留 conversationId、searchFilters 等 Java 字段名。
    # response_model 也会按该结构校验并序列化 HTTP 响应。
    return AgentChatResponse.model_validate(result.model_dump(by_alias=True))


__all__ = ["router"]
