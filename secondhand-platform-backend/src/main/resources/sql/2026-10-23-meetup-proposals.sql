-- 先增量迁移再启动后端。不修改旧订单时间或旧聊天。
ALTER TABLE `order` ADD COLUMN `meetup_time` DATETIME NULL COMMENT 'buyer confirmed meetup time';
ALTER TABLE `message` ADD COLUMN `meetup_proposal_id` BIGINT NULL COMMENT 'trusted meetup proposal reference';
CREATE TABLE IF NOT EXISTS `meetup_proposal` (
 `id` BIGINT NOT NULL AUTO_INCREMENT,
 `order_id` BIGINT NOT NULL,
 `seller_id` BIGINT NOT NULL,
 `buyer_id` BIGINT NOT NULL,
 `order_no` VARCHAR(32) NULL,
 `product_title` VARCHAR(100) NULL,
 `address` VARCHAR(500) NULL,
 `proposed_time` DATETIME NOT NULL,
 `status` VARCHAR(16) NOT NULL,
 `create_time` DATETIME NOT NULL,
 `responded_time` DATETIME NULL,
 PRIMARY KEY (`id`), KEY `idx_order_status` (`order_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='meetup proposal history';
