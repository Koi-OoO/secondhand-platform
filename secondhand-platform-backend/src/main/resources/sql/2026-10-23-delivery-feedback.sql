-- 增量迁移，旧记录保持空值；不自动完成任何订单。
ALTER TABLE `order`
 ADD COLUMN `delivery_issue` VARCHAR(500) NULL,
 ADD COLUMN `delivery_issue_time` DATETIME NULL,
 ADD COLUMN `last_remind_time` DATETIME NULL;
