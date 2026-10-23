"""AI 商品查询服务的 FastAPI 应用入口。"""

from fastapi import FastAPI

from app.api.routes_chat import router as chat_router
from app.core.config import settings

# 1. 创建 Web 应用对象；类似 Java 的服务入口，不在这里执行商品查询。
# Uvicorn 的 app.main:app 表示：导入 app/main.py，找到其中名为 app 的对象。
app = FastAPI(title=settings.app_name)


# 2. 注册 GET /health，供启动检查使用；不代表模型、Java 或数据库都已验证可用。
@app.get("/health")
def health() -> dict[str, str]:
    """返回无需鉴权的轻量健康检查结果。"""
    return {"status": "ok", "service": "ai-service"}


# 3. 挂载聊天路由；具体 POST 接口写在 api/routes_chat.py 中。
app.include_router(chat_router)
