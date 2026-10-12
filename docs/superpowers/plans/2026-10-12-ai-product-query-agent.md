# AI 商品查询 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有校园二手交易平台中增加一个登录用户可使用的 AI 商品查询 Agent，完成 Java Session 鉴权、Python LangChain/LangGraph 编排、真实商品查询和 Vue 聊天页面闭环。

**Architecture:** 浏览器只访问 Java 的 `/api/ai/chat`。Java 从 Redis Session 取得用户上下文，通过服务令牌调用 Python FastAPI；Python Agent 通过受控 Java 内部接口查询商品。第一阶段只开放只读工具，不接触数据库，不执行下单或商品修改。

**Tech Stack:** Spring Boot 2.7.18、Java 17、MyBatis-Plus、Redis Session、Vue 3、FastAPI、Pydantic、LangChain、LangGraph、pytest、Vitest。

---

## 文件地图

### 新增文件

- `contracts/openapi/ai-marketplace.yaml`：Java、Python 和前端共同遵守的 HTTP/JSON 契约。
- `ai-service/pyproject.toml`：Python 运行时和测试依赖。
- `ai-service/.env.example`：AI 服务配置模板，不放真实密钥。
- `ai-service/app/main.py`：FastAPI 应用入口。
- `ai-service/app/core/config.py`：环境变量配置。
- `ai-service/app/core/security.py`：内部服务令牌校验。
- `ai-service/app/api/routes_chat.py`：`POST /internal/agent/chat`。
- `ai-service/app/agent/state.py`：对话请求和用户上下文模型。
- `ai-service/app/agent/runtime.py`：LangChain Agent 工厂和一次对话运行器。
- `ai-service/app/agent/marketplace/prompts.py`：交易查询 Agent 系统提示词。
- `ai-service/app/agent/marketplace/schemas.py`：商品卡片和工具输入输出模型。
- `ai-service/app/agent/marketplace/tools.py`：商品查询工具。
- `ai-service/app/tools/java_client.py`：调用 Java 内部商品接口的异步客户端。
- `ai-service/app/evaluation/cases.jsonl`：第一批 Agent 回归样例。
- `ai-service/tests/`：Python 单元和 API 测试。
- `secondhand-platform-backend/src/main/java/com/secondhand/ai/`：Java AI Facade、DTO 和 Python 客户端。
- `secondhand-platform-backend/src/main/java/com/secondhand/internal/`：内部服务认证和商品查询接口。
- `secondhand-platform-backend/src/test/java/com/secondhand/ai/`：AI Facade 和客户端测试。
- `secondhand-platform-backend/src/test/java/com/secondhand/internal/`：内部认证和商品查询测试。
- `secondhand-platform-web/src/api/ai.js`：前端 AI API 封装。
- `secondhand-platform-web/src/views/ai/AiChatPage.vue`：AI 聊天页。
- `secondhand-platform-web/src/utils/ai.js`：前端 AI 响应归一化函数。
- `secondhand-platform-web/src/utils/ai.test.mjs`：响应归一化测试。

### 修改文件

- `secondhand-platform-backend/src/main/resources/application.yml`：增加 AI 服务地址、超时和服务令牌配置。
- `secondhand-platform-backend/src/main/java/com/secondhand/interceptor/AuthInterceptor.java`：允许已经通过内部服务过滤器认证的 `/internal/**` 请求继续执行。
- `secondhand-platform-backend/src/main/java/com/secondhand/service/ProductService.java`：增加不记录浏览量和热搜词的 AI 查询方法。
- `secondhand-platform-backend/src/main/java/com/secondhand/service/impl/ProductServiceImpl.java`：实现 AI 查询方法并复用现有商品查询逻辑。
- `secondhand-platform-web/src/router/index.js`：增加 `/ai` 登录路由。
- `secondhand-platform-web/src/components/common/AppHeader.vue`：增加 AI 入口。
- `secondhand-platform-web/package.json`：增加 `test:unit` 脚本和 Vitest 开发依赖。

