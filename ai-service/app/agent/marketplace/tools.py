from __future__ import annotations

import json
from typing import Any

try:
    from langchain_core.tools import tool as langchain_tool
except ImportError:  # pragma: no cover - only used before optional dependencies are installed
    langchain_tool = None

from app.agent.marketplace.schemas import ProductCard, UserContext
from app.agent.state import ExecutionContext
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


class _FallbackTool:
    def __init__(self, function):
        self.function = function
        self.name = function.__name__
        self.description = function.__doc__ or ""

    async def ainvoke(self, payload: dict[str, Any]):
        return await self.function(**payload)


def _make_tool(function):
    return langchain_tool(function) if langchain_tool is not None else _FallbackTool(function)


def _cards_json(cards: list[ProductCard]) -> str:
    return json.dumps([card.model_dump(by_alias=True) for card in cards], ensure_ascii=False)


def build_marketplace_tools(
    client: JavaMarketplaceClient,
    user_context: UserContext,
    trace_id: str,
    execution_context: ExecutionContext,
):
    """Build only the read-only marketplace tools for one agent run."""

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
        """Search visible second-hand listings using live Java data."""
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
        """Get one visible listing using live Java data."""
        try:
            card = await client.get_listing(product_id)
        except (JavaServiceError, ValueError) as exc:
            execution_context.latest_items = []
            return json.dumps({"error": str(exc), "items": []}, ensure_ascii=False)
        execution_context.latest_items = [card] if card else []
        return _cards_json(execution_context.latest_items)

    return [_make_tool(search_listings), _make_tool(get_listing)]


__all__ = ["ExecutionContext", "build_marketplace_tools"]
