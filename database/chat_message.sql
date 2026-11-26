-- 订单实时聊天消息表
-- 会话按 threadId 聚合（见 chat_message-thread.sql）：
--   群聊 = 订单ID；单聊 = dm:订单ID:角色A-角色B；客服 = svc:订单ID
CREATE TABLE IF NOT EXISTS chat_message (
    id           VARCHAR(32)  NOT NULL COMMENT '消息ID',
    order_id     VARCHAR(96)  NOT NULL COMMENT '会话ID：订单ID / dm:订单ID:A-B / svc:订单ID',
    from_user_id VARCHAR(32)  NOT NULL COMMENT '发送人ID',
    from_role    VARCHAR(20)  NOT NULL COMMENT '发送人角色：STUDENT/MERCHANT/RIDER/ADMIN',
    content      VARCHAR(500) NOT NULL COMMENT '消息内容',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_order_time (order_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单实时聊天消息表';