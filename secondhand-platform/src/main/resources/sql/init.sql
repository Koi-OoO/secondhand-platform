-- secondhand platform bootstrap schema

CREATE DATABASE IF NOT EXISTS `secondhand`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `secondhand`;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'user id',
  `username` VARCHAR(50) NOT NULL COMMENT 'username',
  `password` VARCHAR(255) NOT NULL COMMENT 'password hash',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT 'nickname',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT 'avatar url',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT 'phone number',
  `email` VARCHAR(100) DEFAULT NULL COMMENT 'email',
  `gender` TINYINT DEFAULT 0 COMMENT 'gender',
  `address` VARCHAR(255) DEFAULT NULL COMMENT 'default address',
  `birthday` DATE DEFAULT NULL COMMENT 'birthday',
  `credit_score` INT DEFAULT 100 COMMENT 'credit score',
  `status` TINYINT DEFAULT 1 COMMENT 'account status',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  `anonymous` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'anonymous evaluation preference',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='users';

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT 'category id',
  `name` VARCHAR(50) NOT NULL COMMENT 'category name',
  `parent_id` INT DEFAULT 0 COMMENT 'parent category id',
  `sort` INT DEFAULT 0 COMMENT 'sort order',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='categories';

DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'product id',
  `seller_id` BIGINT NOT NULL COMMENT 'seller user id',
  `title` VARCHAR(100) NOT NULL COMMENT 'title',
  `description` TEXT COMMENT 'description',
  `price` DECIMAL(10,2) NOT NULL COMMENT 'sale price',
  `stock` INT NOT NULL DEFAULT 1 COMMENT 'available stock',
  `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT 'original price',
  `category_id` INT DEFAULT NULL COMMENT 'category id',
  `condition_level` TINYINT DEFAULT 1 COMMENT 'condition level',
  `status` TINYINT DEFAULT 1 COMMENT '1=on sale, 2=off shelf, 3=sold out',
  `view_count` INT DEFAULT 0 COMMENT 'view count',
  `like_count` INT DEFAULT 0 COMMENT 'favorite count',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='products';

DROP TABLE IF EXISTS `product_image`;
CREATE TABLE `product_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'image id',
  `product_id` BIGINT NOT NULL COMMENT 'product id',
  `url` VARCHAR(500) NOT NULL COMMENT 'image url',
  `sort` INT DEFAULT 0 COMMENT 'sort order, 0 is cover',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product images';

DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'order id',
  `order_no` VARCHAR(32) NOT NULL COMMENT 'order number',
  `buyer_id` BIGINT NOT NULL COMMENT 'buyer user id',
  `seller_id` BIGINT NOT NULL COMMENT 'seller user id',
  `product_id` BIGINT NOT NULL COMMENT 'product id',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT 'purchase quantity',
  `product_amount` DECIMAL(10,2) NOT NULL COMMENT 'product subtotal',
  `freight` DECIMAL(10,2) DEFAULT 0 COMMENT 'shipping fee',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT 'paid total amount',
  `address` VARCHAR(500) NOT NULL COMMENT 'shipping address',
  `express_no` VARCHAR(50) DEFAULT NULL COMMENT 'tracking number',
  `status` TINYINT DEFAULT 0 COMMENT '0=unpaid, 1=to ship, 2=to receive, 3=completed, 4=cancelled',
  `buyer_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'hidden in buyer view',
  `seller_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'hidden in seller view',
  `cancel_type` TINYINT DEFAULT NULL COMMENT '1=buyer cancelled, 2=seller rejected shipment',
  `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT 'cancel or reject reason',
  `pay_time` DATETIME DEFAULT NULL COMMENT 'paid at',
  `deliver_time` DATETIME DEFAULT NULL COMMENT 'shipped at',
  `finish_time` DATETIME DEFAULT NULL COMMENT 'completed at',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer` (`buyer_id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='orders';

DROP TABLE IF EXISTS `evaluation`;
CREATE TABLE `evaluation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'evaluation id',
  `order_id` BIGINT NOT NULL COMMENT 'order id',
  `from_user_id` BIGINT NOT NULL COMMENT 'reviewer user id',
  `to_user_id` BIGINT NOT NULL COMMENT 'review target user id',
  `rating` TINYINT NOT NULL COMMENT 'rating',
  `content` VARCHAR(500) DEFAULT NULL COMMENT 'review content',
  `anonymous` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'anonymous review flag',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_from` (`order_id`, `from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='evaluations';

DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'favorite id',
  `user_id` BIGINT NOT NULL COMMENT 'user id',
  `product_id` BIGINT NOT NULL COMMENT 'product id',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='favorites';

INSERT INTO `category` (`name`, `parent_id`, `sort`) VALUES
  ('手机数码', 0, 1),
  ('电脑办公', 0, 2),
  ('家用电器', 0, 3),
  ('服饰鞋包', 0, 4),
  ('图书教材', 0, 5),
  ('运动户外', 0, 6),
  ('其他', 0, 99);