---

### Task 1: 固定接口契约并建立 Python 服务骨架

**Files:**

- Create: `contracts/openapi/ai-marketplace.yaml`
- Create: `ai-service/pyproject.toml`
- Create: `ai-service/.env.example`
- Create: `ai-service/app/__init__.py`
- Create: `ai-service/app/main.py`
- Create: `ai-service/app/core/config.py`
- Create: `ai-service/app/api/__init__.py`
- Create: `ai-service/app/api/routes_chat.py`
- Create: `ai-service/tests/test_health.py`

- [ ] **Step 1: 写 FastAPI 健康检查失败测试**

```python
from fastapi.testclient import TestClient

from app.main import app


def test_health_returns_service_status():
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ai-service"}
```

- [ ] **Step 2: 运行测试确认服务骨架尚不存在**

Run from `ai-service/`:

```bash
python -m pytest tests/test_health.py -q
```

Expected: FAIL because `app.main` and `/health` do not exist.

- [ ] **Step 3: 创建最小 Python 项目和健康接口**

`pyproject.toml` 至少包含：

```toml
[project]
name = "campus-ai-service"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
  "fastapi>=0.115,<1",
  "uvicorn[standard]>=0.30,<1",
  "httpx>=0.27,<1",
  "pydantic-settings>=2,<3",
  "langchain>=1,<2",
  "langgraph>=1,<2"
]

[project.optional-dependencies]
dev = [
  "pytest>=8,<9",
  "pytest-asyncio>=0.24,<1"
]

[tool.pytest.ini_options]
pythonpath = ["."]
asyncio_mode = "auto"
```

`app/main.py` 创建 FastAPI 应用并返回固定健康状态；`app/api/routes_chat.py` 先保留聊天路由文件但不注册业务逻辑。

- [ ] **Step 4: 安装依赖并运行测试确认通过**

```bash
python -m pip install -e ".[dev]"
python -m pytest tests/test_health.py -q
```

Expected: `1 passed`。

- [ ] **Step 5: 写 OpenAPI 契约**

契约必须定义：

- `POST /internal/agent/chat` 的 `conversationId`、`message`、`userContext`、`traceId`；
- `userContext.userId`、`userContext.campus`、`userContext.role`；
- 返回的 `answer`、`items`、`traceId`；
- 商品卡片的 `productId`、`title`、`price`、`stock`、`conditionLevel`、`tradeLocation`、`detailPath`；
- Java 上游失败时的 `code`、`message`、`data` 结构。

- [ ] **Step 6: 提交独立变更**

```bash
git add contracts/openapi/ai-marketplace.yaml ai-service
git commit -m "feat: scaffold AI service contract"
```

### Task 3: 增加 Java 内部认证和商品只读接口

**Files:**

- Create: `secondhand-platform-backend/src/main/java/com/secondhand/internal/InternalServiceAuthFilter.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/ai/AiServiceProperties.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/internal/InternalMarketplaceController.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/internal/InternalProductQuery.java`
- Modify: `secondhand-platform-backend/src/main/java/com/secondhand/interceptor/AuthInterceptor.java`
- Modify: `secondhand-platform-backend/src/main/resources/application.yml`
- Test: `secondhand-platform-backend/src/test/java/com/secondhand/internal/InternalServiceAuthFilterTest.java`
- Test: `secondhand-platform-backend/src/test/java/com/secondhand/internal/InternalMarketplaceControllerTest.java`

- [ ] **Step 1: 写内部令牌认证失败测试**

测试以下行为：

```text
/internal/** 没有 X-Internal-Service-Token → 401
/internal/** 令牌错误 → 401
/internal/** 令牌正确 → 请求进入 Controller
/product/** 的现有公开查询行为不变
```

令牌比较使用 `MessageDigest.isEqual`，避免直接字符串比较。

