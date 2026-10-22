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

商品搜索在Java的生产Mapper中共用 `ProductKeywordQuery`：将已识别品牌、常见电子商品类型和型号拆为AND条件，同一品牌或类型的中英文名称作为OR同义词。例如“苹果手机”可以匹配“苹果第十三代专业版手机”，并排除主商品为充电器、保护壳等配件的记录；明确查询“苹果手机充电器”时则搜索充电器。标题中明确附带或赠送的配件不改变主商品类型，电池健康信息也不视为配件。

这套规则以商品主标题为依据，词典覆盖当前常见品牌和电子品类；未识别的关键词沿用普通标题匹配。AI与“查看更多”进入的商品广场执行同一Mapper条件。分页排序在时间、价格等相同时增加商品ID作为次序，消除同值排序的不确定性。

商品卡片包含可选 `coverUrl`。Java内部商品接口已有 `images`，Python从中按sort选择第一张有效封面；Java对外响应保留这一字段。缺图或图片无法加载时，聊天页面显示占位。

## 聊天恢复与继续浏览

聊天页面按已登录用户ID在当前浏览器保存当前会话、消息、商品卡片、筛选条件和草稿。刷新或离开后返回会恢复记录；不同账号读不同缓存。“新对话”重置当前账号的会话。刷新发生在请求中途时会提供重试，恢复后不一直显示加载中。浏览器禁止存储时仍可正常聊天，但无法持久保存。

浏览器最多保存最近100条消息，缓存长度上限约512K字符；浏览器缓存用于恢复页面显示，Agent目前仍执行单轮查询。更换浏览器、设备或清除本站存储后，不会自动同步这些记录。

成功搜索时，Python记录与实际发送到Java一致的工具参数，响应新增可选 `searchFilters`：`keyword`、`categoryId`、`minPrice`、`maxPrice`、`conditionLevel`、`sort`。关键词在查询前去除首尾空格，最长200字符，空关键词视为未指定。工具分页不加入继续浏览条件。

每条含搜索条件的回答下提供“查看更多符合条件的商品”，跳转现有商品广场并携带条件。例如100元以内的近全新台灯，会带上 `keyword=台灯&maxPrice=100&conditionLevel=2`。商品列表从URL恢复预算和成色，翻页和排序保留已生效的筛选，允许预算为0。无结果搜索也保留条件；调用失败或只有详情查询时不生成无依据的搜索链接。

上线本次修改时需重新启动Java后端和Python AI Service，让新字段和工具逻辑生效；前端开发模式会热更新，刷新页面即可加载最新界面。修改前的对话没有保存到浏览器，因此无法从缓存恢复。

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

Python 配置写入 `ai-service/.env`。使用 DeepSeek 官方平台时，模型配置如下：

```text
MODEL_NAME=deepseek-flash
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_API_KEY=填写你的 DeepSeek API Key
```

`OPENAI_API_KEY` 是当前 OpenAI 兼容客户端使用的配置名称，也可以保存 DeepSeek 的密钥。`OPENAI_BASE_URL` 决定请求发送到哪个模型平台；不填写时使用 OpenAI 默认地址。当前 Agent 对 DeepSeek 关闭思考模式，避免 `ChatOpenAI` 未保留 `reasoning_content` 时，工具结果回传失败。

PyCharm 的运行配置使用模块 `uvicorn`，参数为 `app.main:app --reload --port 8000`，工作目录为项目下的 `ai-service`。修改 `.env` 后，停止并重新启动 AI Service，让配置重新加载。初次联调可保持 `LANGCHAIN_TRACING_V2=false`，`LANGCHAIN_API_KEY` 留空。

生产环境必须替换默认内部令牌。模型密钥不写入仓库或请求体。

## 验证

```bash
cd ai-service
python -m pytest -q
python -m compileall -q app

cd ../secondhand-platform-backend
mvn test

cd ../secondhand-platform-web
npm run test:unit
npm run test:proxy
npm run build
```

真实模型调用只在手工验收时启用，单元测试使用假 Agent 和 HTTP mock。
