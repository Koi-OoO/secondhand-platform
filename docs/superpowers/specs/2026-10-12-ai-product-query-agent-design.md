# AI 商品查询 Agent 设计

日期：2026-10-12

## 1. 目标与范围

第一阶段在现有校园二手交易平台上增加一个可复用的 AI 商品查询能力，完成从用户提问、需求理解、商品查询到自然语言回答的只读闭环。

目标用户流程：

1. 用户登录后打开 AI 聊天入口。
2. 用户用自然语言描述商品需求，例如“帮我找 300 元以内的台灯”。
3. Java 后端校验登录 Session，并将经过验证的用户上下文传给 Python AI 服务。
4. Python Agent 解析需求，调用 Java 提供的受控商品查询接口。
5. Java 从现有 MySQL 商品数据中查询结果。
6. Python 根据真实结果生成回答，返回商品名称、价格、成色、面交地点和详情链接。

本阶段只实现只读能力，不实现下单、商品发布、商品删除、库存修改、直接数据库访问或自动执行副作用操作。

## 2. 方案选择

采用“前端 → Java → Python → Java 领域接口”的方案。

- 前端只访问 Java，沿用现有 Session、CORS 和统一响应处理。
- Java 负责用户身份、校园上下文、权限、请求转发和商品数据真实来源。
- Python 使用 FastAPI 提供内部 Agent 接口，使用 LangChain 组织模型和工具调用；Agent 状态和后续需要暂停恢复的流程预留 LangGraph 接入点。
- Python 不直接访问 MySQL、Redis Session 或 Java 持久化对象。
- Java 与 Python 之间使用独立 DTO 和 JSON 契约，避免共享数据库模型。

未采用前端直连 Python，因为这会让登录态、跨域和权限边界分散；未采用全部 Java，因为第一阶段需要展示 Python Agent 工程能力。

## 3. 服务边界与目录安排

现有两个项目保留，新增 AI 服务和契约目录：

```text
secondhand-platform/
├── secondhand-platform-backend/
├── secondhand-platform-web/
├── ai-service/
├── contracts/
└── docs/
```

Java 第一阶段只新增 AI 适配边界，不对现有全局 `controller/service/mapper/entity` 结构做大规模迁移：

```text
com.secondhand/
├── ai/
│   ├── controller/       # 对前端的 /api/ai/chat
│   ├── client/           # Python Agent HTTP 客户端
│   ├── dto/              # AI 请求、响应、用户上下文
│   └── config/           # Python 服务地址和超时配置
└── controller/service/mapper/entity/ # 现有业务，按需复用
```

后续新增校园墙、论坛和活动时，再逐步按领域模块迁移，不把本阶段的 AI 需求与大规模重构绑定。

Python 服务安排如下：

```text
ai-service/
├── pyproject.toml
├── app/
│   ├── main.py
│   ├── api/
│   │   ├── routes_chat.py
│   │   └── dependencies.py
│   ├── core/
│   │   ├── config.py
│   │   ├── security.py
│   │   └── logging.py
│   ├── agent/
│   │   ├── runtime.py
│   │   ├── state.py
│   │   └── marketplace/
│   │       ├── prompts.py
│   │       ├── schemas.py
│   │       └── tools.py
│   ├── tools/
│   │   └── java_client.py
│   └── evaluation/
└── tests/
```

## 4. API 与数据契约

### 4.1 前端到 Java

```http
POST /api/ai/chat
Content-Type: application/json
Cookie: SESSION=...
```

请求：

```json
{
  "conversationId": "conversation-001",
  "message": "帮我找 300 元以内的台灯"
}
```

Java 从 Session 获取用户身份，禁止从请求体读取用户 ID 作为可信身份。

### 4.2 Java 到 Python

```http
POST /internal/agent/chat
X-Internal-Service-Token: <server-side credential>
X-Trace-Id: trace-001
```

请求：

```json
{
  "conversationId": "conversation-001",
  "message": "帮我找 300 元以内的台灯",
  "userContext": {
    "userId": 1,
    "campus": "东校区信息工程学院",
    "role": "USER"
  },
  "traceId": "trace-001"
}
```

Python 返回统一结构：

