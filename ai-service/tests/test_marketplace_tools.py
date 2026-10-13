import json

import pytest

from app.agent.marketplace.tools import ExecutionContext, build_marketplace_tools
from app.agent.marketplace.schemas import ProductCard, UserContext


class FakeMarketplaceClient:
    async def search_listings(self, **kwargs):
        return [
            ProductCard(
                productId=10,
                title="台灯",
                price=89,
                stock=1,
                conditionLevel=2,
                tradeLocation="主校区食堂门口",
                detailPath="/product/10",
            )
        ]

    async def get_listing(self, product_id: int):
        if product_id < 1:
            raise ValueError("product_id must be positive")
        return ProductCard(
            productId=product_id,
            title="台灯",
            price=89,
            stock=1,
            conditionLevel=2,
            tradeLocation="主校区食堂门口",
            detailPath=f"/product/{product_id}",
        )


@pytest.mark.asyncio
async def test_search_tool_returns_java_cards_and_updates_execution_context() -> None:
    context = ExecutionContext()
    tools = build_marketplace_tools(
        FakeMarketplaceClient(), UserContext(userId=7, campus="东校区", role="USER"), "trace-1", context
    )

    result = await tools[0].ainvoke({"keyword": "台灯", "size": 10})

    payload = json.loads(result)
    assert payload[0]["productId"] == 10
    assert context.latest_items[0].product_id == 10


@pytest.mark.asyncio
async def test_detail_tool_returns_error_json_for_invalid_id() -> None:
    tools = build_marketplace_tools(
        FakeMarketplaceClient(), UserContext(userId=7, campus="东校区", role="USER"), "trace-1", ExecutionContext()
    )

    result = await tools[1].ainvoke({"product_id": 0})

    payload = json.loads(result)
    assert payload["items"] == []
    assert "product_id must be positive" in payload["error"]
