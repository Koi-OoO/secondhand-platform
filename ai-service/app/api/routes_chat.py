"""AI 商品查询 Agent 对外提供的 HTTP 路由。"""

from fastapi import APIRouter, Depends, HTTPException, status

from app.agent.runtime import AgentUnavailableError, run_agent
from app.agent.state import AgentChatRequest, AgentChatResponse
from app.core.security import require_internal_token

router = APIRouter(prefix="/internal/agent", tags=["agent"])


@router.post("/chat", response_model=AgentChatResponse)
async def chat(
    request: AgentChatRequest,
    _: None = Depends(require_internal_token),
) -> AgentChatResponse:
    """执行一次经过鉴权的只读商品查询。

    依赖项会在进入函数前校验 Java 到 Python 的服务令牌。
    模型配置失败时统一返回 503，避免把内部异常细节暴露给调用方。
    """
    try:
        result = await run_agent(request)
    except AgentUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI 服务暂时不可用",
        ) from exc
    return AgentChatResponse.model_validate(result.model_dump(by_alias=True))


__all__ = ["router"]