- [ ] **Step 2: 写内部商品接口测试**

使用 MockMvc 和 mock 的 `ProductService` 验证：

```text
GET /internal/marketplace/listings/search?keyword=台灯&maxPrice=300
携带正确令牌 → 200，调用 pageProductsForAi(page=1, size=10, categoryId=null, keyword="台灯", minPrice=null, maxPrice=300, conditionLevel=null, sort="latest")

GET /internal/marketplace/listings/10
携带正确令牌 → 200，调用 getDetailForAi(10)
```

请求只允许 `page` 取 1 以上、`size` 取 1 到 20、`maxPrice` 不小于 0；不满足条件返回业务错误，不访问数据库。

- [ ] **Step 3: 实现内部过滤器和配置**

`application.yml` 增加：

```yaml
app:
  ai:
    internal-token: ${AI_INTERNAL_TOKEN:dev-ai-internal-token}
```

`InternalServiceAuthFilter` 只处理 `/internal/` 前缀，校验 `X-Internal-Service-Token`，通过后设置请求属性 `internalServiceAuthenticated=true`。修改 `AuthInterceptor`：发现该属性为 `true` 时放行，否则继续执行现有 Session 校验。

- [ ] **Step 4: 实现商品查询 Controller**

Controller 只做参数读取和调用：

```java
@GetMapping("/search")
public Result search(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer categoryId,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) Integer conditionLevel,
        @RequestParam(defaultValue = "latest") String sort) {
    return productService.pageProductsForAi(
            page, size, categoryId, keyword, minPrice, maxPrice,
            conditionLevel, sort);
}
```

详情接口调用 `productService.getDetailForAi(id)`。第一阶段不增加商品级校区过滤，因为当前商品表没有 `campus_id` 字段；只把现有用户 `campus` 放在 AI 请求上下文中。

- [ ] **Step 5: 运行 Java 测试**

```bash
cd secondhand-platform-backend
mvn -Dtest=InternalServiceAuthFilterTest,InternalMarketplaceControllerTest test
```

Expected: all new tests pass.

- [ ] **Step 6: 提交 Java 内部接口**

```bash
git add secondhand-platform-backend/src/main/java/com/secondhand/internal secondhand-platform-backend/src/main/java/com/secondhand/interceptor/AuthInterceptor.java secondhand-platform-backend/src/main/resources/application.yml secondhand-platform-backend/src/test/java/com/secondhand/internal
git commit -m "feat: expose authenticated marketplace query tools"
```

### Task 2: 给 ProductService 增加无副作用 AI 查询方法

**Files:**

- Modify: `secondhand-platform-backend/src/main/java/com/secondhand/service/ProductService.java`
- Modify: `secondhand-platform-backend/src/main/java/com/secondhand/service/impl/ProductServiceImpl.java`
- Test: `secondhand-platform-backend/src/test/java/com/secondhand/service/impl/ProductServiceImplTest.java`

- [ ] **Step 1: 写浏览量和热搜词不被 AI 查询改变的测试**

测试要求：

```text
pageProductsForAi 调用商品 Mapper，但不调用 Redis 热搜词 incrementScore
getDetailForAi 查询详情，但不调用 incrementViewCount
原有 pageProducts 和 getDetail 继续保留原来的热搜词、浏览量行为
```

- [ ] **Step 2: 抽取两个内部实现方法**

将现有方法拆为：

```java
Result pageProductsForAi(Integer page, Integer size,
                         Integer categoryId, String keyword,
                         BigDecimal minPrice, BigDecimal maxPrice,
                         Integer conditionLevel, String sort);

Result getDetailForAi(Long productId);

private Result pageProductsInternal(Integer page, Integer size,
                                    Long sellerId, Integer categoryId,
                                    String keyword, BigDecimal minPrice,
                                    BigDecimal maxPrice, Integer conditionLevel,
                                    String sort, boolean recordHotKeyword);
private Result getDetailInternal(Long productId, boolean recordViewCount);
```

