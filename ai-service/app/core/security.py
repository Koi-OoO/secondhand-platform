"""服务间请求的鉴权辅助函数。"""

import secrets

from fastapi import Header, HTTPException, status

from app.core.config import settings


async def require_internal_token(
    token: str | None = Header(default=None, alias="X-Internal-Service-Token"),
) -> None:
    """校验 Java 与 Python 之间共享的内部服务令牌。

    ``compare_digest`` 用于进行相对安全的令牌比较；校验失败时会在
    Agent 执行前直接返回 HTTP 401。
    """
    expected = settings.internal_token
    if not token or not expected or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="未授权")
