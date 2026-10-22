"""商品工具和 Agent 状态共享的 Pydantic 数据模型。"""

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


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
    cover_url: str | None = Field(default=None, alias="coverUrl")  # Java商品图片中的真实封面


class ProductDetail(ProductCard):
    """Java 商品详情接口返回的商品详情数据结构。"""

    pass


class SearchFilters(BaseModel):
    """成功商品搜索实际使用的条件，供前端继续浏览；不包含工具分页。"""

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    keyword: str | None = Field(default=None, max_length=200)
    category_id: int | None = Field(default=None, ge=1, alias="categoryId")
    min_price: float | None = Field(default=None, ge=0, allow_inf_nan=False, alias="minPrice")
    max_price: float | None = Field(default=None, ge=0, allow_inf_nan=False, alias="maxPrice")
    condition_level: int | None = Field(default=None, ge=1, le=4, alias="conditionLevel")
    sort: str = "latest"

    @field_validator("keyword")
    @classmethod
    def normalize_keyword(cls, value: str | None) -> str | None:
        """在查询前统一去除首尾空格，空关键词视为未指定，供前端原样继续搜索。"""
        return value or None

    @model_validator(mode="after")
    def validate_price_range(self) -> "SearchFilters":
        """在发起 HTTP 请求前拒绝反向价格区间。"""
        if self.min_price is not None and self.max_price is not None and self.max_price < self.min_price:
            raise ValueError("max_price must be greater than or equal to min_price")
        return self


class SearchListingsParams(SearchFilters):
    """只读商品搜索工具接受的筛选条件及有限分页参数。"""

    page: int = Field(default=1, ge=1)
    size: int = Field(default=10, ge=1, le=20)
