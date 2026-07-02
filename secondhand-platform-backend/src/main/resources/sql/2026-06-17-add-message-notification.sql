-- 私信表 message：买卖双方一对一聊天记录
CREATE TABLE `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'message id',
  `from_user_id` BIGINT NOT NULL COMMENT '发送者用户 id',
  `to_user_id` BIGINT NOT NULL COMMENT '接收者用户 id',
  `product_id` BIGINT DEFAULT NULL COMMENT '关联商品 id（围绕某商品聊天，可空）',
  `content` VARCHAR(1000) NOT NULL COMMENT '消息内容',
  `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '接收方是否已读：0=未读，1=已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  KEY `idx_to_read` (`to_user_id`, `is_read`),
  KEY `idx_pair` (`from_user_id`, `to_user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='private messages';

-- 通知表 notification：系统/互动通知
CREATE TABLE `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'notification id',
  `user_id` BIGINT NOT NULL COMMENT '接收者用户 id',
  `type` VARCHAR(20) NOT NULL COMMENT '类型：order=订单，favorite=收藏，system=系统',
  `title` VARCHAR(100) NOT NULL COMMENT '标题',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '内容',
  `related_id` BIGINT DEFAULT NULL COMMENT '关联业务 id（如订单 id）',
  `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读，1=已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='notifications';
