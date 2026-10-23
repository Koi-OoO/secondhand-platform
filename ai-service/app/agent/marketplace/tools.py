"""基于 Java 商品只读接口的 LangChain 工具。"""

# 此文件定义模型可以请求调用的函数；真正执行 HTTP 查询的是 java_client.py。

from __future__ import annotations

import json
from typing import Any

try:
    from langchain_core.tools import tool as langchain_tool
except ImportError:  # 依赖未安装时使用下面的备用工具包装器
    langchain_tool = None

from app.agent.marketplace.schemas import ProductCard, SearchFilters, SearchListingsParams, UserContext
from app.agent.state import ExecutionContext
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


class _FallbackTool:
    """LangChain 不可用时使用的简单异步工具适配器。"""

    def __init__(self, function):
        # 保存函数及其说明，让未安装 LangChain 的测试环境也能调用这些只读函数。
        self.function = function
        self.name = function.__name__
        self.description = function.__doc__ or ""

    async def ainvoke(self, payload: dict[str, Any]):
        """使用类似 LangChain 的接口调用被包装的协程。"""
        return await self.function(**payload)


def _make_tool(function):
    """返回 LangChain 工具；依赖不可用时返回便于测试的备用包装器。"""
    # 包装后，框架能把函数名、文档字符串和参数定义转换成发给模型的工具说明。
    return langchain_tool(function) if langchain_tool is not None else _FallbackTool(function)


def _cards_json(cards: list[ProductCard]) -> str:
    """使用接口字段别名，将来自 Java 的商品卡片序列化为 JSON。"""
    # model_dump 转成字典，by_alias 使用 Java/前端字段名；ensure_ascii=False 保留可读中文。
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

    # 两个工具都定义在工厂函数内部，因此能使用本轮传进来的 client 和 execution_context。
    # 工具函数的文档字符串会发给模型，阅读说明放在 # 注释中，保持原有工具描述不变。
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
            # 1. 校验模型给出的关键词、价格、分页等参数，不直接相信模型生成的值。
            query = SearchListingsParams(
                keyword=keyword,
                category_id=category_id,
                min_price=min_price,
                max_price=max_price,
                condition_level=condition_level,
                sort=sort,
                page=page,
                size=size,
            )
            # 2. 转成字典，再用 ** 拆成参数，交给 Java 客户端查询真实商品。
            cards = await client.search_listings(**query.model_dump())
        except (JavaServiceError, ValueError) as exc:
            # 查询失败时清除本轮之前的查询结果，并把错误信息作为工具结果交回 Agent。
            execution_context.latest_items = []
            execution_context.latest_search_filters = None
            return json.dumps({"error": str(exc), "items": []}, ensure_ascii=False)
        # 3. 为前端保存最新一次成功查询的卡片；这是独立于模型回答的真实数据。
        execution_context.latest_items = cards
        # 4. 保存相同的筛选条件，用于“更多类似商品”；不继承工具自己的页码和每页条数。
        execution_context.latest_search_filters = SearchFilters.model_validate(
            query.model_dump(exclude={"page", "size"})
        )
        # 5. 把结果交回模型，供它继续组织回答，而不是直接把这段 JSON 当成聊天回复。
        return _cards_json(cards)

    async def get_listing(product_id: int) -> str:
        """使用 Java 实时数据查询一个当前可见的商品。"""
        try:
            # 1. 用商品 ID 调用 Java 的只读详情接口。
            card = await client.get_listing(product_id)
        except (JavaServiceError, ValueError) as exc:
            execution_context.latest_items = []
            execution_context.latest_search_filters = None
            return json.dumps({"error": str(exc), "items": []}, ensure_ascii=False)
        # 2. 更新前端卡片，再把详情交回模型；已有搜索条件保持原样，便于继续浏览。
        execution_context.latest_items = [card] if card else []
        return _cards_json(execution_context.latest_items)

    # 将这两个函数注册为工具；没有提供下单、改价、发布等写入工具。
    return [_make_tool(search_listings), _make_tool(get_listing)]


__all__ = ["ExecutionContext", "build_marketplace_tools"]
