from fastapi import FastAPI

from app.api.routes_chat import router as chat_router
from app.core.config import settings

app = FastAPI(title=settings.app_name)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-service"}


app.include_router(chat_router)
