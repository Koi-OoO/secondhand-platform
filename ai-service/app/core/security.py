"""服务间请求的鉴权辅助函数。"""

import secrets

from fastapi import Header, HTTPException, status

from app.core.config import settings


async def require_internal_token(
    # 1. 从请求头读取服务令牌；没有传入时得到 None，不把令牌从聊天内容中取出。
    token: str | None = Header(default=None, alias="X-Internal-Service-Token"),
) -> None:
    """校验 Java 与 Python 之间共享的内部服务令牌。

    ``compare_digest`` 用于进行相对安全的令牌比较；校验失败时会在
    Agent 执行前直接返回 HTTP 401。
    """
    # 2. 与 Python 本地配置比较；Java 发出的令牌必须和这里的配置一致。
    expected = settings.internal_token
    if not token or not expected or not secrets.compare_digest(token, expected):
        # 3. 不匹配就直接结束请求，不进入 Agent，也不调用收费模型。
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="未授权")
