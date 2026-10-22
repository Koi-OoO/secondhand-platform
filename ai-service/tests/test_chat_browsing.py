"""验证真实商品封面与工具筛选条件的完整响应链路，不调用模型或数据库。"""

import json

import pytest
from fastapi.testclient import TestClient

from app.agent.marketplace import schemas
from app.agent.marketplace.schemas import ProductCard, UserContext
from app.agent.marketplace.tools import build_marketplace_tools
from app.agent.runtime import run_agent
from app.agent.state import AgentChatRequest, ExecutionContext
from app.core.config import settings
from app.main import app
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


def raw_product(**extra):
    """模拟Java现有商品JSON，封面仍来自真实images字段。"""
    return {"id": 10, "title": "台灯", "price": 89, "stock": 1,
            "conditionLevel": 2, "tradeLocation": "图书馆门口", **extra}


def test_client_selects_first_valid_cover_by_sort():
    card = JavaMarketplaceClient._card(raw_product(images=[
        None, {"url": "", "sort": 0},
        {"url": "/uploads/gallery.jpg", "sort": 2},
        {"url": "/demo-products/desk-lamp.webp", "sort": 0},
    ]))
    assert getattr(card, "cover_url", None) == "/demo-products/desk-lamp.webp"
    assert card.model_dump(by_alias=True)["coverUrl"] == "/demo-products/desk-lamp.webp"


def test_client_keeps_legacy_cards_without_images_compatible():
    card = JavaMarketplaceClient._card(raw_product())
    assert hasattr(card, "cover_url"), "商品卡缺少可空封面字段"
    assert card.cover_url is None


class RecordingClient:
    """记录实际工具参数，提供封面或模拟Java错误。"""

    def __init__(self, empty=False):
        self.calls = []
        self.empty = empty
        self.fail = False

    async def search_listings(self, **kwargs):
        self.calls.append(kwargs)
        if self.fail:
            raise JavaServiceError("查询失败")
        return [] if self.empty else [ProductCard(
            productId=10, title="台灯", price=89, stock=1, conditionLevel=2,
            tradeLocation="图书馆门口", detailPath="/product/10",
            coverUrl="/demo-products/desk-lamp.webp",
        )]

    async def get_listing(self, product_id):
        return ProductCard(productId=product_id, title="台灯", price=89, stock=1,
                           conditionLevel=2, tradeLocation="图书馆门口", detailPath=f"/product/{product_id}")


def tools_for(client, context):
    """按正常运行方式创建工具，让参数校验和执行上下文参与测试。"""
    return build_marketplace_tools(client, UserContext(userId=7, campus="主校区", role="USER"), "trace", context)


@pytest.mark.asyncio
async def test_search_exposes_actual_filters_without_tool_pagination():
    client, context = RecordingClient(), ExecutionContext()
    tools = tools_for(client, context)
    await tools[0].ainvoke({"keyword": "台灯", "category_id": 3, "min_price": 0,
                           "max_price": 100, "condition_level": 2, "sort": "price_asc", "page": 2, "size": 3})
    filters = getattr(context, "latest_search_filters", None)
    assert filters is not None, "成功搜索缺少实际筛选条件"
    assert filters.model_dump(by_alias=True, exclude_none=True) == {
        "keyword": "台灯", "categoryId": 3, "minPrice": 0, "maxPrice": 100,
        "conditionLevel": 2, "sort": "price_asc",
    }
    assert client.calls[0]["page"] == 2 and client.calls[0]["size"] == 3


@pytest.mark.asyncio
async def test_empty_search_preserves_criteria_and_failure_clears_them():
    client, context = RecordingClient(empty=True), ExecutionContext()
    tools = tools_for(client, context)
    await tools[0].ainvoke({"keyword": "台灯", "max_price": 100})
    assert getattr(context, "latest_search_filters", None) is not None
    assert context.latest_items == []
    client.fail = True
    result = json.loads(await tools[0].ainvoke({"keyword": "笔记本"}))
    assert "error" in result
    assert context.latest_search_filters is None


@pytest.mark.asyncio
async def test_invalid_tool_filter_is_rejected_before_client_call():
    client, context = RecordingClient(), ExecutionContext()
    result = json.loads(await tools_for(client, context)[0].ainvoke({"min_price": 200, "max_price": 100}))
    assert isinstance(result, dict) and "error" in result
    assert client.calls == []
    assert getattr(context, "latest_search_filters", None) is None


@pytest.mark.asyncio
async def test_keyword_is_canonical_before_http_and_metadata():
    client, context = RecordingClient(), ExecutionContext()
    await tools_for(client, context)[0].ainvoke({"keyword": "  台灯  "})
    assert client.calls[0]["keyword"] == "台灯"
    assert context.latest_search_filters.keyword == client.calls[0]["keyword"]


@pytest.mark.asyncio
async def test_oversized_keyword_cannot_be_silently_truncated_in_more_link():
    client, context = RecordingClient(), ExecutionContext()
    result = json.loads(await tools_for(client, context)[0].ainvoke({"keyword": "教材" * 101}))
    assert isinstance(result, dict) and "error" in result
    assert client.calls == []


class SearchAgent:
    """模拟模型调用工具，回答文本中的数值不能取代实际查询参数。"""

    def __init__(self, tools):
        self.tools = tools

    async def ainvoke(self, payload):
        await self.tools[0].ainvoke({"keyword": "台灯", "max_price": 100})
        return {"messages": [{"role": "assistant", "content": "商品筛选以实际工具参数为准。"}]}


def request():
    """有效的内部请求，身份来自Java而不是消息内容。"""
    return AgentChatRequest(conversationId="c-1", message="找100元内台灯",
                            userContext={"userId": 7, "campus": "主校区", "role": "USER"}, traceId="trace-1")


@pytest.mark.asyncio
async def test_runtime_returns_cover_and_search_filters():
    result = await run_agent(request(), client=RecordingClient(), agent_factory=lambda tools: SearchAgent(tools))
    dumped = result.model_dump(by_alias=True, exclude_none=True)
    assert dumped.get("searchFilters") == {"keyword": "台灯", "maxPrice": 100, "sort": "latest"}
    assert dumped["items"][0]["coverUrl"] == "/demo-products/desk-lamp.webp"


@pytest.mark.asyncio
async def test_detail_after_search_keeps_this_search_browsing_criteria():
    context = ExecutionContext()
    tools = tools_for(RecordingClient(), context)
    await tools[0].ainvoke({"keyword": "台灯", "max_price": 100})
    await tools[1].ainvoke({"product_id": 10})
    filters = getattr(context, "latest_search_filters", None)
    assert filters is not None and filters.max_price == 100


def test_http_route_keeps_cover_and_filter_aliases(monkeypatch):
    assert hasattr(schemas, "SearchFilters"), "缺少筛选条件响应模型"

    async def fake_run_agent(payload):
        return await run_agent(payload, client=RecordingClient(), agent_factory=lambda tools: SearchAgent(tools))

    monkeypatch.setattr("app.api.routes_chat.run_agent", fake_run_agent)
    monkeypatch.setattr(settings, "internal_token", "test-token")
    response = TestClient(app).post("/internal/agent/chat", headers={"X-Internal-Service-Token": "test-token"},
                                    json=request().model_dump(by_alias=True))
    assert response.status_code == 200
    body = response.json()
    assert body["searchFilters"]["maxPrice"] == 100
    assert body["items"][0]["coverUrl"] == "/demo-products/desk-lamp.webp"
