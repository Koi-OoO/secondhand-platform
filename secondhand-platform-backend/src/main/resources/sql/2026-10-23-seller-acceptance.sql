-- 先增量迁移再启动新后端。不修改旧订单状态，不补造接单时间。
ALTER TABLE `order` ADD COLUMN `accept_time` DATETIME NULL COMMENT 'seller accepted at';