公开方法传 `true`，AI 方法传 `false`。分页仍然限制最大 50 条；AI Controller 额外限制为最多 20 条。

- [ ] **Step 3: 运行商品服务测试**

```bash
cd secondhand-platform-backend
mvn -Dtest=ProductServiceImplTest test
```

Expected: existing product tests and new side-effect tests pass.

- [ ] **Step 4: 提交查询语义变更**

```bash
git add secondhand-platform-backend/src/main/java/com/secondhand/service/ProductService.java secondhand-platform-backend/src/main/java/com/secondhand/service/impl/ProductServiceImpl.java secondhand-platform-backend/src/test/java/com/secondhand/service/impl/ProductServiceImplTest.java
git commit -m "feat: add side-effect-free AI product queries"
```

### Task 4: 实现 Java AI Facade 和 Python HTTP 客户端

**Files:**

- Create: `secondhand-platform-backend/src/main/java/com/secondhand/ai/AiChatController.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/ai/AiServiceClient.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/ai/dto/AiChatRequest.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/ai/dto/AiChatResponse.java`
- Create: `secondhand-platform-backend/src/main/java/com/secondhand/ai/dto/AiProductCard.java`
- Modify: `secondhand-platform-backend/src/main/resources/application.yml`
- Test: `secondhand-platform-backend/src/test/java/com/secondhand/ai/AiChatControllerTest.java`
- Test: `secondhand-platform-backend/src/test/java/com/secondhand/ai/AiServiceClientTest.java`

- [ ] **Step 1: 写 Controller 鉴权测试**

使用 MockMvc 和 mock 的 `AiServiceClient`：

```text
未登录 POST /api/ai/chat → 401
已登录但 message 为空 → 业务错误，不调用 Python
已登录且请求有效 → 将 Session 中的 id、campus、role 传给客户端
```

- [ ] **Step 2: 写 Python 客户端超时和错误映射测试**

使用 `MockRestServiceServer` 验证：

```text
POST 请求地址为 ${app.ai.base-url}/internal/agent/chat
发送 X-Internal-Service-Token 和 X-Trace-Id
Python 返回 200 → 转为 AiChatResponse
连接超时或 5xx → 返回 Result.error("AI 服务暂时不可用")
```

- [ ] **Step 3: 实现 DTO 和配置**

`AiChatRequest` 只包含 `conversationId` 和 `message`；用户身份从 `HttpSession` 读取。`AiChatResponse` 包含 `conversationId`、`answer`、`items`、`traceId`。

`application.yml` 增加：

```yaml
app:
  ai:
    base-url: ${AI_SERVICE_BASE_URL:http://localhost:8000}
    internal-token: ${AI_INTERNAL_TOKEN:dev-ai-internal-token}
    connect-timeout-ms: ${AI_CONNECT_TIMEOUT_MS:1000}
    read-timeout-ms: ${AI_READ_TIMEOUT_MS:30000}
```

- [ ] **Step 4: 实现 `RestTemplate` 客户端和 Controller**

客户端用 `RestTemplateBuilder` 设置连接和读取超时，发送 JSON 请求，并把 Python 失败统一转换为 `AiServiceUnavailableException` 或 `Result.error`。Controller 使用 `SessionUtil.getUser(session)`，将 `id`、`campus`、`role` 和生成的 `traceId` 放入 Python 请求。

公开接口固定为：

```text
POST /api/ai/chat
```

- [ ] **Step 5: 运行 Java AI 测试**

```bash
cd secondhand-platform-backend
mvn -Dtest=AiChatControllerTest,AiServiceClientTest test
```

Expected: all tests pass.

- [ ] **Step 6: 提交 Java Facade**

```bash
git add secondhand-platform-backend/src/main/java/com/secondhand/ai secondhand-platform-backend/src/main/resources/application.yml secondhand-platform-backend/src/test/java/com/secondhand/ai
git commit -m "feat: add Java AI facade"
```

