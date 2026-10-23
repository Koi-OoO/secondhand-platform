# 只检查健康接口的响应格式，不测试模型或 Java 服务是否可用。
from fastapi.testclient import TestClient

from app.main import app


def test_health_returns_service_status():
    # 调用应用对象，不打开真实网络端口；然后核对 HTTP 状态和响应 JSON。
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ai-service"}