```json
{
  "conversationId": "conversation-001",
  "answer": "我找到了 2 件符合条件的台灯。",
  "items": [
    {
      "productId": 10,
      "title": "宿舍护眼台灯",
      "price": 89.0,
      "condition": "九成新",
      "tradeLocation": "主校区食堂门口",
      "detailPath": "/product/10"
    }
  ],
  "traceId": "trace-001"
}
```

### 4.3 Python 到 Java 商品工具接口

```http
GET /internal/marketplace/listings/search
GET /internal/marketplace/listings/{productId}
```

搜索参数允许关键词、分类、最高价格、最低成色、分页和排序。Java 对参数做范围校验，并只返回当前用户可见的商品。当前商品表没有商品级 `campus_id`，因此第一阶段不实现按校区筛选；用户现有的 `campus` 字段只作为上下文传递，后续增加商品校区字段后再加入过滤条件。

## 5. Agent 行为

第一阶段 Agent 只拥有两个工具：

```text
marketplace.search_listings
marketplace.get_listing
```

Agent 处理顺序：

1. 判断是否为商品查询意图。
2. 提取关键词、价格、分类、成色和排序条件；用户提到校区时保留为未执行的上下文，避免声称当前支持商品级校区筛选。
3. 信息不足时提出一个简短澄清问题。
4. 调用 `search_listings` 查询真实商品。
5. 必要时调用 `get_listing` 获取详情。
6. 根据工具返回结果生成回答，并附带商品详情路径。

约束：

- 商品价格、库存、面交地点和状态只能来自 Java 工具结果。
- 没有查询结果时明确说明，不生成虚构商品。
- 用户要求下单、发布或修改商品时，第一阶段返回“暂不支持该操作”，不调用写操作。
- Agent 输入和工具结果都设置长度限制、超时和错误兜底。

## 6. 身份、安全与错误处理

- 浏览器只与 Java 通信，Python 服务不暴露给浏览器。
- Java 到 Python 使用服务端凭证和请求超时；Python 到 Java 使用内部服务凭证。
- `userId`、用户现有的 `campus` 字段和角色由 Java 从当前 Session 得出，Python 不接受聊天文本中的身份声明。
- Java 商品查询接口再次执行资源可见性检查。
- 日志记录 `traceId`、耗时、工具名和错误类型，不记录密码、Session 值和完整敏感聊天内容。
- Python 超时、模型错误或 Java 查询失败时，Java 返回统一的可展示错误；不把内部堆栈返回给用户。
- 对相同 `conversationId` 的请求预留并发控制，避免前端重复提交造成上下文错乱。

## 7. 前端交互

新增一个 AI 聊天入口和最小聊天页面：

- 消息列表；
- 输入框和发送按钮；
- 加载状态；
- 商品结果卡片；
- 点击卡片跳转到已有商品详情页；
- 未登录时跳转登录页；
- 错误提示和重试按钮。

第一阶段使用普通 JSON 请求即可，等回答内容和工具事件稳定后，再考虑 SSE 流式输出。

## 8. 测试与验收

Java：

- AI Controller 登录态测试；
- Python 客户端超时和错误映射测试；
- 商品搜索内部接口参数和可见性测试；
- OpenAPI 契约字段测试。

Python：

- 工具参数转换测试；
- Java 客户端响应解析测试；
- 无结果、超时、非法参数和 Java 5xx 场景测试；
- 至少覆盖商品搜索、商品详情、非商品问题和不支持写操作的 Agent 回归样例。

前端：

- AI API 调用和错误展示测试；
- 商品结果卡片跳转测试；
- 登录过期后的处理测试。

验收场景：

1. “帮我找 300 元以内的台灯”返回真实商品。
2. “有没有九成新的自行车”包含成色条件，并在结果中返回真实面交地点。
3. 没有结果时不编造商品。
4. 未登录请求被拦截。
5. Agent 无法直接创建订单或修改商品。

## 9. 后续复用边界

未来增加校园墙、论坛和活动时：

- Java 新增对应领域模块和受控查询接口；
- Python 新增对应工具包，不修改 Agent Runtime；
- 共用用户上下文、会话、权限、日志、评测和前端聊天组件；
- RAG 保存校园规则、FAQ 和办事指南，实时业务数据继续通过领域工具查询。

下单、发帖、报名等写操作将在后续阶段单独设计，统一采用“Agent 生成草稿 → 用户确认 → Java 校验并执行”的流程。
