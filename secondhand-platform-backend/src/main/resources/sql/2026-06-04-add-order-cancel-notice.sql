USE `secondhand`;

ALTER TABLE `order`
  ADD COLUMN `cancel_type` TINYINT DEFAULT NULL COMMENT '取消来源: 1买家取消 2卖家拒绝发货' AFTER `status`,
  ADD COLUMN `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消或拒绝原因' AFTER `cancel_type`;
