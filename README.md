# 二手交易平台(Secondhand Platform)

> 一个前后端分离的二手商品交易平台,覆盖用户、商品、订单、评价、收藏的完整交易闭环。后端基于 Spring Boot,前端基于 Vue 3。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.4-42b883)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5-blue)
![Redis](https://img.shields.io/badge/Redis-Session%20%26%20Lock-red)

## 项目简介

本项目模拟校园/社区二手交易场景,实现了从用户注册登录、商品发布、浏览下单、发货收货到评价的完整业务闭环。后端采用经典的 Controller-Service-Mapper 分层架构,引入 Redis 分布式 Session 与 Redisson 分布式锁解决会话共享和并发超卖问题;前端为 Vue 3 单页应用,基于 Element Plus 构建。

## 技术栈

### 后端(secondhand-platform)

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

## 核心功能

- **用户**:注册、登录(基于 Redis 的分布式 Session)、个人资料维护、信用分
- **商品**:发布(多图上传)、分类浏览、关键词搜索、详情、上下架、库存管理
- **订单**:下单、支付、发货(支持批量发货)、确认收货、取消 / 拒发;完整订单状态机,买卖家各自视角软删除
- **评价**:订单完成后买卖双方互评,支持匿名评价
- **收藏**:商品收藏与取消
- **文件存储**:本地存储 / 阿里云 OSS 双模式,可通过配置切换

## 技术亮点

- **Redis 分布式 Session**:Session 外置到 Redis,应用层无状态,便于水平扩展。
- **Redisson 分布式锁防超卖**:按商品维度加锁(`tryLock` 带等待/持有超时,`finally` 校验持有者再释放),下单扣减库存、取消/拒发回补库存全程串行化,杜绝并发超卖。
- **统一响应 + 全局异常处理**:`Result` 统一返回结构,`@RestControllerAdvice` 全局兜底异常,前端处理更一致。
- **拦截器鉴权**:`AuthInterceptor` 统一做登录态校验,白名单放行文档与公开接口。
- **存储抽象**:`UploadPathResolver` 屏蔽本地存储与 OSS 的差异,切换无侵入。
- **接口文档**:集成 Knife4j(OpenAPI 3),启动后可在线调试。
- **测试覆盖**:Service / Controller / 工具类均有单元测试。
- **前端工程化**:Element Plus 按需引入、第三方库分包(vendor 拆分)、生产构建 gzip 压缩。

## 项目结构

```
my-project/
├── secondhand-platform/             # 后端(Spring Boot)
│   └── src/main/
│       ├── java/com/secondhand/
│       │   ├── config/              # Redis / Redisson / MyBatis-Plus / Session / OpenAPI 等配置
│       │   ├── controller/          # REST 接口层
│       │   ├── service/             # 业务逻辑层(接口 + impl)
│       │   ├── mapper/              # MyBatis-Plus 数据访问层
│       │   ├── entity/              # 实体与请求/响应对象
│       │   ├── interceptor/         # 登录鉴权拦截器
│       │   ├── handler/             # 全局异常处理
│       │   └── util/                # Result / Session / 密码 / 文件 / 缓存等工具
│       └── resources/
│           ├── mapper/              # MyBatis XML
│           ├── sql/                 # 建表与迁移脚本
│           └── application.yml      # 主配置(application-local.yml 放本地密钥,不提交)
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
| `user` | 用户(含信用分、匿名评价偏好) |
| `category` | 商品分类(树形,预置常见类目) |
| `product` | 商品(价格、库存、成色、状态、浏览/收藏计数) |
| `product_image` | 商品图片(`sort=0` 为封面) |
| `order` | 订单(订单号、金额、状态机、买卖家软删除、取消/拒发原因) |
| `evaluation` | 评价(评分、内容、匿名标记) |
| `favorite` | 收藏(用户 - 商品唯一) |

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
mysql -u root -p < secondhand-platform/src/main/resources/sql/init.sql
```

### 2. 配置后端

数据库连接默认在 `secondhand-platform/src/main/resources/application.yml` 中(默认 `root / 123456`,按本地实际修改)。

密钥等敏感配置放在 **`application-local.yml`**(该文件已被 `.gitignore` 忽略,不会提交)。在 `secondhand-platform/src/main/resources/` 下新建:

```yaml
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
cd secondhand-platform
mvn spring-boot:run
```

- 服务地址:http://localhost:8080
- 接口文档(Knife4j):http://localhost:8080/doc.html

### 4. 启动前端

```bash
cd secondhand-platform-web
npm install
npm run dev
```

访问 http://localhost:5173 即可。前端已配置开发代理,将 `/api` 转发到后端 `localhost:8080`。

## 界面预览

> 待补充(可放首页、商品详情、订单等页面截图)。

## 作者

- koi · [GitHub](https://github.com/Koi-OoO)
