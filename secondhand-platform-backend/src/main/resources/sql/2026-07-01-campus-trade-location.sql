-- 校园化改造：用户添加校区/学院字段，商品添加交易地点字段
ALTER TABLE `user`
    ADD COLUMN `campus` VARCHAR(100) DEFAULT NULL COMMENT '校区/学院' AFTER `address`;

ALTER TABLE `product`
    ADD COLUMN `trade_location` VARCHAR(200) DEFAULT NULL COMMENT '建议面交地点（如东区食堂门口）' AFTER `stock`;
