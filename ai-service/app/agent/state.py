from dataclasses import dataclass, field

from pydantic import BaseModel, ConfigDict, Field

from app.agent.marketplace.schemas import ProductCard, UserContext


class AgentChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str = Field(alias="conversationId", min_length=1)
    message: str = Field(min_length=1)
    user_context: UserContext = Field(alias="userContext")
    trace_id: str = Field(alias="traceId", min_length=1)


class AgentChatResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str | None = Field(default=None, alias="conversationId")
    answer: str
    items: list["ProductCard"] = Field(default_factory=list)
    trace_id: str = Field(alias="traceId")


@dataclass
class ExecutionContext:
    latest_items: list["ProductCard"] = field(default_factory=list)


AgentChatResponse.model_rebuild()
