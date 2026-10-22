-- =====================================================================
--  演示账号口令重置（用于「口令升级前就已经建过库」的本地环境）
--
--  什么时候用：
--    你的本地库是口令升级之前建的，sys_user 里还存着旧口令的密文。
--    （新克隆 + 重新执行 schema.sql / data.sql 的同学不需要执行本脚本。）
--
--  怎么做：
--    1. 执行本脚本 —— 只把四个演示账号的密码清空，不动其他数据；
--    2. 重启后端（profiles = dev,local）；
--       DevDataInitializer 发现密码为空，会自动写入新口令的 BCrypt 密文；
--    3. 用 database/README.md「三、演示账号」里的新口令登录验证。
--
--  注意：不要为了改口令去重跑 schema.sql —— 它开头是 DROP DATABASE，会清空整库。
-- =====================================================================

USE campus_ai_delivery;

UPDATE sys_user
SET password = ''
WHERE deleted = 0
  AND username IN ('20210001', '13900000001', '13700000001', 'admin');

SELECT username,
       role,
       IF(password = '', '待初始化（重启后端后自动写入）', '已设置') AS password_state
FROM sys_user
WHERE deleted = 0;
