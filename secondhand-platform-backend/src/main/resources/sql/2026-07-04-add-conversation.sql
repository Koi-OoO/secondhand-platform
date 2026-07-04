-- 私信会话表 conversation：保存一对用户之间的会话入口，即使尚未发送消息也会出现在消息中心
CREATE TABLE IF NOT EXISTS `conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'conversation id',
  `user1_id` BIGINT NOT NULL COMMENT 'smaller user id in the pair',
  `user2_id` BIGINT NOT NULL COMMENT 'larger user id in the pair',
  `product_id` BIGINT DEFAULT NULL COMMENT 'related product id from contact entry, nullable',
  `last_message_id` BIGINT DEFAULT NULL COMMENT 'last message id, nullable before first message',
  `last_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'last active time',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_pair` (`user1_id`, `user2_id`),
  KEY `idx_user1_last` (`user1_id`, `last_time`),
  KEY `idx_user2_last` (`user2_id`, `last_time`),
  KEY `idx_last_message` (`last_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='private message conversations';

-- 用已有 message 数据回填会话入口，保留每对用户最新一条消息作为 last_message_id
INSERT INTO `conversation` (
  `user1_id`,
  `user2_id`,
  `product_id`,
  `last_message_id`,
  `last_time`,
  `create_time`,
  `update_time`
)
SELECT
  pair.user1_id,
  pair.user2_id,
  last_msg.product_id,
  pair.last_message_id,
  last_msg.create_time,
  pair.first_time,
  CURRENT_TIMESTAMP
FROM (
  SELECT
    LEAST(from_user_id, to_user_id) AS user1_id,
    GREATEST(from_user_id, to_user_id) AS user2_id,
    MAX(id) AS last_message_id,
    MIN(create_time) AS first_time
  FROM `message`
  GROUP BY LEAST(from_user_id, to_user_id), GREATEST(from_user_id, to_user_id)
) pair
LEFT JOIN `message` last_msg ON last_msg.id = pair.last_message_id
ON DUPLICATE KEY UPDATE
  `product_id` = COALESCE(`conversation`.`product_id`, VALUES(`product_id`)),
  `last_message_id` = VALUES(`last_message_id`),
  `last_time` = VALUES(`last_time`),
  `update_time` = CURRENT_TIMESTAMP;
