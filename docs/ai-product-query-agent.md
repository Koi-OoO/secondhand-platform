# AI 商品查询 Agent

## 请求链路

```text
Vue /ai
  -> Java POST /api/ai/chat
  -> Python POST /internal/agent/chat
  -> Java GET /internal/marketplace/listings/search|{id}
  -> ProductService 的只读查询
```

浏览器只持有 Java 的登录 Session。Java 从 Session 读取 `userId`、`campus` 和 `role`，生成 `traceId`，再携带 `X-Internal-Service-Token` 调用 Python。Python 向 Java 内部接口发送同一个服务令牌和 trace ID，并把用户上下文放在请求头中。

## 当前能力

Agent 只有两个工具：`search_listings` 和 `get_listing`。工具返回的商品卡片包含 `productId`、`title`、`price`、`stock`、`conditionLevel`、`tradeLocation` 和 `detailPath`。价格、库存、成色与地点都来自 Java 查询结果，Agent 不生成商品数据。

“帮我下单”“发布商品”“修改商品”等写操作会被拒绝。Java AI 查询路径强制只查询在售商品，也不会记录普通商品详情接口的浏览量和热搜词副作用。

## 本地配置

Java：

```text
AI_SERVICE_BASE_URL=http://localhost:8000
AI_INTERNAL_TOKEN=dev-ai-internal-token
AI_CONNECT_TIMEOUT_MS=1000
AI_READ_TIMEOUT_MS=30000
```

Python：

```text
INTERNAL_TOKEN=dev-ai-internal-token
JAVA_BACKEND_BASE_URL=http://localhost:8080
MODEL_NAME=gpt-4o-mini
OPENAI_API_KEY=...
```

生产环境必须替换默认内部令牌。模型密钥不写入仓库或请求体。

## 验证

```bash
cd ai-service
python -m pytest -q
python -m compileall -q ai-service

cd ../secondhand-platform-backend
mvn test

cd ../secondhand-platform-web
npm run test:unit
npm run build
```

真实模型调用只在手工验收时启用，单元测试使用假 Agent 和 HTTP mock。
