"""商品查询 Agent 的创建工厂和单轮执行流程。"""

from __future__ import annotations

import inspect
from typing import Any, Callable

from pydantic import BaseModel, ConfigDict, Field

from app.agent.marketplace.prompts import MARKETPLACE_SYSTEM_PROMPT
from app.agent.marketplace.schemas import ProductCard
from app.agent.marketplace.tools import build_marketplace_tools
from app.agent.state import AgentChatRequest, ExecutionContext
from app.core.config import settings
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


class AgentUnavailableError(RuntimeError):
    """模型配置缺失或运行依赖不可用时抛出的异常。"""


class AgentRunResult(BaseModel):
    """在转换为 FastAPI 响应模型前使用的内部结果结构。"""

    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str | None = Field(default=None, alias="conversationId")
    answer: str
    items: list[ProductCard] = Field(default_factory=list)
    trace_id: str = Field(alias="traceId")


WRITE_INTENT_MARKERS = ("下单", "购买", "付款", "发布商品", "修改商品", "删除商品", "发帖")


def _is_write_request(message: str) -> bool:
    """判断用户是否提出了当前不支持的写操作请求。"""
    return any(marker in message for marker in WRITE_INTENT_MARKERS)


def _content_text(message: Any) -> str:
    """从 LangChain 消息对象或消息字典中提取纯文本。"""
    content = message.get("content") if isinstance(message, dict) else getattr(message, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in content
        )
    return str(content or "")


def _extract_answer(result: Any) -> str:
    """从 LangGraph 运行结果中提取最后一条助手文本。"""
    if isinstance(result, dict):
        messages = result.get("messages") or []
        if messages:
            return _content_text(messages[-1])
        return str(result.get("answer") or "")
    return _content_text(result)


def create_marketplace_agent(tools, model_name: str | None = None):
    """创建只包含商品工具的生产环境 LangChain Agent。

    Raises:
        AgentUnavailableError: 未配置模型名称或 LangChain OpenAI 适配器时抛出。
    """
    model_name = model_name or settings.model_name
    if not model_name:
        raise AgentUnavailableError("AI 模型未配置")
    try:
        from langchain.agents import create_agent
        from langchain_openai import ChatOpenAI
    except ImportError as exc:  # 运行环境未安装 LangChain OpenAI 适配器
        raise AgentUnavailableError("AI 运行依赖未安装") from exc

    model = ChatOpenAI(model=model_name, api_key=settings.openai_api_key)
    return create_agent(model=model, tools=tools, system_prompt=MARKETPLACE_SYSTEM_PROMPT)


async def run_agent(
    request: AgentChatRequest,
    client: JavaMarketplaceClient | None = None,
    agent_factory: Callable[[list[Any]], Any] | None = None,
    model_name: str | None = None,
) -> AgentRunResult:
    """执行一次请求，并返回回答和可信的商品卡片。

    ``agent_factory`` 可以注入测试替身，使测试无需调用真实模型。
    """
    if _is_write_request(request.message):
        return AgentRunResult(
            conversationId=request.conversation_id,
            answer="暂不支持下单、发布或修改商品，我可以帮你查询在售商品。",
            items=[],
            traceId=request.trace_id,
        )

    execution_context = ExecutionContext()
    marketplace_client = client or JavaMarketplaceClient(
        user_context=request.user_context,
        trace_id=request.trace_id,
    )
    tools = build_marketplace_tools(
        marketplace_client, request.user_context, request.trace_id, execution_context
    )

    agent = agent_factory(tools) if agent_factory is not None else create_marketplace_agent(tools, model_name)
    if inspect.isawaitable(agent):
        agent = await agent

    try:
        result = await agent.ainvoke(
            {"messages": [{"role": "user", "content": request.message}]}
        )
    except JavaServiceError:
        return AgentRunResult(
            conversationId=request.conversation_id,
            answer="商品服务暂时不可用，请稍后再试。",
            items=[],
            traceId=request.trace_id,
        )
    except Exception as exc:
        if isinstance(exc, AgentUnavailableError):
            raise
        return AgentRunResult(
            conversationId=request.conversation_id,
            answer="AI 服务暂时不可用，请稍后再试。",
            items=[],
            traceId=request.trace_id,
        )

    answer = _extract_answer(result).strip() or "没有找到匹配的商品。"
    return AgentRunResult(
        conversationId=request.conversation_id,
        answer=answer,
        items=execution_context.latest_items,
        traceId=request.trace_id,
    )
