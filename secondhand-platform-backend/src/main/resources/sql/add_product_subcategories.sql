-- 为校园商品的七个大类补充子分类。请在已有业务数据库中执行，不要运行 init.sql。
-- 不新增字段、不重置表、不更新商品；父分类 ID 按名称查找，不依赖固定编号。
-- 使用事务和命名锁，避免重复执行或并发执行产生重复子分类。
SET NAMES utf8mb4;
SET @subcategory_lock_name = CONCAT(DATABASE(), ':product-subcategories-v1');
SELECT GET_LOCK(@subcategory_lock_name, 10) INTO @subcategory_lock_acquired;

-- 断言失败时制造主键冲突，让不带 --force 的 mysql 客户端停止执行。
CREATE TEMPORARY TABLE subcategory_migration_guard (id INT PRIMARY KEY);
INSERT INTO subcategory_migration_guard VALUES (1);
INSERT INTO subcategory_migration_guard SELECT 1
WHERE COALESCE(@subcategory_lock_acquired, 0) <> 1;

START TRANSACTION;
CREATE TEMPORARY TABLE desired_product_subcategories (
    parent_name VARCHAR(50) NOT NULL,
    child_name VARCHAR(50) NOT NULL,
    child_sort INT NOT NULL,
    PRIMARY KEY (parent_name, child_name)
) CHARACTER SET utf8mb4;

INSERT INTO desired_product_subcategories VALUES
('手机数码', '手机', 1),
('手机数码', '平板', 2),
('手机数码', '耳机', 3),
('手机数码', '摄影器材', 4),
('手机数码', '充电配件', 5),
('手机数码', '保护壳与贴膜', 6),
('手机数码', '其他数码', 99),
('电脑办公', '笔记本', 1),
('电脑办公', '台式机', 2),
('电脑办公', '显示器', 3),
('电脑办公', '键鼠', 4),
('电脑办公', '电脑配件', 5),
('电脑办公', '办公文具', 6),
('电脑办公', '其他办公', 99),
('家用电器', '台灯', 1),
('家用电器', '风扇', 2),
('家用电器', '生活电器', 3),
('家用电器', '厨房电器', 4),
('家用电器', '其他电器', 99),
('服饰鞋包', '上衣', 1),
('服饰鞋包', '裤装', 2),
('服饰鞋包', '鞋', 3),
('服饰鞋包', '背包', 4),
('服饰鞋包', '其他箱包', 5),
('服饰鞋包', '服饰配件', 6),
('服饰鞋包', '其他服饰鞋包', 99),
('图书教材', '专业教材', 1),
('图书教材', '考试资料', 2),
('图书教材', '外语学习', 3),
('图书教材', '文学读物', 4),
('图书教材', '其他图书', 99),
('运动户外', '球类', 1),
('运动户外', '球拍', 2),
('运动户外', '健身器材', 3),
('运动户外', '户外用品', 4),
('运动户外', '其他运动用品', 99),
('其他', '宿舍用品', 1),
('其他', '乐器', 2),
('其他', '手工收藏', 3),
('其他', '其他闲置', 99);

-- 七个父分类必须各自唯一存在，防止运行到错误的库或给同名父类重复插入。
SELECT COUNT(*) INTO @subcategory_parent_count
FROM category
WHERE parent_id = 0
  AND name IN (SELECT DISTINCT parent_name FROM desired_product_subcategories);
SELECT COUNT(DISTINCT name) INTO @subcategory_parent_distinct
FROM category
WHERE parent_id = 0
  AND name IN (SELECT DISTINCT parent_name FROM desired_product_subcategories);
INSERT INTO subcategory_migration_guard SELECT 1
WHERE @subcategory_parent_count <> 7 OR @subcategory_parent_distinct <> 7;

INSERT INTO category (name, parent_id, sort)
SELECT desired.child_name, parent.id, desired.child_sort
FROM desired_product_subcategories desired
JOIN category parent ON parent.name = desired.parent_name AND parent.parent_id = 0
WHERE @subcategory_lock_acquired = 1
  AND @subcategory_parent_count = 7 AND @subcategory_parent_distinct = 7
  AND NOT EXISTS (
      SELECT 1 FROM category existing
      WHERE existing.parent_id = parent.id AND existing.name = desired.child_name
  );

SELECT ROW_COUNT() AS inserted_subcategories;
COMMIT;
SELECT RELEASE_LOCK(@subcategory_lock_name) AS released_migration_lock;
DROP TEMPORARY TABLE desired_product_subcategories;
DROP TEMPORARY TABLE subcategory_migration_guard;
