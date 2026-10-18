# 商品搜索：Elasticsearch 接入

已实现 Java 客户端、索引校验、商品全量导入和全文搜索接口 `GET /product/search`，返回商品图片与当前展示数据。**商品广场 `/products` 已调用 ES 搜索接口；首页等原有调用继续使用 MySQL 的 `/product/page`。Canal 增量同步代码与部署模板见 [README-canal.md](README-canal.md)，本机已通过真实商品接口到 ES 的新增、改价、换图、上下架和删除同步验证。**

## 配置

IK 升级已完成：正式索引为 `campus_products_v2`，已完成316条商品导入、真实搜索和 Canal 增量验收；原磁盘水位阻塞已解除，默认磁盘保护保持启用。运行配置见 [README-ik.md](README-ik.md)。

配置位于 `src/main/resources/application.yml` 的 `app.search.elasticsearch`，也可以通过 IDEA 运行配置的环境变量覆盖：

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `PRODUCT_SEARCH_ES_ENABLED` | `false` | 是否注册 ES 客户端并在启动时初始化索引 |
| `PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP` | `false` | 本次启动是否将 MySQL 商品全量导入 ES，需要同时启用 ES |
| `PRODUCT_SEARCH_ES_URL` | `http://127.0.0.1:9200` | ES HTTP(S) 地址 |
| `PRODUCT_SEARCH_ES_INDEX` | `campus_products_v2` | 当前 IK 版本的商品索引名 |
| `PRODUCT_SEARCH_ES_CONNECT_TIMEOUT_MS` | `1000` | 建立连接及等待连接池的超时，毫秒 |
| `PRODUCT_SEARCH_ES_SOCKET_TIMEOUT_MS` | `5000` | 读取响应超时，毫秒 |
| `PRODUCT_SEARCH_ES_USERNAME` | 空 | 可选 Basic 用户名 |
| `PRODUCT_SEARCH_ES_PASSWORD` | 空 | 可选 Basic 密码，与用户名一起配置 |

本机现有 ES 无密码且仅监听本机，用户名、密码均保持空即可。真实密码不要写入仓库。HTTPS 连接使用正常证书校验，不会忽略证书错误；URL 不允许内嵌用户名密码。

在 IDEA 中设置 `PRODUCT_SEARCH_ES_ENABLED=true` 后重启 Java，会检查版本、所选中文分词器，并创建缺失的空索引或验证已有索引。成功日志为“已创建空索引”或“已验证现有结构”。关闭开关时，旧 MySQL 列表接口仍可使用，ES 搜索接口返回 HTTP 503，商品广场会显示搜索不可用与重新加载入口。

首次导入时再设置 `PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP=true` 并重启 Java。看到“商品搜索导入完成：共 N 条”后，把这个导入变量改回 `false`，保留启用变量为 `true`。这里使用环境变量控制配置，不需要更改 Java 源码。

导入只读 MySQL，按 ID 游标每批200条读取，以商品 ID 作为 ES 文档 ID。重复导入覆盖同 ID，不生成重复商品，也不会自动清空索引。Bulk 请求逐项检查；部分失败会中断启动并明确报错，已成功的部分不会回滚，修复原因后可以重新导入。导入服务不对外开放 HTTP 接口。

当前全量导入用于初始化：它不是数据库快照，不提供跨 MySQL/ES 事务，不删除历史孤立文档，也未处理导入期间的并发商品变更。Canal 同步默认关闭，启用与初始化顺序见 README-canal.md；全量导入期间暂停写入，不能与 Canal 消费同时启用。商品广场已在本机真实同步验证后切换 ES，运行时需保持 ES 与 Canal 同步启用。正式重建应另建版本索引并规划切换，不能把重复导入当成完整重建。

商品广场每页20件，宽屏5列；价格、成色、分类、关键词、排序和页码会保存到 URL。请求失败显示错误与重试，成功但无结果显示空状态，不自动回退 MySQL。连续查询只展示最新请求结果；超过10000个匹配时保留实际总数，但限制可翻页范围并提示缩小筛选。

