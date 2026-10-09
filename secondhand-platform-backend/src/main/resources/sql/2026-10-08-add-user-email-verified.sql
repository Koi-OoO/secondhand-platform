-- 为邮箱绑定和密码找回增加验证状态
ALTER TABLE `user`
  ADD COLUMN `email_verified` TINYINT NOT NULL DEFAULT 0
  COMMENT '邮箱是否已验证：0=未验证，1=已验证'
  AFTER `email`;
