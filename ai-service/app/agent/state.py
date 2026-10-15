"""Agent 的请求、响应和单次运行状态模型。"""

from dataclasses import dataclass, field

from pydantic import BaseModel, ConfigDict, Field

from app.agent.marketplace.schemas import ProductCard, SearchFilters, UserContext


class AgentChatRequest(BaseModel):
    """Java AI Facade 发送给 Python 的请求数据。"""

    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str = Field(alias="conversationId", min_length=1)
    message: str = Field(min_length=1)
    user_context: UserContext = Field(alias="userContext")
    trace_id: str = Field(alias="traceId", min_length=1)


class AgentChatResponse(BaseModel):
    """返回给 Java AI Facade 的稳定响应结构。"""

    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str | None = Field(default=None, alias="conversationId")
    answer: str
    items: list["ProductCard"] = Field(default_factory=list)
    search_filters: SearchFilters | None = Field(default=None, alias="searchFilters")
    trace_id: str = Field(alias="traceId")


@dataclass
class ExecutionContext:
    """一次 Agent 调用期间由多个工具共享的可变状态。"""

    latest_items: list["ProductCard"] = field(default_factory=list)
    latest_search_filters: SearchFilters | None = None  # 仅记录本次成功搜索的真实参数


AgentChatResponse.model_rebuild()