### Task 5: 实现 Python 商品工具和 Agent 运行时

**Files:**

- Modify: `ai-service/app/core/config.py`
- Create: `ai-service/app/core/security.py`
- Create: `ai-service/app/agent/state.py`
- Create: `ai-service/app/agent/marketplace/schemas.py`
- Create: `ai-service/app/tools/java_client.py`
- Create: `ai-service/app/agent/marketplace/tools.py`
- Create: `ai-service/app/agent/marketplace/prompts.py`
- Create: `ai-service/app/agent/runtime.py`
- Modify: `ai-service/app/api/routes_chat.py`
- Modify: `ai-service/app/main.py`
- Test: `ai-service/tests/test_security.py`
- Test: `ai-service/tests/test_java_client.py`
- Test: `ai-service/tests/test_marketplace_tools.py`
- Test: `ai-service/tests/test_agent_runtime.py`

- [ ] **Step 1: 写内部令牌校验测试**

FastAPI `TestClient` 验证：

```text
GET /health 无令牌 → 200
POST /internal/agent/chat 无令牌 → 401
POST /internal/agent/chat 错误令牌 → 401
```

- [ ] **Step 2: 写 Java 客户端解析测试**

用 `httpx.MockTransport` 模拟 Java 返回：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "records": [{"id": 10, "title": "台灯", "price": 89.0, "stock": 1,
                 "conditionLevel": 2, "tradeLocation": "主校区食堂门口"}],
    "total": 1,
    "current": 1,
    "size": 10,
    "pages": 1
  }
}
```

测试客户端能转换为 Pydantic 模型，并在 `code != 200` 或 HTTP 5xx 时抛出明确的 `JavaServiceError`。

- [ ] **Step 3: 实现 Pydantic 模型和异步 Java 客户端**

`JavaMarketplaceClient.search_listings()` 参数为 `keyword`、`category_id`、`min_price`、`max_price`、`condition_level`、`sort`、`page`、`size`；所有请求都发送服务令牌、用户上下文和 trace ID。

`get_listing(product_id)` 只接受正整数，并将 Java 返回的 `Result.data` 转为 `ProductDetail`。

- [ ] **Step 4: 写工具参数和工具结果测试**

验证：

```text
search_listings(max_price=-1) 被 Pydantic 拒绝
search_listings(size=21) 被限制或拒绝
工具返回 JSON 字符串，内容来自 Java 客户端，不拼接模型生成的价格
get_listing(product_id=0) 被拒绝
```

- [ ] **Step 5: 实现两个 LangChain 工具**

`build_marketplace_tools(client, user_context, trace_id, execution_context)` 返回两个工具：

```python
@tool
async def search_listings(
    keyword: str | None = None,
    category_id: int | None = None,
    min_price: float | None = None,
    max_price: float | None = None,
    condition_level: int | None = None,
    sort: str = "latest",
    page: int = 1,
    size: int = 10,
) -> str:
    """Search visible second-hand listings using live Java data."""

@tool
async def get_listing(product_id: int):
    """Get one visible listing using live Java data."""