## 使用搜索接口

Java 启动并完成导入后，可在浏览器访问：

```text
http://localhost:8080/product/search?keyword=苹果手机
```

| 参数 | 说明 |
| --- | --- |
| `keyword` | 可选，最多200字符；当前默认 IK 分词，要求搜索词同时匹配，标题权重高于描述 |
| `categoryId` | 可选；子类只查该类，大类包括其子类和仍属于该大类的旧商品 |
| `minPrice`、`maxPrice` | 可选，单位为元，不能为负且最低价不高于最高价 |
| `conditionLevel` | 可选，1至4，与现有商品成色定义一致 |
| `sort` | `relevance`（默认）、`latest`、`hottest`、`price_asc`、`price_desc` |
| `page`、`size` | 默认1、20，每页最多50；`page × size` 不超过10000 |

响应沿用 `Result` 和 `Page<Product>`：`data.records` 是商品列表，每件含 `images`（图片对象列表）和 `imageUrls`（地址列表）；`total/current/size/pages` 为分页信息。排序最终使用商品 ID 兜底，在数据不变时翻页顺序稳定；并发改动下不提供翻页快照。

ES 只返回命中 ID，再按 ES 顺序批量回查 MySQL 的商品、卖家展示字段和图片。当前已下架、已删除或价格/分类/成色不再符合条件的商品不会展示。`total` 仍是索引近实时总数，过滤后本页可能不足 `size`；标题和排序在后台任务处理与ES刷新前也可能暂时过期。

参数无效返回 HTTP 400；ES 关闭、不可达、查询超时或分片失败返回 HTTP 503，不伪装成空结果或悄悄回退成 LIKE。错误响应仍沿用项目的 `Result.error` 业务码500，应同时检查 HTTP 状态。

中文分词本身不判断“手机”和“手机配件”：它们都可能包含“苹果”“手机”。需要精确区分时，应把商品发布到对应子类并传入 `categoryId`；系统不会按标题自动重新分类现有商品。

明确启用时，ES 不可达、缺少 IK 或索引不兼容都会阻止本次启动并给出错误，不会悄悄覆盖已有数据。当前不自动修复/重建不兼容索引。需要结构升级时，使用新的版本索引并另行规划数据迁移。

## 文件职责

- `config/ElasticsearchConfig.java`：注册可复用客户端、超时、可选认证，统一关闭连接池。
- `search/ElasticsearchProperties.java`：读取并校验配置；不生成包含密码的 toString。
- `search/ProductSearchIndexInitializer.java`：连接检查、IK 验证、缺失索引创建、已有映射验证。
- `search/ProductSearchDocumentFactory.java`：商品转换、精确金额、祖先分类路径和封面选择。
- `search/ProductSearchImportService.java`：ID 游标读取和 Bulk 导入，只读 MySQL。
- `search/ProductSearchCriteria.java`、`ProductSearchService.java`：搜索参数校验、ES 查询、MySQL 批量回查。
- `controller/ProductSearchController.java`：公开 GET 搜索接口，局部处理400/503。
- `config/ProductSearchConfig.java`：装配业务服务，显式开启时执行启动导入。
- `resources/search/product-index-v2.json`：IK 索引设置及字段类型，类似 MySQL 建表结构。

使用官方 Java API Client `8.19.19` 连接 ES `8.19.23`，客户端与服务端补丁版本可以不同，保持 8.19 系列。JSON API 显式选择 `2.1.3` 与 Parsson `1.1.9` 对齐。ES 使用独立的 Jackson mapper，不修改 Spring MVC 的日期格式；不引入 Spring Data Elasticsearch，也不升级 Spring Boot。

主启动类排除了 Boot 自带的低级 ES 客户端自动配置，避免开关关闭时仍自动创建连接池。客户端生命周期由 transport 统一管理。

