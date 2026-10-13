from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration loaded from environment variables or .env."""

    app_name: str = "ai-service"
    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    log_level: str = "INFO"
    internal_token: str = "dev-ai-internal-token"
    java_backend_base_url: str = "http://localhost:8080"
    java_connect_timeout_seconds: float = 1.0
    java_read_timeout_seconds: float = 30.0
    model_name: str | None = None
    langchain_tracing_v2: bool = False
    langchain_project: str = "secondhand-ai-service"
    langchain_api_key: str | None = None
    openai_api_key: str | None = None

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @model_validator(mode="after")
    def require_production_token(self) -> "Settings":
        if self.app_env.lower() in {"prod", "production"} and (
            not self.internal_token or self.internal_token == "dev-ai-internal-token"
        ):
            raise ValueError("生产环境必须配置 INTERNAL_TOKEN")
        return self


settings = Settings()