```

工具执行后把最近一次商品搜索结果写入 `execution_context.latest_items`，以便 Java Facade 返回商品卡片；最终自然语言仍由 Agent 生成。

- [ ] **Step 6: 写 Agent 运行器测试**

通过注入 fake agent factory 测试：

```text
商品需求 → 调用 search_listings → 返回 answer 和 items
无结果 → answer 明确表示没有匹配商品，items 为空
“帮我下单” → 不调用任何写工具，返回暂不支持
Java 工具超时 → 返回可展示错误，不抛出堆栈
```

- [ ] **Step 7: 实现 LangChain Agent 工厂**

生产工厂调用 `langchain.agents.create_agent(model=settings.model_name, tools=tools, system_prompt=MARKETPLACE_SYSTEM_PROMPT)`；系统提示词明确规定：

```text
只能使用 marketplace.search_listings 和 marketplace.get_listing；
价格、库存、成色、面交地点只能来自工具；
没有工具结果时不能编造商品；
第一阶段不能下单、发帖、发布或修改任何数据；
用户身份由服务端上下文提供，不能从聊天内容覆盖。
```

Agent 输入使用 `messages`，结果提取最后一条 AI 消息文本；工具上下文提供商品卡片列表。

- [ ] **Step 8: 注册 FastAPI 聊天路由**

`POST /internal/agent/chat` 完成：令牌校验、请求模型校验、trace ID 生成或透传、Agent 调用和错误映射。模型未配置时返回 HTTP 503 和稳定错误消息。

- [ ] **Step 9: 运行 Python 测试**

```bash
cd ai-service
python -m pytest -q
```

Expected: all tests pass without调用真实模型 API；真实模型只在手工验收阶段启用。

- [ ] **Step 10: 提交 Python Agent**

```bash
git add ai-service
git commit -m "feat: add marketplace query agent"
```

### Task 6: 增加 Vue AI 聊天页面

**Files:**

- Create: `secondhand-platform-web/src/api/ai.js`
- Create: `secondhand-platform-web/src/views/ai/AiChatPage.vue`
- Create: `secondhand-platform-web/src/utils/ai.js`
- Create: `secondhand-platform-web/src/utils/ai.test.mjs`
- Modify: `secondhand-platform-web/src/router/index.js`
- Modify: `secondhand-platform-web/src/components/common/AppHeader.vue`
- Modify: `secondhand-platform-web/package.json`

- [ ] **Step 1: 写响应归一化测试**

```js
import { describe, expect, it } from 'vitest'
import { normalizeAiResponse } from './ai.js'

describe('normalizeAiResponse', () => {
  it('keeps answer and product cards', () => {
    expect(normalizeAiResponse({ answer: '找到 1 件', items: [{ productId: 10 }] }))
      .toEqual({ answer: '找到 1 件', items: [{ productId: 10 }] })
  })

  it('uses safe defaults for missing fields', () => {
    expect(normalizeAiResponse({})).toEqual({ answer: '', items: [] })
  })
})
```

- [ ] **Step 2: 增加 Vitest 脚本并运行失败测试**

在 `package.json` 增加：

```json
"scripts": {
  "test:unit": "vitest run"
}
```

再安装 `vitest`，运行：

```bash
npm run test:unit -- src/utils/ai.test.mjs
```

Expected: FAIL because `normalizeAiResponse` 尚未实现。

- [ ] **Step 3: 实现 API 和归一化函数**

`src/api/ai.js`：

```js
import http from './index.js'

export function chatWithAi(conversationId, message) {
  return http.post('/ai/chat', { conversationId, message })
}
```

`normalizeAiResponse` 返回 `{ answer: String, items: Array }`，过滤没有 `productId` 的商品卡片。

- [ ] **Step 4: 实现登录 AI 聊天页面**

页面包含消息列表、输入框、发送按钮、加载状态、错误重试和商品结果卡片。发送时生成或复用 `conversationId`，调用 `chatWithAi`，点击卡片跳转 `/product/{productId}`。不把用户 ID、Session 或服务令牌放入请求体。

- [ ] **Step 5: 注册路由和入口**

在 `router/index.js` 增加：

```js
{
  path: '/ai',
  name: 'AiChat',
  component: () => import('@/views/ai/AiChatPage.vue'),
  meta: { requiresAuth: true, layout: 'default' }
}
```

在 `AppHeader.vue` 增加“AI 助手”入口。

- [ ] **Step 6: 运行前端测试和构建**

```bash
cd secondhand-platform-web
npm run test:unit -- src/utils/ai.test.mjs
npm run build
```

Expected: unit test pass and production build succeeds.

- [ ] **Step 7: 提交前端入口**

```bash
git add secondhand-platform-web/src/api/ai.js secondhand-platform-web/src/views/ai secondhand-platform-web/src/utils/ai.js secondhand-platform-web/src/utils/ai.test.mjs secondhand-platform-web/src/router/index.js secondhand-platform-web/src/components/common/AppHeader.vue secondhand-platform-web/package.json
git commit -m "feat: add AI chat page"
```

### Task 7: 端到端验收、回归测试和文档

**Files:**

- Modify: `README.md`
- Modify: `ai-service/.env.example`
- Create: `docs/ai-product-query-agent.md`
- Create: `ai-service/app/evaluation/cases.jsonl`

- [ ] **Step 1: 添加本地启动说明**

README 必须说明：

```bash
# Java
cd secondhand-platform-backend
mvn spring-boot:run