## 索引字段约定

索引默认名 `campus_products_v2`，1 个主分片、0 个副本，适合当前本机单节点开发；生产副本规划应根据节点数确定。

| 字段 | ES 类型 | 用途 |
| --- | --- | --- |
| `id`、`sellerId` | long | 商品及卖家 ID；文档 `_id` 使用商品 ID |
| `title`、`description` | text / ik_max_word（查询 ik_smart） | 中文全文搜索 |
| `categoryId` | integer | 商品当前实际分类 |
| `categoryPathIds` | integer 数组 | 当前分类和祖先分类 ID，支持按大类查询子类商品 |
| `priceCents`、`originalPriceCents` | long | 价格，单位为分，避免浮点精度误差 |
| `stock` | integer | 库存 |
| `conditionLevel`、`status` | byte | 成色和商品状态 |
| `viewCount`、`likeCount` | long | 浏览和收藏数量 |
| `createTime`、`updateTime` | date / epoch_millis | 毫秒时间戳，不受前端日期格式影响 |
| `coverUrl`、`tradeLocation` | keyword，不索引 | 展示内容，不参与全文匹配 |

例如一件手机分类为 8，所属大类为 1，则 `categoryId=8`、`categoryPathIds=[1,8]`。旧商品仅有大类时保留 `categoryId=1`、`categoryPathIds=[1]`，不猜测其子类。

导入时，MySQL 的元价格使用 `BigDecimal.movePointRight(2).longValueExact()` 转成分；Java 业务接口继续以元为单位。日期转换为 Unix 毫秒。查询价格下界向上取整为分、上界向下取整为分，避免返回超过预算的商品。

`dynamic=strict` 会拒绝未声明字段，避免商品实体的非搜索字段意外写入。已有索引验证管理标记、字段类型、关键分析/日期配置和查询/排序开关，并拒绝禁用或裁剪 `_source` 的映射，不会覆盖不兼容索引。

## 验证

普通测试使用本地 HTTP 协议替身，无需运行 ES：

```powershell
mvn test
```

真实 ES 测试仅允许本机地址，先启动带 IK 的 ES。在 PowerShell 中设置变量并运行，测试会创建随机临时索引，结束只删除自己的索引：

```powershell
$env:CAMPUS_ES_URL='http://127.0.0.1:9200'
mvn '-Dtest=ElasticsearchLiveTest' test
```

若还要验证实际 Spring Boot 开关，并创建/验证正式 `campus_products_v2` 索引，需要本机 MySQL、Redis 可用，再显式增加：

```powershell
$env:CAMPUS_ES_INITIALIZE_PRODUCT_INDEX='true'
mvn '-Dtest=ElasticsearchLiveTest' test
```

要验证真实316条商品导入、重复导入、HTTP搜索/图片/分页和故障响应，先设置本机 ES 地址，然后运行：测试只写随机临时 ES 索引，结束清理该索引，不修改 MySQL。

```powershell
$env:CAMPUS_ES_PRODUCT_SEARCH_LIVE='true'
mvn '-Dtest=ProductSearchLiveTest' test
```

`CAMPUS_ES_IMPORT_FORMAL=true` 是额外的手动验证开关：只有同时开启上面的真实搜索测试时，才会执行写入正式 `campus_products_v2` 的验证。普通回归不设置它。业务初始化优先使用 IDEA 的 `PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP`，无需运行测试写正式数据。

上述变量只在当前 PowerShell 会话生效。所有真实测试均限制本机地址；临时测试索引只删除自己的随机名称，正式索引不会被清理。测试关闭自己启动的 Java 上下文，不停止用户的 Redis/ES 容器。

参考：[官方客户端安装](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/8.19/installation.html)、[Bulk 导入](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/8.19/indexing-bulk.html)、[搜索接口](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/8.19/searching.html)、[IK 插件](https://github.com/infinilabs/analysis-ik)。
