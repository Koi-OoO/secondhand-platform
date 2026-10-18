# IK 商品搜索

项目仅使用 IK，正式索引为 `campus_products_v2`。标题和描述写入使用 `ik_max_word`，查询使用 `ik_smart`，启动时检查两种分词器及已有映射兼容性。不再提供分词器切换配置或 SmartCN 回退分支。

## 运行配置

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `PRODUCT_SEARCH_ES_INDEX` | `campus_products_v2` | 查询、全量导入、Canal 写入目标 |
| `PRODUCT_SEARCH_ES_ENABLED` | `false` | 开启 ES |
| `PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP` | `false` | 显式全量导入，成功后关闭 |
| `PRODUCT_SEARCH_CANAL_ENABLED` | `false` | 开启增量消费，与启动导入互斥 |

此前 IDEA 中如设置过 `PRODUCT_SEARCH_ES_ANALYSIS_PROFILE`，可以移除，该变量已不再使用。

## 已验证能力

2026-10-18 已验证316条商品导入、中文查询、图片、筛选、分页，以及商品新增、改价、换图、上下架和删除经 Canal 自动同步至 v2；临时数据已清理。商品广场9组浏览器场景通过。清理后完整后端测试142项全部通过（包含5项真实环境测试）。旧 `campus_products_v1` 已删除，v2仍为316条，运行中的业务中文查询正常。最新测试结果见本地 `target/ik-cleanup-tests.log`。

## 后续升级

需要修改映射时，新建版本索引，暂停写入与消费、导入当前 MySQL 数据、校验后统一切换查询与 Canal，再恢复写入。不覆盖现有映射。已停用的旧索引不作为回退方案。

## 容器重建

当前容器已安装匹配 ES8.19.23 的 IK，普通重启保留；删除容器重建会丢失原地安装的插件。`infrastructure/elasticsearch/Dockerfile` 仅安装 IK，可用于后续构建；尚未构建或替换当前容器。当前容器中的旧分词插件暂留，不参与业务，避免为卸载而重启 ES。

构建命令仅生成新镜像，不替换现有容器：

```powershell
docker build -t campus-elasticsearch:8.19.23-ik infrastructure/elasticsearch
```

更换容器时保留数据挂载及本机端口限制，不删除数据目录。磁盘水位保护保持默认。

IK 来源：[项目说明](https://github.com/infinilabs/analysis-ik)、[8.19.23插件](https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.19.23.zip)。安装包版本已核对；插件安装时的旧安全策略及网络访问 entitlement 提示未隐藏，本机未配置远程词典。

## AI 分支接入

`ai-marketplace` 中 Python 仍调用已鉴权的 `/internal/marketplace/listings/search`。Java 的 AI 查询入口复用 `ProductSearchService`，与商品广场共享 IK 索引、筛选和展示数据；不再用旧 MySQL 搜索兜底。详情查询仍不增加浏览量。

启动 Java 需开启 `PRODUCT_SEARCH_ES_ENABLED=true`，日常保持 `PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP=false`，需要自动同步时开启 `PRODUCT_SEARCH_CANAL_ENABLED=true`。合并分支后重启 IDEA 后端，Python 端不需要更换接口地址；服务令牌和模型配置沿用原配置。

已通过真实内部接口鉴权、IK 中文搜索、图片、零预算、分页边界及503测试（`CAMPUS_AI_ES_LIVE=true`、`AiEsSearchLiveTest`）。Python 工具客户端和 Agent 单元测试通过；本轮未调用真实收费模型，不将工具查询验收等同于真实模型端到端验收。
