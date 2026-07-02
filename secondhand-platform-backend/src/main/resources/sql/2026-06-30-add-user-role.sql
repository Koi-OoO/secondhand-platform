-- ============================================================
-- 管理端支持：用户角色字段
-- date: 2026-06-30
-- ============================================================

-- 1. 为 user 表新增 role 字段
ALTER TABLE `user`
  ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'USER'
  COMMENT '角色：USER=普通用户，ADMIN=管理员'
  AFTER `status`;

-- 2. 初始化已有用户为普通用户（DEFAULT 已处理）

-- 3. 创建默认管理员账号 — 请按以下步骤操作：
--    ① 先确保 MySQL 中 secondhand 库存在且应用能连接
--    ② 执行本脚本（只执行上面的 ALTER TABLE 部分即可）
--    ③ 启动后端应用：mvn spring-boot:run
--    ④ 通过 POST /user/register 注册 admin 用户（或用 curl 调接口）
--    ⑤ 执行：UPDATE `user` SET `role` = 'ADMIN' WHERE `username` = 'admin';
--    这一步的密码哈希由应用自动生成，无需手动计算。
