# 商品二级分类

`add_product_subcategories.sql` 为现有七个大类新增 40 个子分类，不修改表结构，不改变商品、图片或原有分类的 ID。通过父分类名称查找实际 ID，重复执行只会补齐缺失记录。

## 执行

1. 在数据库工具中选择项目业务库（本机为 `secondhand`）。
2. 在一个连接中执行整个 `add_product_subcategories.sql`。发生错误立即停止，不要逐条跳过错误继续执行。
3. 首次执行 `inserted_subcategories` 应为 40；已补齐时为 0。原有子分类存在时，新增数量相应减少。
4. 清除当前项目 Redis 数据库中的单个缓存键 `category::tree`，否则 `/category/list` 最多可能继续返回 30 分钟旧分类。不要清空 Redis 数据库。Redis 数据库编号以本地配置为准。
5. 重启 Java 服务以加载新的发布/编辑校验，刷新前端。

没有 Flyway 等迁移框架自动运行此脚本。新环境先初始化业务库，再单独执行本迁移；已有环境不要重新运行会重建表的 `init.sql`。

## 兼容行为

- 新商品必须选择没有子类的具体分类。
- 编辑旧商品时，可以保留原分类；省略 `categoryId` 表示不修改分类。更换分类必须选择叶子。
- 发布页允许显示父分类以兼容历史值，但新选择父分类时会显示“请选择具体子分类”，不能提交。
- 商品广场中，点击大类显示原大类和其直属子类下的商品；点击子类只显示该子类。
- 原有商品不自动根据标题重新分类，因此子分类在人工补齐旧商品之前可能为空。大类浏览仍正常。
- 目前只支持两级结构，与现有分类树接口及商品筛选 SQL 保持一致。

## 检查

```sql
SELECT parent.name AS parent_name, child.id, child.name
FROM category parent JOIN category child ON child.parent_id = parent.id
WHERE parent.parent_id = 0
ORDER BY parent.sort, child.sort, child.id;
```

迁移锁只约束本脚本的并发运行。分类长期维护或增加管理后台时，应另行设计分类权限、唯一约束及缓存失效。
