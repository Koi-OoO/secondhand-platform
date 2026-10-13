import httpx
import pytest

from app.agent.marketplace.schemas import UserContext
from app.tools.java_client import JavaMarketplaceClient, JavaServiceError


@pytest.mark.asyncio
async def test_search_listings_parses_java_result_and_sends_context_headers() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["headers"] = dict(request.headers)
        return httpx.Response(
            200,
            json={
                "code": 200,
                "message": "成功",
                "data": {
                    "records": [
                        {
                            "id": 10,
                            "title": "台灯",
                            "price": 89.0,
                            "stock": 1,
                            "conditionLevel": 2,
                            "tradeLocation": "主校区食堂门口",
                        }
                    ],
                    "total": 1,
                },
            },
        )

    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    client = JavaMarketplaceClient(
        base_url="http://java.test",
        internal_token="token",
        user_context=UserContext(userId=7, campus="东校区", role="USER"),
        trace_id="trace-1",
        http_client=http_client,
    )

    cards = await client.search_listings(keyword="台灯", size=10)

    assert cards[0].product_id == 10
    assert cards[0].detail_path == "/product/10"
    assert seen["url"] == "http://java.test/internal/marketplace/listings/search?keyword=%E5%8F%B0%E7%81%AF&page=1&size=10&sort=latest"
    headers = seen["headers"]
    assert headers["x-internal-service-token"] == "token"
    assert headers["x-trace-id"] == "trace-1"
    assert headers["x-user-id"] == "7"
    await http_client.aclose()


@pytest.mark.asyncio
async def test_java_business_error_raises_clear_exception() -> None:
    http_client = httpx.AsyncClient(
        transport=httpx.MockTransport(
            lambda request: httpx.Response(200, json={"code": 500, "message": "查询失败", "data": None})
        )
    )
    client = JavaMarketplaceClient(base_url="http://java.test", internal_token="token", http_client=http_client)

    with pytest.raises(JavaServiceError, match="查询失败"):
        await client.search_listings()
    await http_client.aclose()


@pytest.mark.asyncio
async def test_java_http_5xx_raises_clear_exception() -> None:
    http_client = httpx.AsyncClient(
        transport=httpx.MockTransport(lambda request: httpx.Response(503, json={"message": "down"}))
    )
    client = JavaMarketplaceClient(base_url="http://java.test", internal_token="token", http_client=http_client)

    with pytest.raises(JavaServiceError, match="商品服务暂时不可用"):
        await client.search_listings()
    await http_client.aclose()


@pytest.mark.asyncio
async def test_search_rejects_invalid_price_and_size_before_http_call() -> None:
    client = JavaMarketplaceClient(base_url="http://java.test", internal_token="token")

    with pytest.raises(ValueError):
        await client.search_listings(max_price=-1)
    with pytest.raises(ValueError):
        await client.search_listings(size=21)


@pytest.mark.asyncio
async def test_get_listing_rejects_non_positive_id() -> None:
    client = JavaMarketplaceClient(base_url="http://java.test", internal_token="token")

    with pytest.raises(ValueError):
        await client.get_listing(0)
