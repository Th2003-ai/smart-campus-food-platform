-- 聊天会话线程：order_id 从「订单ID」改为「会话 threadId」
-- 群聊 threadId = 订单ID（32 位）
-- 单聊 threadId = dm:{订单ID}:{角色A}-{角色B}
-- 客服 threadId = svc:{订单ID}
-- 最长约 51 字符，原 VARCHAR(32) 不够，扩到 96。
ALTER TABLE chat_message
    MODIFY COLUMN order_id VARCHAR(96) NOT NULL COMMENT '会话ID：订单ID / dm:订单ID:A-B / svc:订单ID';

-- 老数据无需迁移：群聊记录的 order_id 本身就是订单ID，语义不变。