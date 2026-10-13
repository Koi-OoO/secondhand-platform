import secrets

from fastapi import Header, HTTPException, status

from app.core.config import settings


async def require_internal_token(
    token: str | None = Header(default=None, alias="X-Internal-Service-Token"),
) -> None:
    expected = settings.internal_token
    if not token or not expected or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="未授权")
