ALTER TABLE `order`
  ADD COLUMN `buyer_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '买家视角是否隐藏：0=否，1=是' AFTER `status`,
  ADD COLUMN `seller_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '卖家视角是否隐藏：0=否，1=是' AFTER `buyer_deleted`;
