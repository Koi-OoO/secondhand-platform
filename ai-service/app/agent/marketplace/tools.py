"""基于 Java 商品只读接口的 LangChain 工具。"""

from __future__ import annotations

import json
from typing import Any

try:
    from langchain_core.tools import tool as langchain_tool
except ImportError:  # 依赖未安装时使用下面的备用工具包装器
    langchain_tool = None

from app.agent.marketplace.schemas import ProductCard, UserContext
from app.agent.state import ExecutionContext
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


class _FallbackTool:
    """LangChain 不可用时使用的简单异步工具适配器。"""

    def __init__(self, function):
        self.function = function
        self.name = function.__name__
        self.description = function.__doc__ or ""

    async def ainvoke(self, payload: dict[str, Any]):
        """使用类似 LangChain 的接口调用被包装的协程。"""
        return await self.function(**payload)


def _make_tool(function):
    """返回 LangChain 工具；依赖不可用时返回便于测试的备用包装器。"""
    return langchain_tool(function) if langchain_tool is not None else _FallbackTool(function)


def _cards_json(cards: list[ProductCard]) -> str:
    """使用接口字段别名，将来自 Java 的商品卡片序列化为 JSON。"""
    return json.dumps([card.model_dump(by_alias=True) for card in cards], ensure_ascii=False)


def build_marketplace_tools(
    client: JavaMarketplaceClient,
    user_context: UserContext,
    trace_id: str,
    execution_context: ExecutionContext,
):
    """为一次 Agent 运行构建只读商品工具。

    Args:
        client: 作为商品数据来源的 Java 客户端。
        user_context: 当前请求对应的已登录用户上下文。
        trace_id: 在多个服务之间传递的链路追踪 ID。
        execution_context: 保存本次运行最新商品卡片的状态对象。

    Returns:
        Agent 可以使用的搜索工具和详情工具。
    """

    async def search_listings(
        keyword: str | None = None,
        category_id: int | None = None,
        min_price: float | None = None,
        max_price: float | None = None,
        condition_level: int | None = None,
        sort: str = "latest",
        page: int = 1,
        size: int = 10,
    ) -> str:
        """使用 Java 实时数据搜索当前可见的二手商品。"""
        try:
            cards = await client.search_listings(
                keyword=keyword,
                category_id=category_id,
                min_price=min_price,
                max_price=max_price,
                condition_level=condition_level,
                sort=sort,
                page=page,
                size=size,
            )
        except (JavaServiceError, ValueError) as exc:
            execution_context.latest_items = []
            return json.dumps({"error": str(exc), "items": []}, ensure_ascii=False)
        execution_context.latest_items = cards
        return _cards_json(cards)

    async def get_listing(product_id: int) -> str:
        """使用 Java 实时数据查询一个当前可见的商品。"""
        try:
            card = await client.get_listing(product_id)
        except (JavaServiceError, ValueError) as exc:
            execution_context.latest_items = []
            return json.dumps({"error": str(exc), "items": []}, ensure_ascii=False)
        execution_context.latest_items = [card] if card else []
        return _cards_json(execution_context.latest_items)

    return [_make_tool(search_listings), _make_tool(get_listing)]


__all__ = ["ExecutionContext", "build_marketplace_tools"]
