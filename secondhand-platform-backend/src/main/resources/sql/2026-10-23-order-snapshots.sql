-- 增量迁移：先执行本脚本，再启动新后端。不要重跑 init.sql。
-- 保留历史订单的 NULL，不用当前商品信息冒充历史快照。
ALTER TABLE `order`
  ADD COLUMN `snapshot_title` VARCHAR(100) NULL COMMENT 'product title at order creation',
  ADD COLUMN `snapshot_image` VARCHAR(1000) NULL COMMENT 'product cover at order creation',
  ADD COLUMN `unit_price` DECIMAL(10,2) NULL COMMENT 'unit price at order creation',
  ADD COLUMN `cancel_time` DATETIME NULL COMMENT 'cancelled or rejected at';
