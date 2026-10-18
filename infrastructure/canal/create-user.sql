-- 以管理员身份执行，先替换密码占位文字；本文件不是自动迁移，不随 Java 启动执行。
-- 用户名和密码都属于 MySQL。Docker 的 .env 使用相同用户名和密码。
-- % 允许容器来源连接；生产环境应根据实际部署收窄来源地址。
CREATE USER 'canal_campus'@'%' IDENTIFIED BY 'REPLACE_WITH_CANAL_PASSWORD';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal_campus'@'%';
GRANT SELECT ON secondhand.product TO 'canal_campus'@'%';
GRANT SELECT ON secondhand.product_image TO 'canal_campus'@'%';
GRANT SELECT ON secondhand.category TO 'canal_campus'@'%';
-- GRANT 会立即生效，不需要 FLUSH PRIVILEGES。
