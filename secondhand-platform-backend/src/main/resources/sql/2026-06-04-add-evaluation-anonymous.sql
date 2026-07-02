ALTER TABLE `evaluation`
  ADD COLUMN `anonymous` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否匿名评价'
  AFTER `content`;
