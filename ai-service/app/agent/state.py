"""Agent 的请求、响应和单次运行状态模型。"""

from dataclasses import dataclass, field

from pydantic import BaseModel, ConfigDict, Field

from app.agent.marketplace.schemas import ProductCard, SearchFilters, UserContext


class AgentChatRequest(BaseModel):
    """Java AI Facade 发送给 Python 的请求数据。"""

    model_config = ConfigDict(populate_by_name=True)

    # alias 对接 Java 的驼峰命名，Python 内部继续使用下划线命名。
    conversation_id: str = Field(alias="conversationId", min_length=1)  # 会话标识，本身不等于已有聊天记忆
    message: str = Field(min_length=1)  # 本轮用户输入，不是完整聊天记录
    user_context: UserContext = Field(alias="userContext")  # Java 根据登录状态提供的身份
    trace_id: str = Field(alias="traceId", min_length=1)  # 本次调用的追踪标识


class AgentChatResponse(BaseModel):
    """返回给 Java AI Facade 的稳定响应结构。"""

    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str | None = Field(default=None, alias="conversationId")
    answer: str  # 模型最终组织的文字回答
    items: list["ProductCard"] = Field(default_factory=list)  # 工具查询得到的商品卡片
    search_filters: SearchFilters | None = Field(default=None, alias="searchFilters")  # 继续浏览使用的查询条件
    trace_id: str = Field(alias="traceId")


@dataclass
class ExecutionContext:
    """一次 Agent 调用期间由多个工具共享的可变状态。"""

    # 每轮都创建一个新对象；工具在这里保存结果，run_agent 再取出来生成响应。
    # default_factory=list 确保每个对象有自己的列表，避免不同请求共用同一份商品卡片。
    latest_items: list["ProductCard"] = field(default_factory=list)
    latest_search_filters: SearchFilters | None = None  # 仅记录本次成功搜索的真实参数


# 补全使用字符串声明的类型引用，让响应模型能正确处理 ProductCard。
AgentChatResponse.model_rebuild()
