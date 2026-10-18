# Canal 商品变化同步

数据流：MySQL binlog → Canal 1.1.8 TCP → Java 消费者 → ES。Java 会回查当前 MySQL 数据，以同一商品 ID 覆盖搜索文档；未找到商品则删除 ES 文档。商品下架后文档保留当前状态，搜索只返回在售商品。同步只读 MySQL，不向业务表写入，不新增同步任务表。

## 本机验证结果（2026-10-17）

已通过现有运行中的 Java 服务执行真实注册、登录、发布及商品管理接口，并直接读取 ES 核对变化：新增商品及图片/分类路径、改价、更换封面、下架后搜索隐藏、重新上架后恢复搜索、软删除后搜索隐藏、清理时物理删除对应 ES 文档均通过。编辑和删除前遵循既有“先下架”业务规则。

验证没有手动写 ES、手动刷新索引或另外启动消费者。按 ID 读取文档和全文搜索可见时间不同，因此搜索检查等待正常刷新。随机临时账号、商品、图片已精确清理，原有业务表记录摘要一致，ES 从 316 条恢复至 316 条。证据保存在本机忽略目录 `target/canal-live-result.json`。

随后已完成 ES 停止后的自动补同步，以及 Canal 停机期间变化在重启后的续传：ES 不可用时商品修改仍保存至 MySQL、搜索返回503；ES 恢复后自动同步停机期间的价格；Canal 重启后补同步价格与图片，后续新变化也能持续同步。容器均恢复运行，临时数据已清理，原有业务记录摘要一致，证据为本机 `target/canal-recovery-result.json`。

2026-10-18 又完成 Java 进程重启续传验证：先确认临时下架商品在 MySQL 和 ES 中均为11.11元，用户在 IDEA 停止后端；验证8080无监听后仅将该临时商品的 MySQL 价格改为7.77元，确认 ES 仍为11.11元。用户保持原配置重新启动 Java 后，ES 自动更新为7.77元。测试商品已精确清理，清理事件也自动同步到 ES，文档数量恢复，原有业务记录摘要一致。证据为本机 `target/java-restart-result.json`。

上述结果覆盖本机短时停机恢复，不代表已验证超出 binlog 保留期、位点文件损坏或多实例切换。分类结构变更的真实场景仍待单独验收。商品广场已切换 `/product/search`，使用 ES 搜索；首页等旧列表调用保留 MySQL。

## 当前范围

- 监听 `secondhand.product`、`product_image`、`category`；图片更换/删除、封面排序和图片转移都会刷新相关商品。
- 分类变化分批扫描全部现存商品，刷新祖先分类路径；不依赖分类旧数据，也不猜测缺失分类。
- 批次成功写入 ES 后才 ack。异常 rollback 并退避重连；重复事件使用稳定 ID，允许至少一次投递。
- 单进程单线程消费者，不能让多个 Java 实例同时消费同一个 destination；多实例方案需另外设计选主。
- 相关 DDL 阻塞并持续重试，不静默确认。遇到 DDL 需人工验证映射、必要时重建，再有计划地处理位点；不能直接跳过。
- 商品广场已在上述真实链路验证后切换 ES，运行时需同时保持 ES 与同步链路可用。

## 1. 创建 MySQL 专用账号

已检查配置应为 `log_bin=ON`、`binlog_format=ROW`、`binlog_row_image=FULL`、`server_id=1`。

在 IDEA 的数据库控制台打开项目根目录的 `infrastructure/canal/create-user.sql`，先替换密码占位文字，再使用管理员执行。它只创建账号和授予权限，不改商品数据。若账号已存在，先检查授权，不要重复执行建账号或随意覆盖密码。

复制 `infrastructure/canal/.env.example` 为同目录 `.env`，填写 `CANAL_MYSQL_PASSWORD`。这份密码供 Canal 登录 MySQL；不是 Java 登录 Canal 的密码。`.env` 已忽略，不能提交真实凭据。Docker 管理员可查看容器环境变量；本配置只适用于本机开发。

宿主机 MySQL 使用 `host.docker.internal:3306`；容器里的 `127.0.0.1` 指 Canal 自己，不能用于连接 Windows 上的 MySQL。防火墙或 MySQL 监听范围拒绝容器连接时应按具体错误处理，不自动修改系统设置。

Canal 的 TSDB 首次初始化会先枚举可见库。Compose 同时配置库级排除规则，跳过 `secondhand` 以外的库，再用表级白名单限定三张表。这样无需给专用账号增加 `performance_schema` 等系统库的读取权限。

