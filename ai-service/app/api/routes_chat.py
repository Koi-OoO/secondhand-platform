"""HTTP routes exposed by the internal marketplace Agent service."""

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
    """Run one authenticated, read-only marketplace Agent turn.

    The dependency validates the Java-to-Python service token before this
    function is called. Model configuration failures are translated into a
    stable 503 response so internal details do not reach the caller.
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
