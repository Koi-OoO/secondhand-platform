"""Async HTTP client for the Java marketplace internal APIs."""

from __future__ import annotations

from typing import Any
from urllib.parse import quote

import httpx

from app.agent.marketplace.schemas import ProductCard, ProductDetail, SearchListingsParams, UserContext
from app.core.config import settings


class JavaServiceError(RuntimeError):
    """A stable, user-safe error from the Java marketplace service."""


class JavaMarketplaceClient:
    """Query visible products from Java without accessing its database directly."""

    def __init__(
        self,
        base_url: str | None = None,
        internal_token: str | None = None,
        user_context: UserContext | None = None,
        trace_id: str | None = None,
        http_client: httpx.AsyncClient | None = None,
    ) -> None:
        self.base_url = (base_url or settings.java_backend_base_url).rstrip("/")
        self.internal_token = internal_token or settings.internal_token
        self.user_context = user_context
        self.trace_id = trace_id or ""
        self._http_client = http_client

    def _headers(self) -> dict[str, str]:
        """Build service-authentication, tracing, and user-context headers."""
        headers = {
            "X-Internal-Service-Token": self.internal_token,
            "X-Trace-Id": self.trace_id,
        }
        if self.user_context is not None:
            headers["X-User-Id"] = str(self.user_context.user_id)
            headers["X-Campus"] = quote(self.user_context.campus, safe="")
            headers["X-User-Role"] = quote(self.user_context.role, safe="")
        return headers

    async def _request(self, method: str, path: str, params: dict[str, Any] | None = None) -> Any:
        """Make one Java request and unwrap its unified ``Result`` envelope."""
        url = f"{self.base_url}{path}"
        try:
            if self._http_client is not None:
                response = await self._http_client.request(
                    method, url, params=params, headers=self._headers()
                )
            else:
                timeout = httpx.Timeout(
                    connect=settings.java_connect_timeout_seconds,
                    read=settings.java_read_timeout_seconds,
                    write=settings.java_read_timeout_seconds,
                    pool=settings.java_connect_timeout_seconds,
                )
                async with httpx.AsyncClient(timeout=timeout) as client:
                    response = await client.request(
                        method, url, params=params, headers=self._headers()
                    )
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise JavaServiceError("商品服务暂时不可用") from exc

        if payload.get("code") != 200:
            raise JavaServiceError(str(payload.get("message") or "商品查询失败"))
        return payload.get("data")

    @staticmethod
    def _card(raw: dict[str, Any]) -> ProductCard:
        """Convert Java product JSON into the public Agent card shape."""
        product_id = raw.get("productId", raw.get("id"))
        if product_id is None:
            raise JavaServiceError("商品数据缺少商品 ID")
        return ProductCard(
            productId=int(product_id),
            title=str(raw.get("title") or ""),
            price=float(raw.get("price") or 0),
            stock=int(raw.get("stock") or 0),
            conditionLevel=int(raw.get("conditionLevel") or 0),
            tradeLocation=str(raw.get("tradeLocation") or ""),
            detailPath=str(raw.get("detailPath") or f"/product/{product_id}"),
        )

    async def search_listings(
        self,
        keyword: str | None = None,
        category_id: int | None = None,
        min_price: float | None = None,
        max_price: float | None = None,
        condition_level: int | None = None,
        sort: str = "latest",
        page: int = 1,
        size: int = 10,
    ) -> list[ProductCard]:
        """Search visible listings with validated, bounded filters.

        Raises:
            ValueError: If a filter violates the search bounds.
            JavaServiceError: If Java returns an HTTP or business error.
        """
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
        data = await self._request(
            "GET",
            "/internal/marketplace/listings/search",
            params={
                key: value
                for key, value in {
                    "keyword": query.keyword,
                    "categoryId": query.category_id,
                    "minPrice": query.min_price,
                    "maxPrice": query.max_price,
                    "conditionLevel": query.condition_level,
                    "page": query.page,
                    "size": query.size,
                    "sort": query.sort,
                }.items()
                if value is not None
            },
        )
        records = (data or {}).get("records", [])
        return [self._card(record) for record in records]

    async def get_listing(self, product_id: int) -> ProductDetail | None:
        """Fetch one visible listing by positive product ID."""
        if product_id < 1:
            raise ValueError("product_id must be positive")
        data = await self._request("GET", f"/internal/marketplace/listings/{product_id}")
        if not data:
            return None
        card = self._card(data)
        return ProductDetail.model_validate(card.model_dump(by_alias=True))
