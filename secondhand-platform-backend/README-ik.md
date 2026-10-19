# IK 商品搜索

## 2026-10-18 分类与搜索准确性修复

300条演示商品已按原始清单归入子分类，手机30条，另16条原有商品不自动迁移；商品与图片摘要已核对。执行前预览和备份保存在本机忽略目录 `category-migration.local/`，不提交个人业务数据。

索引包含 `primaryTitle`，只在明确的主商品后附赠另一商品时截断赠品内容。品牌/商品类型约束主标题，容量等剩余属性仍允许搜索描述；普通关键词继续全文查询，不只支持手机。

明确机型数字与专业版也约束主标题，防止赠品中的型号冒充主商品；其余属性从主标题和描述查询。结构化类型短语显式使用 `ik_max_word` 与索引一致，避免“手机壳”在 smart/max 切分不同导致配件漏排；普通全文查询仍使用映射的 `ik_smart`。

2026-10-19 真实 ES 与内部接口共10项验证通过，包括苹果手机、充电器、iPhone13、赠品型号排除、促销前缀、描述属性、图片、鉴权和503。随机测试索引已清理；本轮未调用真实模型。验证日志：本机 `target/search-fix-final-live.log`。

用户在 IDEA 启动日常服务后已完成固定名称切换验收：实际查询与Canal写入统一使用 `campus_products`；手机分类30条、苹果手机10条、充电器10条、iPhone13共10条，1800元上限返回6条。Python真实工具客户端卡片及图片正常。随机临时商品发布、下架、改价、物理清理均自动同步；清理后恢复316条，10张业务表原记录摘要一致。证据：本机 `target/campus-products-running-result.json`。

当前仅保留 `campus_products` 商品索引，共316条；停用索引已清理。日常 IDEA 服务使用 `PRODUCT_SEARCH_ES_INDEX=campus_products`，保持 ES 与 Canal 开启、启动全量导入关闭。迁移前的本机备份位于忽略目录 `category-migration.local/`。

项目仅使用 IK，正式索引为 `campus_products`。标题和描述写入使用 `ik_max_word`，查询使用 `ik_smart`，启动时检查两种分词器及已有映射兼容性。不再提供分词器切换配置或 SmartCN 回退分支。

## 运行配置

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `PRODUCT_SEARCH_ES_INDEX` | `campus_products` | 查询、全量导入、Canal 写入目标 |
| `PRODUCT_SEARCH_ES_ENABLED` | `false` | 开启 ES |
| `PRODUCT_SEARCH_ES_IMPORT_ON_STARTUP` | `false` | 显式全量导入，成功后关闭 |
| `PRODUCT_SEARCH_CANAL_ENABLED` | `false` | 开启增量消费，与启动导入互斥 |

此前 IDEA 中如设置过 `PRODUCT_SEARCH_ES_ANALYSIS_PROFILE`，可以移除，该变量已不再使用。

## 已验证能力

2026-10-19 完整后端回归202项，其中194项通过、8项真实环境用例默认跳过；单独开启的10项搜索及内部接口测试全部通过。固定名称切换后，实际Java服务查询、临时商品自动同步和清理均验证通过。日志：本机 `target/index-rename-unit.log`、`target/index-rename-live.log`。

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
