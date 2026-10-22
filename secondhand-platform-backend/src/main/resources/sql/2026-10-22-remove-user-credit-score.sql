-- 移除用户信用分；保留 evaluation 表中的星级和文字评价。
-- 仅对现有数据库执行一次，不要重新执行 init.sql（初始化脚本会重建表）。
-- 先部署移除 creditScore 字段的新后端，再执行此迁移。
ALTER TABLE `user` DROP COLUMN `credit_score`;