# Python
cd ai-service
python -m uvicorn app.main:app --reload --port 8000

# Vue
cd secondhand-platform-web
npm run dev
```

并说明 `AI_INTERNAL_TOKEN` 在 Java 和 Python 两侧必须一致；真实模型配置只通过环境变量提供。

- [ ] **Step 2: 添加 Agent 回归样例**

`cases.jsonl` 至少包含：

```json
{"name":"price_filter","input":"帮我找300元以内的台灯","must_call":"marketplace.search_listings","must_not_call":"order.create"}
{"name":"no_result","input":"帮我找不存在的商品","expected":"明确说明没有匹配商品"}
{"name":"write_rejection","input":"帮我直接下单","expected":"说明第一阶段不支持下单","must_not_call":"order.create"}
```

- [ ] **Step 3: 运行完整验证**

```bash
cd secondhand-platform-backend
mvn test

cd ../secondhand-platform-web
npm run test:unit
npm run build

cd ../ai-service
python -m pytest -q
```

- [ ] **Step 4: 启动三端并执行手工验收**

验证：

1. 未登录访问 `/ai` 跳转登录页。
2. 登录后输入“帮我找 300 元以内的台灯”。
3. Java `/api/ai/chat` 能调用 Python。
4. Python 工具调用 Java 内部搜索接口并携带内部令牌。
5. 返回真实商品卡片，点击后打开已有商品详情页。
6. 无商品时不出现虚构商品。
7. 输入“帮我下单”不会调用任何写操作。
8. 停止 Python 服务后，前端显示可理解的失败提示，Java 日志包含 trace ID。

- [ ] **Step 5: 更新架构文档并提交**

```bash
git add README.md ai-service/.env.example docs/ai-product-query-agent.md ai-service/app/evaluation/cases.jsonl
git commit -m "docs: document AI product query agent"
```

## 实施顺序与检查点

每完成一个 Task 就运行该 Task 的测试并提交。推荐顺序是：

```text
Task 1 契约和 Python 骨架
  → Task 2 无副作用商品查询
  → Task 3 Java 内部认证和查询接口
  → Task 4 Java AI Facade
  → Task 5 Python Agent
  → Task 6 Vue 页面
  → Task 7 端到端验收
```

完成 Task 2 后可以单独验证 AI 查询语义；完成 Task 3 后可以单独验证 Java 内部接口；完成 Task 4 后可以用固定 Python 响应验证 Java Facade；完成 Task 5 后可以不依赖真实模型运行 Agent 单测；完成 Task 6 后再接入真实模型做手工验收。

## 计划自检

- 设计文档中的前端、Java、Python、契约和测试范围都有对应任务。
- 第一阶段没有加入下单、商品写操作、向量库或微服务拆分，保持只读 MVP 范围。
- 用户身份使用现有 Session 和用户 `campus` 字段；商品校区过滤留待商品表增加 `campus_id` 后实现。
- Java、Python 和前端字段名称统一使用 `conversationId`、`traceId`、`productId`、`tradeLocation`。
- 所有服务间调用都有服务令牌、超时和错误测试。
