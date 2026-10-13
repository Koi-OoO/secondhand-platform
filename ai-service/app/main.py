"""FastAPI application entry point for the AI marketplace service."""

from fastapi import FastAPI

from app.api.routes_chat import router as chat_router
from app.core.config import settings

# Uvicorn imports this object through ``app.main:app`` when the service starts.
app = FastAPI(title=settings.app_name)


@app.get("/health")
def health() -> dict[str, str]:
    """Return a lightweight liveness response without requiring authentication."""
    return {"status": "ok", "service": "ai-service"}


app.include_router(chat_router)
