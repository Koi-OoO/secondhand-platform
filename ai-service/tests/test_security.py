import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.core.config import Settings, settings


def test_health_does_not_require_internal_token() -> None:
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200


def test_agent_route_rejects_missing_internal_token() -> None:
    client = TestClient(app)
    response = client.post(
        "/internal/agent/chat",
        json={"conversationId": "c-1", "message": "找教材", "userContext": {"userId": 1, "campus": "东校区", "role": "USER"}, "traceId": "t-1"},
    )
    assert response.status_code == 401


def test_agent_route_rejects_wrong_internal_token() -> None:
    client = TestClient(app)
    response = client.post(
        "/internal/agent/chat",
        headers={"X-Internal-Service-Token": "wrong"},
        json={"conversationId": "c-1", "message": "找教材", "userContext": {"userId": 1, "campus": "东校区", "role": "USER"}, "traceId": "t-1"},
    )
    assert response.status_code == 401


def test_agent_route_returns_stable_503_when_model_is_not_configured(monkeypatch) -> None:
    monkeypatch.setattr('app.agent.runtime.settings.model_name', None)
    client = TestClient(app)
    response = client.post(
        "/internal/agent/chat",
        headers={"X-Internal-Service-Token": settings.internal_token},
        json={
            "conversationId": "c-1",
            "message": "找教材",
            "userContext": {"userId": 1, "campus": "东校区", "role": "USER"},
            "traceId": "t-1",
        },
    )
    assert response.status_code == 503
    assert response.json()["detail"] == "AI 服务暂时不可用"


def test_production_settings_reject_default_internal_token() -> None:
    with pytest.raises(ValueError, match="INTERNAL_TOKEN"):
        Settings(app_env="production", internal_token="dev-ai-internal-token")
