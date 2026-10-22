"""从环境变量或 ``.env`` 文件读取的类型化运行配置。"""

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """FastAPI、Agent 和 Java 客户端共同使用的配置。"""

    app_name: str = "ai-service"  # 服务名称
    app_env: str = "development"  # 运行环境
    app_host: str = "0.0.0.0"  # 监听地址
    app_port: int = 8000  # 监听端口
    log_level: str = "INFO"  # 日志级别
    internal_token: str = "dev-ai-internal-token"  # Java/Python 服务令牌
    java_backend_base_url: str = "http://localhost:8080"  # Java 后端地址
    java_connect_timeout_seconds: float = 1.0  # 连接 Java 的超时时间
    java_read_timeout_seconds: float = 30.0  # 读取 Java 响应的超时时间
    model_name: str | None = None  # 语言模型名称
    langchain_tracing_v2: bool = False  # 是否启用 LangChain 追踪
    langchain_project: str = "secondhand-ai-service"  # LangChain 项目名
    langchain_api_key: str | None = None  # LangSmith API Key
    openai_api_key: str | None = None  # 模型服务 API Key
    openai_base_url: str | None = None  # 兼容接口地址；未填写时使用 OpenAI 默认地址

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @model_validator(mode="after")
    def require_production_token(self) -> "Settings":
        """禁止生产环境继续使用开发环境默认令牌。"""
        if self.app_env.lower() in {"prod", "production"} and (
            not self.internal_token or self.internal_token == "dev-ai-internal-token"
        ):
            raise ValueError("生产环境必须配置 INTERNAL_TOKEN")
        return self


settings = Settings()
