# 测试注册给模型的商品工具：返回给模型的 JSON 与保存给前端的卡片都必须来自客户端结果。
import json

import pytest

from app.agent.marketplace.tools import ExecutionContext, build_marketplace_tools
from app.agent.marketplace.schemas import ProductCard, UserContext


class FakeMarketplaceClient:
    # 用固定商品替代 Java 查询结果，专门验证工具如何传递和保存这些数据。
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
    # 1. 准备本轮数据容器，并创建工具；列表中第一个是搜索，第二个是详情。
    context = ExecutionContext()
    tools = build_marketplace_tools(
        FakeMarketplaceClient(), UserContext(userId=7, campus="东校区", role="USER"), "trace-1", context
    )

    # 2. 模拟框架根据模型提供的参数执行搜索工具。
    result = await tools[0].ainvoke({"keyword": "台灯", "size": 10})

    # 3. 分别检查给模型的结果和给前端保存的商品，防止两者不一致。
    payload = json.loads(result)
    assert payload[0]["productId"] == 10
    assert context.latest_items[0].product_id == 10


@pytest.mark.asyncio
async def test_detail_tool_returns_error_json_for_invalid_id() -> None:
    # 无效商品 ID 应变成工具错误结果，不产生虚构卡片。
    tools = build_marketplace_tools(
        FakeMarketplaceClient(), UserContext(userId=7, campus="东校区", role="USER"), "trace-1", ExecutionContext()
    )

    result = await tools[1].ainvoke({"product_id": 0})

    payload = json.loads(result)
    assert payload["items"] == []
    assert "product_id must be positive" in payload["error"]
