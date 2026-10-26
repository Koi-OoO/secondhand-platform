-- 为已有数据库补充互动通知发起者快照；不回填旧记录，不删除通知。
ALTER TABLE `notification`
    ADD COLUMN `actor_user_id` BIGINT DEFAULT NULL COMMENT 'public actor id, absent for old notifications',
    ADD COLUMN `actor_nickname` VARCHAR(50) DEFAULT NULL COMMENT 'actor nickname snapshot',
    ADD COLUMN `actor_avatar` VARCHAR(500) DEFAULT NULL COMMENT 'actor avatar snapshot';
