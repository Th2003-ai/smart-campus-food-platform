-- 订单实时聊天消息表
-- 聊天室按订单聚合：学生、商户、骑手三方围绕同一笔订单沟通
CREATE TABLE IF NOT EXISTS chat_message (
    id           VARCHAR(32)  NOT NULL COMMENT '消息ID',
    order_id     VARCHAR(32)  NOT NULL COMMENT '订单ID（聊天室）',
    from_user_id VARCHAR(32)  NOT NULL COMMENT '发送人ID',
    from_role    VARCHAR(20)  NOT NULL COMMENT '发送人角色：STUDENT/MERCHANT/RIDER/ADMIN',
    content      VARCHAR(500) NOT NULL COMMENT '消息内容',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_order_time (order_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单实时聊天消息表';