## 2. 启动 Canal

先在 Docker Desktop 启动现有的 ES、Redis。以下命令在项目根目录 PowerShell 执行。

下面只检查 Compose 配置能否解析，不创建容器，也不会打印展开后的密码：

```powershell
docker compose --env-file infrastructure/canal/.env -f infrastructure/canal/compose.yml config --quiet
```

下面创建并后台启动 Canal；也会创建保存消费位点和日志的命名卷：

```powershell
docker compose --env-file infrastructure/canal/.env -f infrastructure/canal/compose.yml up -d
```

下面查看实例日志，确认它确实已连接 MySQL；TCP 健康检查只说明端口可连，不代表 binlog 已正确解析：

```powershell
docker exec canal tail -n 60 /home/admin/canal-server/logs/campus/campus.log
```

如果实例日志尚未创建，先看服务日志：`docker exec canal tail -n 60 /home/admin/canal-server/logs/canal/canal.log`。检查错误时不要把密码或整行敏感数据贴到聊天。

在 Docker Desktop Containers 中可看到 `campus-search` 下的 `canal`，后续可用可视化按钮停止/启动。配置更新后应重新执行 `up -d`，单纯 Restart 不会更新创建时环境变量。不要使用 `down -v`：它会删除消费位点。不要把 `docker compose config` 的完整输出发到聊天，它可能包含密码。

## 3. 首次导入与消费顺序

1. 暂停 Java 业务写入和其他直接数据库修改。
2. 启动 Canal，确认已成功连接 MySQL；保持配置卷和消费位点，初始化期间不要重建容器或清空卷。
3. Java 设置 `PRODUCT_SEARCH_ES_ENABLED=true`、`PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP=true`、`PRODUCT_SEARCH_CANAL_ENABLED=false`，启动完成现有商品导入。
4. 停止 Java，把导入开关设回 `false`，把 `PRODUCT_SEARCH_CANAL_ENABLED=true`，再启动。
5. 确认消费者连接后恢复写入，完成下面的端到端验收。

全量导入和增量消费禁止同时运行，避免全量旧快照覆盖增量新数据。默认 destination 是 `campus`、schema 是 `secondhand`，其他配置名见 `application.yml`。本机 11111 仅绑定 loopback，默认 Java 到 Canal 的用户名和密码为空；部署到服务器时应另配 Canal 客户端认证及网络隔离，不能把 MySQL 密码直接当成 Canal 客户端密码。

若 ES 曾手动删除文档、已有孤立文档或 Canal 位点丢失/超出 binlog 保留期，重复导入现存商品不能清理旧文档，应暂停写入，使用新版本索引完整重建并重新规划同步位点。当前不提供在线无锁重建与索引别名切换。

## 4. 验收与限制

建议新建一条明确标记的临时商品测试，不修改原有商品：验证发布、改标题价格、换图片、下架、恢复、删除，并比对 ES 中对应商品 ID 的文档。再测试停止 ES 后修改临时商品，恢复 ES 后确认重试成功；停止/重启 Java、Canal 后确认新增变化继续同步。分类测试使用独立临时分类，避免破坏真实分类路径。

日志会记录连接状态、累计失败次数和最后成功批次时间；失败不打印原始凭据、SQL 或 binlog 行数据。连接成功或最后成功时间并不能证明没有积压，仍需结合 Canal 实例日志、binlog 位点和实际文档验证。当前没有生产级告警/延迟指标，也不提供零延迟或跨 MySQL/ES 事务保证。

Canal 使用内存缓冲和文件位点，必须保证 MySQL binlog 保留期覆盖停机时间。磁盘位点可能滞后导致重放，幂等写入可处理重复；若日志已被清理，必须明确报错并安排重建，不能自动跳至最新位点。分类删除导致仍被引用的路径缺失时会阻塞，需修复源数据或制定迁移方案。

实现参考：[Canal 官方快速开始](https://github.com/alibaba/canal/wiki/QuickStart)、[1.1.8 文件位点配置](https://github.com/alibaba/canal/blob/canal-1.1.8/deployer/src/main/resources/canal.properties)。

已知依赖限制：测试会提示 Canal 1.1.8 内置 Protobuf 生成代码较旧，存在上游提示的拒绝服务风险。项目没有关闭该警告，也没有声称升级 Protobuf 运行库即可消除它；本次连接仅限可信本机服务，生产使用前需评估上游重新生成协议代码或兼容升级方案。
