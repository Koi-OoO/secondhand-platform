# 校园闲物 — 校园二手交易平台 (Campus Secondhand Platform)

> 面向校园场景的 C2C 二手交易平台，聚焦学生毕业季/换寝时的闲置流转。后端基于 Spring Boot，前端基于 Vue 3，覆盖发布、浏览、下单、面交、评价的完整闭环。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.4-42b883)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5-blue)
![Redis](https://img.shields.io/badge/Redis-Session%20%26%20Lock-red)
![AI](https://img.shields.io/badge/AI-FastAPI%20%2B%20LangChain-orange)

## 项目简介

本项目面向校园二手交易场景，实现了从学生注册登录、拍照发布闲置、浏览搜索、下单约定面交到交易互评的完整业务闭环。后端采用经典的 Controller-Service-Mapper 分层架构，引入 Redis 分布式 Session 与 Redisson 分布式锁解决会话共享和并发超卖问题；前端为 Vue 3 单页应用，基于 Element Plus 构建。

项目还包含一个只读的 AI 商品查询模块：浏览器访问 Java 的 `/api/ai/chat`，Java 负责 Session 用户上下文和服务间令牌，Python FastAPI/LangChain Agent 通过受控 Java 内部接口查询在售商品。AI 阶段不执行下单、发布、修改或删除操作，后续校园墙、论坛和活动模块可以复用这条 Java Facade 与 Python Agent 边界。

## 技术栈

### 后端(secondhand-platform-backend)

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 2.7.18 | Web 基础框架 |
| Java | 17 | 语言版本 |
| MySQL | 8.0 | 关系型数据库 |
| MyBatis-Plus | 3.5.3 | ORM,简化 CRUD |
| Redis | - | 缓存 + 分布式 Session |
| Spring Session | 2.7.4 | Session 外置到 Redis |
| Redisson | 3.17.7 | 分布式锁(防超卖) |
| 阿里云 OSS | 3.17.4 | 对象存储(图片) |
| Knife4j / SpringDoc | 4.5.0 / 1.7.0 | OpenAPI 3 接口文档 |
| Hutool | 5.8.22 | 工具类库 |
| Lombok | 1.18.30 | 简化样板代码 |

### 前端(secondhand-platform-web)

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Vue | 3.4 | 渐进式框架(Composition API) |
| Vite | 5.4 | 构建工具 |
| Vue Router | 4.3 | 路由 |
| Pinia | 2.1 | 状态管理 |
| Element Plus | 2.5 | UI 组件库(按需引入) |
| Axios | 1.6 | HTTP 客户端 |
| Sass | 1.77 | CSS 预处理 |

### AI 服务(ai-service)

| 技术 | 说明 |
| --- | --- |
| FastAPI | 内部 Agent HTTP 接口和令牌校验 |
| LangChain / LangGraph | Agent 工具编排和运行时扩展点 |
| httpx | 异步调用 Java 商品查询接口 |
| Pydantic | 请求、响应和商品卡片模型 |

## 核心功能

- **用户**:注册登录（基于 Redis 的分布式 Session）、账号安全中心、邮箱验证码找回密码、个人资料维护（含校区/学院）、信用分
- **商品**:发布（多图上传、成色、面交地点）、分类浏览、关键词搜索、详情、上下架、库存管理
- **订单**:下单（填写面交地点）、发货、确认收货、取消/拒发；完整订单状态机，买卖家各自视角软删除
- **评价**:订单完成后买家可评价卖家,支持匿名评价,并更新卖家信用分
- **收藏**:商品收藏与取消
- **私信**:买卖双方一对一实时聊天(WebSocket),支持联系卖家创建持久会话、会话列表、聊天记录、未读红点、在线/最近在线状态
- **通知**:订单状态变更与收藏等互动通知,实时推送 + 历史分页 + 已读管理
- **文件存储**:本地存储 / 阿里云 OSS 双模式,可通过配置切换

## 技术亮点

- **Redis 分布式 Session**:Session 外置到 Redis,应用层无状态,便于水平扩展。
- **Redisson 分布式锁防超卖**:按商品维度加锁(`tryLock` 带等待/持有超时,`finally` 校验持有者再释放),下单扣减库存、取消/拒发回补库存复用同一商品锁,降低并发下库存不一致和超卖风险。
- **WebSocket 实时推送**:私信与通知基于 Spring 原生 `WebSocketHandler`,握手复用 Spring Session 登录态(无需额外 token),服务端维护「用户→连接」在线表做点对点推送;读写走 REST、WebSocket 只做下行推送,兼顾落库可靠与实时体验。私信会话独立存储到 `conversation` 表,点击联系卖家即可创建持久空会话,避免未发送第一条消息时会话无法保留。
- **统一响应 + 全局异常处理**:`Result` 统一返回结构,`@RestControllerAdvice` 全局兜底异常,前端处理更一致。
- **拦截器鉴权**:`AuthInterceptor` 统一做登录态校验,白名单放行文档与公开接口。
- **存储抽象**:`UploadPathResolver` 屏蔽本地存储与 OSS 的差异,切换无侵入。
- **接口文档**:集成 Knife4j(OpenAPI 3),启动后可在线调试。
- **测试覆盖**:Service / Controller / Config / Interceptor / 工具类均有单元测试。
- **前端工程化**:Element Plus 按需引入、第三方库分包(vendor 拆分)、生产构建 gzip 压缩。
- **AI 只读查询**:LangChain Agent 只能调用商品搜索和详情工具，商品价格、库存、成色和面交地点都来自 Java 查询结果；服务令牌、超时和 trace ID 跨 Java/Python 传递。

## 项目结构

```
my-project/
├── secondhand-platform-backend/     # 后端(Spring Boot)
│   └── src/main/
│       ├── java/com/secondhand/
│       │   ├── config/              # Redis / Redisson / MyBatis-Plus / Session / OpenAPI 等配置
│       │   ├── controller/          # REST 接口层
│       │   ├── service/             # 业务逻辑层(接口 + impl)
│       │   ├── mapper/              # MyBatis-Plus 数据访问层
│       │   ├── entity/              # 实体与请求/响应对象
│       │   ├── interceptor/         # 登录鉴权拦截器
│       │   ├── handler/             # 全局异常处理
│       │   ├── websocket/           # WebSocket 连接管理与实时推送
│       │   └── util/                # Result / Session / 密码 / 文件 / 缓存等工具
│       └── resources/
│           ├── mapper/              # MyBatis XML
│           ├── sql/                 # 建表与迁移脚本
│           └── application.yml      # 主配置(application-local.yml 放本地密钥,不提交)
├── ai-service/                       # Python 商品查询 Agent
│   ├── app/agent/                    # 状态、工具、提示词和运行时
│   ├── app/tools/                    # Java 商品 HTTP 客户端
│   └── tests/                        # 安全、客户端、工具和 Agent 测试
│
└── secondhand-platform-web/         # 前端(Vue 3 + Vite)
    └── src/
        ├── api/                     # Axios 接口封装
        ├── components/              # 公共组件
        ├── layouts/                 # 布局
        ├── views/                   # 页面(auth / home / product / user)
        ├── router/                  # 路由
        ├── stores/                  # Pinia 状态
        ├── assets/styles/           # SCSS 设计变量与全局样式
        └── utils/                   # 格式化、常量等工具
```

## 数据模型

| 表 | 说明 |
| --- | --- |
| `user` | 用户（含校区、信用分、匿名评价偏好） |
| `category` | 商品分类（树形，预置常见类目） |
| `product` | 商品（价格、库存、成色、面交地点、状态、浏览/收藏计数） |
| `product_image` | 商品图片(`sort=0` 为封面) |
| `order` | 订单(订单号、金额、状态机、买卖家软删除、取消/拒发原因) |
| `evaluation` | 评价(评分、内容、匿名标记) |
| `favorite` | 收藏(用户 - 商品唯一) |
| `conversation` | 私信会话(一对用户一条会话、关联商品、最后消息、最后活跃时间) |
| `message` | 私信(双向、可关联商品、已读标记) |
| `notification` | 通知(类型、关联业务 id、已读标记) |

## 快速开始

### 环境要求

- JDK 17
- Maven 3.6+
- MySQL 8.x
- Redis
- Node.js 18+

### 1. 初始化数据库

执行建表脚本(会创建 `secondhand` 库并预置分类):

```bash
mysql -u root -p < secondhand-platform-backend/src/main/resources/sql/init.sql
```

> **仅新建空数据库时运行 `init.sql`**：脚本会删表重建。已有数据库只执行尚未应用的增量迁移，不要重复运行初始化脚本。

### 2. 配置后端

数据库连接默认在 `secondhand-platform-backend/src/main/resources/application.yml` 中。

密钥等敏感配置放在 **`application-local.yml`**(该文件已被 `.gitignore` 忽略,不会提交)。在 `secondhand-platform-backend/src/main/resources/` 下新建:

主配置通过 `DB_PASSWORD` 环境变量读取数据库密码；也可在本地文件中填写。该文件只保留在自己的电脑上，不要提交真实密码。下列占位符需替换为自己的配置：

```yaml
spring:
  datasource:
    password: <你的MySQL密码>
app:
  oss:
    endpoint: oss-cn-xxx.aliyuncs.com
    access-key-id: <你的 AccessKey ID>
    access-key-secret: <你的 AccessKey Secret>
    bucket-name: <你的 Bucket>
  upload:
    local-path: ./uploads   # 不使用 OSS 时,图片存到本地此目录
```

> 不接入 OSS 也可运行,图片会保存到本地 `upload.local-path`。

### 3. 启动后端

```bash
cd secondhand-platform-backend
mvn spring-boot:run
```

- 服务地址:http://localhost:8080
- 接口文档(Knife4j):http://localhost:8080/doc.html

### 4. 启动 AI 服务

AI 服务启动前，将 Python 的 `INTERNAL_TOKEN` 配置成与 Java 的 `AI_INTERNAL_TOKEN` 相同的值；真实模型密钥只通过环境变量提供：

```bash
cd ai-service
python -m pip install -e ".[dev]"
set INTERNAL_TOKEN=dev-ai-internal-token
set MODEL_NAME=gpt-4o-mini
set OPENAI_API_KEY=<你的模型密钥>
python -m uvicorn app.main:app --reload --port 8000
```

Linux/macOS 将 `set` 替换为 `export`。未配置 `MODEL_NAME` 时，服务仍可启动和通过健康检查，但聊天接口会返回稳定的 503 提示。

### 5. 启动前端

```bash
cd secondhand-platform-web
npm install
npm run dev
```

访问 http://localhost:5173 即可。前端已配置开发代理,将 `/api` 转发到后端 `localhost:8080`。

### 邮箱找回密码配置

找回密码使用绑定邮箱发送六位验证码。启动后端前配置 SMTP 环境变量：

```text
MAIL_HOST=smtp.example.com
MAIL_PORT=465
MAIL_USERNAME=your-mail@example.com
MAIL_PASSWORD=your-smtp-password
MAIL_SMTP_AUTH=true
MAIL_SMTP_SSL=true
```

用户需要在“账号安全”中绑定并验证邮箱。找回流程包含申请验证码、校验验证码、使用一次性凭证重置密码三个接口，验证码有效期为 10 分钟，发送间隔为 60 秒。

注册时邮箱可以留空。登录后进入“账号安全”，通过验证码绑定并验证邮箱；未验证邮箱的账号不能使用自助找回密码。已有数据库需要先执行 `secondhand-platform-backend/src/main/resources/sql/2026-10-08-add-user-email-verified.sql`，新建数据库时 `init.sql` 已包含该字段。

## 界面预览

> 待补充(可放首页、商品详情、订单等页面截图)。

## 作者

- koi · [GitHub](https://github.com/Koi-OoO)
