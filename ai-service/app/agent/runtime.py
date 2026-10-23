"""商品查询 Agent 的创建工厂和单轮执行流程。"""

# 阅读主线：run_agent 接收问题 → create_marketplace_agent 组装助手 → ainvoke 执行 → 返回回答和卡片。

from __future__ import annotations

import inspect
from typing import Any, Callable
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field

from app.agent.marketplace.prompts import MARKETPLACE_SYSTEM_PROMPT
from app.agent.marketplace.schemas import ProductCard, SearchFilters
from app.agent.marketplace.tools import build_marketplace_tools
from app.agent.state import AgentChatRequest, ExecutionContext
from app.core.config import settings
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


class AgentUnavailableError(RuntimeError):
    """模型配置缺失或运行依赖不可用时抛出的异常。"""


class AgentRunResult(BaseModel):
    """在转换为 FastAPI 响应模型前使用的内部结果结构。"""

    # Python 内部用 conversation_id，对外 JSON 用 conversationId；两种名字均可用于创建对象。
    model_config = ConfigDict(populate_by_name=True)

    conversation_id: str | None = Field(default=None, alias="conversationId")
    answer: str
    items: list[ProductCard] = Field(default_factory=list)
    search_filters: SearchFilters | None = Field(default=None, alias="searchFilters")
    trace_id: str = Field(alias="traceId")


WRITE_INTENT_MARKERS = ("下单", "购买", "付款", "发布商品", "修改商品", "删除商品", "发帖")


def _is_write_request(message: str) -> bool:
    """判断用户是否提出了当前不支持的写操作请求。"""
    # 这是关键词拦截，不是完整的意图识别；Agent 另外也只注册了两个只读工具。
    return any(marker in message for marker in WRITE_INTENT_MARKERS)


def _content_text(message: Any) -> str:
    """从 LangChain 消息对象或消息字典中提取纯文本。"""
    # 1. 兼容测试使用的字典和框架返回的消息对象。
    content = message.get("content") if isinstance(message, dict) else getattr(message, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        # 2. 内容也可能由多个片段组成，把其中的文本拼起来。
        return "".join(
            part.get("text", "") if isinstance(part, dict) else str(part) for part in content
        )
    return str(content or "")


def _extract_answer(result: Any) -> str:
    """从 LangGraph 运行结果中提取最后一条助手文本。"""
    # result 是整轮 Agent 的执行结果；商品卡片不从这段模型文字中解析。
    if isinstance(result, dict):
        messages = result.get("messages") or []
        if messages:
            # 正常完成后取最后一条消息，作为这一轮助手的回答。
            return _content_text(messages[-1])
        return str(result.get("answer") or "")
    return _content_text(result)


def create_marketplace_agent(tools, model_name: str | None = None):
    """创建只包含商品工具的生产环境 LangChain Agent。

    Raises:
        AgentUnavailableError: 未配置模型名称或 LangChain OpenAI 适配器时抛出。
    """
    # 1. 确认使用哪个模型；未显式传入时，读取项目配置。
    model_name = model_name or settings.model_name
    if not model_name:
        raise AgentUnavailableError("AI 模型未配置")
    # 在真正创建 Agent 时加载依赖；缺少依赖时转换成项目统一异常。
    try:
        from langchain.agents import create_agent
        from langchain_openai import ChatOpenAI
    except ImportError as exc:  # 运行环境未安装 LangChain OpenAI 适配器
        raise AgentUnavailableError("AI 运行依赖未安装") from exc

    # 2. 准备模型名称、访问密钥和服务地址。这里只传递配置，不输出密钥。
    model_options = {
        'model': model_name,
        'api_key': settings.openai_api_key,
        'base_url': settings.openai_base_url or None,
    }
    if urlsplit(settings.openai_base_url or '').hostname == 'api.deepseek.com':
        # ChatOpenAI 不保留 DeepSeek 的 reasoning_content，首次联调使用非思考模式，
        # 避免工具结果回传时因缺少推理内容而失败。
        model_options['extra_body'] = {'thinking': {'type': 'disabled'}}
    # 3. 创建远程模型客户端；** 把字典拆成参数，此时还没有发送用户问题。
    model = ChatOpenAI(**model_options)
    # 4. 将模型、商品工具和系统规则组装成 Agent；执行时由框架协调模型与工具。
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
    # 1. 先拦截当前不支持的写操作，命中后直接返回，不调用模型。
    if _is_write_request(request.message):
        return AgentRunResult(
            conversationId=request.conversation_id,
            answer="暂不支持下单、发布或修改商品，我可以帮你查询在售商品。",
            items=[],
            traceId=request.trace_id,
        )

    # 2. 创建本轮的数据容器和 Java 客户端；这里的 context 不是跨轮聊天记忆。
    execution_context = ExecutionContext()
    marketplace_client = client or JavaMarketplaceClient(
        user_context=request.user_context,
        trace_id=request.trace_id,
    )
    # 3. 为本轮创建商品查询工具，工具结果会写入上面的 execution_context。
    tools = build_marketplace_tools(
        marketplace_client, request.user_context, request.trace_id, execution_context
    )

    # 4. 正常请求创建真实 Agent；测试可传入 agent_factory，用替身避免调用收费模型。
    agent = agent_factory(tools) if agent_factory is not None else create_marketplace_agent(tools, model_name)
    if inspect.isawaitable(agent):
        # 兼容异步创建的测试替身；普通 create_agent 的返回值不需要这一步。
        agent = await agent

    try:
        # 5. 开始执行：模型提出工具调用 → Python 执行工具 → 结果交回模型 → 得到最终回答。
        # await 等待整轮执行完成；目前只传当前问题，没有加载以前的聊天记录。
        result = await agent.ainvoke(
            {"messages": [{"role": "user", "content": request.message}]}
        )
    except JavaServiceError:
        # 本轮失败时不展示商品卡片，避免把失败解释为一次成功搜索。
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

    # 6. 回答取自 Agent 消息，卡片和筛选条件取自实际工具结果，两者分别组装。
    answer = _extract_answer(result).strip() or "没有找到匹配的商品。"
    return AgentRunResult(
        conversationId=request.conversation_id,
        answer=answer,
        items=execution_context.latest_items,
        searchFilters=execution_context.latest_search_filters,
        traceId=request.trace_id,
    )
