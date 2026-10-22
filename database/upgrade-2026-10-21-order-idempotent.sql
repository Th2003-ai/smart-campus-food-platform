-- =============================================================================
-- 增量升级脚本：下单幂等（request_id + 唯一索引）
--
-- 适用场景：数据库已经按旧版 schema.sql 建过，不想重建库。
-- 全新安装不需要执行本脚本 —— schema.sql 里已经包含这些变更。
--
-- 执行一次即可。若重复执行，ADD COLUMN / ADD KEY 会报 1060/1061 错误，
-- 说明已经升级过了，可以忽略。
-- =============================================================================

USE campus_ai_delivery;

-- 1) 客户端请求号：前端每次进入结算页生成一个 UUID，随请求头 X-Request-Id 提交
ALTER TABLE orders
    ADD COLUMN request_id VARCHAR(64) DEFAULT NULL
        COMMENT '客户端请求号（下单幂等键，同一用户+同一请求号只允许一单）'
        AFTER remark;

-- 2) 数据库层兜底：同一用户 + 同一请求号只能有一条订单
--    MySQL 的唯一索引允许多个 NULL，所以历史订单（request_id 为 NULL）不受影响
ALTER TABLE orders
    ADD UNIQUE KEY uk_user_request (user_id, request_id);

-- 校验
SHOW INDEX FROM orders WHERE Key_name = 'uk_user_request';
