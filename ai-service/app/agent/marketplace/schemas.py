from pydantic import BaseModel, ConfigDict, Field, model_validator


class UserContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    user_id: int = Field(alias="userId")
    campus: str
    role: str


class ProductCard(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    product_id: int = Field(alias="productId")
    title: str
    price: float
    stock: int
    condition_level: int = Field(alias="conditionLevel")
    trade_location: str = Field(alias="tradeLocation")
    detail_path: str = Field(alias="detailPath")


class ProductDetail(ProductCard):
    pass


class SearchListingsParams(BaseModel):
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
        if self.min_price is not None and self.max_price is not None and self.max_price < self.min_price:
            raise ValueError("max_price must be greater than or equal to min_price")
        return self
