from fastapi import APIRouter

router = APIRouter(prefix="/internal/agent", tags=["agent"])

__all__ = ["router"]
