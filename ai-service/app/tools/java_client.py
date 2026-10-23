"""调用 Java 商品内部接口的异步 HTTP 客户端。"""

# 调用链：Agent 工具 → 本文件发 HTTP 请求 → Java 查询商品 → 本文件转成商品卡片。

from __future__ import annotations

from typing import Any
from urllib.parse import quote

import httpx

from app.agent.marketplace.schemas import ProductCard, ProductDetail, SearchListingsParams, UserContext
from app.core.config import settings


class JavaServiceError(RuntimeError):
    """Java 商品服务发生异常时使用的稳定、安全错误。"""


class JavaMarketplaceClient:
    """通过 Java 查询可见商品，不直接访问 Java 数据库。"""

    def __init__(
        self,
        base_url: str | None = None,
        internal_token: str | None = None,
        user_context: UserContext | None = None,
        trace_id: str | None = None,
        http_client: httpx.AsyncClient | None = None,
    ) -> None:
        # 1. 保存 Java 地址、服务令牌和本轮身份信息；不在 Python 中创建数据库连接。
        self.base_url = (base_url or settings.java_backend_base_url).rstrip("/")
        self.internal_token = internal_token or settings.internal_token
        self.user_context = user_context
        self.trace_id = trace_id or ""
        # 2. 测试可传入带 MockTransport 的客户端，让请求只进入测试函数而不访问 Java。
        self._http_client = http_client

    def _headers(self) -> dict[str, str]:
        """构造服务鉴权、链路追踪和用户上下文请求头。"""
        headers = {
            "X-Internal-Service-Token": self.internal_token,
            "X-Trace-Id": self.trace_id,
        }
        if self.user_context is not None:
            # 用户信息由 Java 聊天入口传入；中文校区先编码，适配 HTTP 请求头传输。
            headers["X-User-Id"] = str(self.user_context.user_id)
            headers["X-Campus"] = quote(self.user_context.campus, safe="")
            headers["X-User-Role"] = quote(self.user_context.role, safe="")
        return headers

    async def _request(self, method: str, path: str, params: dict[str, Any] | None = None) -> Any:
        """发起一次 Java 请求，并拆出统一的 ``Result`` 响应数据。"""
        # 1. 拼出 Java 完整接口地址；params 是附在 URL 上的查询参数。
        url = f"{self.base_url}{path}"
        try:
            if self._http_client is not None:
                response = await self._http_client.request(
                    method, url, params=params, headers=self._headers()
                )
            else:
                # 2. 分别限制连接和等待响应的时间，避免 Java 不可用时一直等待。
                timeout = httpx.Timeout(
                    connect=settings.java_connect_timeout_seconds,
                    read=settings.java_read_timeout_seconds,
                    write=settings.java_read_timeout_seconds,
                    pool=settings.java_connect_timeout_seconds,
                )
                async with httpx.AsyncClient(timeout=timeout) as client:
                    # async with 在请求结束后关闭客户端；await 等待 HTTP 请求完成。
                    response = await client.request(
                        method, url, params=params, headers=self._headers()
                    )
            # 3. 先检查 HTTP 状态，再解析 JSON；HTTP 503 等不能当成“没有商品”。
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise JavaServiceError("商品服务暂时不可用") from exc

        # 4. HTTP 200 也可能携带业务失败，继续检查 Java Result 中的 code。
        if payload.get("code") != 200:
            raise JavaServiceError(str(payload.get("message") or "商品查询失败"))
        # 5. 只返回业务数据 data，剥掉 code/message 外壳。
        return payload.get("data")

    @staticmethod
    def _cover_url(raw: dict[str, Any]) -> str | None:
        """保留已有封面字段，或从Java的images列表按sort选择有效封面。"""
        # 1. 有明确封面地址时直接沿用，不由模型生成图片地址。
        direct = raw.get("coverUrl")
        if isinstance(direct, str) and direct.strip():
            return direct.strip()
        # 2. 否则从图片列表筛掉空值和异常记录，再选 sort 最小的有效图片。
        images = raw.get("images")
        if not isinstance(images, list):
            return None
        valid = [image for image in images if isinstance(image, dict)
                 and isinstance(image.get("url"), str) and image["url"].strip()]
        if not valid:
            return None
        # 兼容缺少sort的旧图片数据；不让异常图片记录影响整个商品查询。
        cover = min(valid, key=lambda image: image.get("sort")
                    if isinstance(image.get("sort"), (int, float)) else 0)
        return cover["url"].strip()

    @staticmethod
    def _card(raw: dict[str, Any]) -> ProductCard:
        """将 Java 商品 JSON 转换为 Agent 对外使用的商品卡片结构。"""
        # 兼容 Java 的 id 和卡片的 productId，只挑选前端需要的字段。
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
            coverUrl=JavaMarketplaceClient._cover_url(raw),
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
        """使用经过校验且有边界限制的筛选条件搜索在售商品。

        Raises:
            ValueError: 筛选条件超出允许范围时抛出。
            JavaServiceError: Java 返回 HTTP 错误或业务错误时抛出。
        """
        # 1. 客户端本身也校验参数，其他调用者绕过 Agent 工具时仍有相同边界。
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
        # 2. 请求 Java 搜索接口，将 Python 下划线字段转换为 Java 的驼峰字段。
        # 下面的字典推导式只过滤 None，因此 max_price=0 等有效零值不会被丢掉。
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
        # 3. Java 分页结果中 records 才是商品列表，将每项转换成统一卡片。
        records = (data or {}).get("records", [])
        return [self._card(record) for record in records]

    async def get_listing(self, product_id: int) -> ProductDetail | None:
        """根据正整数商品 ID 查询一个在售商品详情。"""
        # 1. 无效 ID 直接拒绝，不向 Java 发送请求。
        if product_id < 1:
            raise ValueError("product_id must be positive")
        # 2. 调用只读详情接口；Java 负责商品可见性判断，AI 查询不增加浏览量。
        data = await self._request("GET", f"/internal/marketplace/listings/{product_id}")
        if not data:
            return None
        # 3. 复用卡片字段转换，返回给工具，不把整个数据库对象暴露给模型。
        card = self._card(data)
        return ProductDetail.model_validate(card.model_dump(by_alias=True))
