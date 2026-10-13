"""AI 商品查询服务的 FastAPI 应用入口。"""

from fastapi import FastAPI

from app.api.routes_chat import router as chat_router
from app.core.config import settings

# Uvicorn 启动时会通过 ``app.main:app`` 导入这个应用对象。
app = FastAPI(title=settings.app_name)


@app.get("/health")
def health() -> dict[str, str]:
    """返回无需鉴权的轻量健康检查结果。"""
    return {"status": "ok", "service": "ai-service"}


app.include_router(chat_router)
