"""Authentication helpers for service-to-service requests."""

import secrets

from fastapi import Header, HTTPException, status

from app.core.config import settings


async def require_internal_token(
    token: str | None = Header(default=None, alias="X-Internal-Service-Token"),
) -> None:
    """Require the shared Java/Python internal service token.

    ``compare_digest`` keeps the comparison constant-time enough for this
    boundary and the dependency raises HTTP 401 before the Agent is invoked.
    """
    expected = settings.internal_token
    if not token or not expected or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="未授权")
