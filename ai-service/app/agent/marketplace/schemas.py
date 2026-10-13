"""商品工具和 Agent 状态共享的 Pydantic 数据模型。"""

from pydantic import BaseModel, ConfigDict, Field, model_validator


class UserContext(BaseModel):
    """由 Java Session 层传入的已登录用户上下文。"""

    model_config = ConfigDict(populate_by_name=True)

    user_id: int = Field(alias="userId")
    campus: str
    role: str


class ProductCard(BaseModel):
    """允许 Agent 展示给用户的安全商品字段。"""

    model_config = ConfigDict(populate_by_name=True)

    product_id: int = Field(alias="productId")
    title: str
    price: float
    stock: int
    condition_level: int = Field(alias="conditionLevel")
    trade_location: str = Field(alias="tradeLocation")
    detail_path: str = Field(alias="detailPath")


class ProductDetail(ProductCard):
    """Java 商品详情接口返回的商品详情数据结构。"""

    pass


class SearchListingsParams(BaseModel):
    """只读商品搜索工具接受的、经过校验的筛选条件。"""

    keyword: str | None = None
    category_id: int | None = Field(default=None, ge=1)
    min_price: float | None = Field(default=None, ge=0)
    max_price: float | None = Field(default=None, ge=0)
    condition_level: int | None = Field(default=None, ge=1, le=4)
    sort: str = "latest"
    page: int = Field(default=1, ge=1)
    size: int = Field(default=10, ge=1, le=20)

    @model_validator(mode="after")
    def validate_price_range(self) -> "SearchListingsParams":
        """在发起 HTTP 请求前拒绝反向价格区间。"""
        if self.min_price is not None and self.max_price is not None and self.max_price < self.min_price:
            raise ValueError("max_price must be greater than or equal to min_price")
        return self
