/*==============================================================*/
/* Database name:  campus_ai_delivery                           */
/* DBMS name:      MySQL 5.0                                    */
/* Created on:     2026/10/18 15:24:31                          */
/*==============================================================*/


drop database if exists campus_ai_delivery;

/*==============================================================*/
/* Database: campus_ai_delivery                                 */
/*==============================================================*/
create database campus_ai_delivery;

use campus_ai_delivery;

/*==============================================================*/
/* Table: admin                                                 */
/*==============================================================*/
create table admin
(
   id                   VARCHAR(32) not null comment '管理员ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   real_name            VARCHAR(50) default NULL comment '姓名',
   admin_level          TINYINT not null default 1 comment '管理员级别：1普通 2超级',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_user_id (user_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='管理员表';

/*==============================================================*/
/* Table: ai_chat_message                                       */
/*==============================================================*/
create table ai_chat_message
(
   id                   VARCHAR(32) not null comment '消息ID',
   session_id           VARCHAR(32) not null comment '所属会话ID',
   role                 VARCHAR(20) not null comment '角色：user / assistant / system',
   content              TEXT default NULL comment '消息内容',
   tokens               INT default NULL comment 'Token 消耗',
   model_version        VARCHAR(50) default NULL comment '模型版本',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   key idx_session_id (session_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 对话消息表';

/*==============================================================*/
/* Table: ai_chat_session                                       */
/*==============================================================*/
create table ai_chat_session
(
   id                   VARCHAR(32) not null comment '会话ID',
   user_id              VARCHAR(32) not null comment '所属用户ID',
   session_type         VARCHAR(20) not null comment '会话类型：ORDER点餐 / SEARCH搜索 / SERVICE客服',
   title                VARCHAR(100) default NULL comment '会话标题（取首句）',
   message_count        INT not null default 0 comment '消息数',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   key idx_user_type (user_id, session_type)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 对话会话表';

/*==============================================================*/
/* Table: ai_config                                             */
/*==============================================================*/
create table ai_config
(
   id                   VARCHAR(32) not null comment '配置ID',
   provider             VARCHAR(50) not null comment '服务商，如 openai-compatible / doubao / qwen',
   api_url              VARCHAR(255) not null comment 'API 地址',
   model_name           VARCHAR(100) not null comment '模型名称',
   api_key              VARCHAR(255) not null comment 'API Key（加密存储，禁止返回前端）',
   temperature          DECIMAL(3,2) not null default 0.70 comment '温度参数',
   max_tokens           INT not null default 2048 comment '最大输出 Token',
   timeout_ms           INT not null default 15000 comment '超时时间（毫秒）',
   is_default           TINYINT not null default 0 comment '是否默认配置：0否 1是',
   status               TINYINT not null default 1 comment '状态：0停用 1启用',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='大模型配置表';

/*==============================================================*/
/* Table: banner                                                */
/*==============================================================*/
create table banner
(
   id                   VARCHAR(32) not null comment '轮播图ID',
   title                VARCHAR(100) default NULL comment '标题',
   image_url            VARCHAR(255) not null comment '图片URL',
   link_url             VARCHAR(255) default NULL comment '跳转链接',
   sort                 INT not null default 0 comment '排序值',
   status               TINYINT not null default 1 comment '状态：0停用 1启用',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='首页轮播图表';

/*==============================================================*/
/* Table: cart_item                                             */
/*==============================================================*/
create table cart_item
(
   id                   VARCHAR(32) not null comment '购物车项ID',
   user_id              VARCHAR(32) not null comment '所属学生ID',
   shop_id              VARCHAR(32) not null comment '所属店铺ID（同一店铺才可一起结算）',
   dish_id              VARCHAR(32) not null comment '菜品ID',
   spec_id              VARCHAR(32) default NULL comment '规格ID',
   quantity             INT not null default 1 comment '数量',
   selected             TINYINT not null default 1 comment '是否勾选结算：0否 1是',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   key idx_user_shop (user_id, shop_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='购物车表';

/*==============================================================*/
/* Table: delivery_record                                       */
/*==============================================================*/
create table delivery_record
(
   id                   VARCHAR(32) not null comment '配送记录ID',
   order_id             VARCHAR(32) not null comment '订单ID',
   rider_id             VARCHAR(32) not null comment '骑手ID',
   grab_time            DATETIME default NULL comment '抢单时间',
   pickup_time          DATETIME default NULL comment '取餐时间',
   finish_time          DATETIME default NULL comment '送达时间',
   duration_min         INT default NULL comment '配送耗时（分钟）',
   delivery_fee         DECIMAL(10,2) not null default 0.00 comment '本单配送费（骑手收入）',
   status               TINYINT not null default 0 comment '配送状态：0待取餐 1配送中 2已送达 3异常',
   abnormal_type        VARCHAR(50) default NULL comment '异常类型：地址错误/学生拒收/餐品损坏等',
   abnormal_desc        VARCHAR(255) default NULL comment '异常说明',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_order_id (order_id),
   key idx_rider_id (rider_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='配送记录表';

/*==============================================================*/
/* Table: diet_profile                                          */
/*==============================================================*/
create table diet_profile
(
   id                   VARCHAR(32) not null comment '档案ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   diet_goal            TINYINT not null default 0 comment '饮食目标：0普通 1减脂 2增肌',
   taste_preference     VARCHAR(100) default NULL comment '口味偏好，逗号分隔，如"清淡,微辣"',
   allergy_foods        TEXT default NULL comment '忌口/过敏原食材列表（不超过500字），AI点餐时过滤',
   dislike_foods        TEXT default NULL comment '不喜欢的食材',
   height_cm            INT default NULL comment '身高（cm），用于营养估算',
   weight_kg            DECIMAL(5,1) default NULL comment '体重（kg），用于营养估算',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   unique key uk_user_id (user_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户饮食档案表';

/*==============================================================*/
/* Table: diet_report                                           */
/*==============================================================*/
create table diet_report
(
   id                   VARCHAR(32) not null comment '报告ID',
   user_id              VARCHAR(32) not null comment '用户ID',
   period_type          TINYINT not null comment '周期：7近7天 30近30天',
   start_date           DATE not null comment '统计开始日期',
   end_date             DATE not null comment '统计结束日期',
   order_count          INT not null default 0 comment '统计期内订单数',
   structure_data       TEXT default NULL comment '饮食结构数据（JSON：高油/高盐/荤素比例等）',
   nutrition_data       TEXT default NULL comment '营养估算数据（JSON：热量/蛋白质等）',
   suggestion           TEXT default NULL comment 'AI 健康建议',
   allergy_risk         TEXT default NULL comment '过敏原风险提醒',
   model_version        VARCHAR(50) default NULL comment '模型版本',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '生成时间',
   primary key (id),
   key idx_user_period (user_id, period_type)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='饮食分析报告表';

/*==============================================================*/
/* Table: dish                                                  */
/*==============================================================*/
create table dish
(
   id                   VARCHAR(32) not null comment '菜品ID',
   shop_id              VARCHAR(32) not null comment '所属店铺ID',
   category_id          VARCHAR(32) default NULL comment '菜品分类ID',
   dish_name            VARCHAR(50) not null comment '菜品名称（1-50字符）',
   price                DECIMAL(10,2) not null comment '菜品单价（0.01-9999.99）',
   image                VARCHAR(255) default NULL comment '主图URL（对象存储）',
   description          VARCHAR(500) default NULL comment '菜品描述（AI 语义匹配的依据）',
   tags                 VARCHAR(200) default NULL comment '菜品标签，逗号分隔，如"清淡,低脂,不辣"',
   ingredients          VARCHAR(255) default NULL comment '主要食材，逗号分隔（过敏原过滤依据）',
   stock                INT not null default 0 comment '库存数量（>=0，不可为负）',
   status               TINYINT not null default 1 comment '上下架状态：0下架 1上架',
   monthly_sales        INT not null default 0 comment '月销量（近30天）',
   score                DECIMAL(3,2) not null default 5.00 comment '菜品评分（0.00-5.00）',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   deleted              TINYINT not null default 0 comment '逻辑删除：0未删除 1已删除',
   primary key (id),
   key idx_shop_id (shop_id),
   key idx_category_id (category_id),
   key idx_status (status)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品表';

/*==============================================================*/
/* Table: dish_category                                         */
/*==============================================================*/
create table dish_category
(
   id                   VARCHAR(32) not null comment '分类ID',
   shop_id              VARCHAR(32) not null comment '所属店铺ID',
   name                 VARCHAR(50) not null comment '分类名称',
   sort                 INT not null default 0 comment '排序值',
   status               TINYINT not null default 1 comment '状态：0停用 1启用',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   key idx_shop_id (shop_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品分类表';

/*==============================================================*/
/* Table: dish_image                                            */
/*==============================================================*/
create table dish_image
(
   id                   VARCHAR(32) not null comment '图片ID',
   dish_id              VARCHAR(32) not null comment '所属菜品ID',
   url                  VARCHAR(255) not null comment '图片URL（对象存储）',
   sort                 INT not null default 0 comment '排序值',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   key idx_dish_id (dish_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品图片表';

/*==============================================================*/
/* Table: dish_spec                                             */
/*==============================================================*/
create table dish_spec
(
   id                   VARCHAR(32) not null comment '规格ID',
   dish_id              VARCHAR(32) not null comment '所属菜品ID',
   spec_name            VARCHAR(50) not null comment '规格名称，如"大份""微辣"',
   price_diff           DECIMAL(10,2) not null default 0.00 comment '相对基准价的差价，可为负',
   stock                INT default NULL comment '该规格库存，NULL 表示共用菜品库存',
   sort                 INT not null default 0 comment '排序值',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   key idx_dish_id (dish_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品规格表';

/*==============================================================*/
/* Table: grab_log                                              */
/*==============================================================*/
create table grab_log
(
   id                   VARCHAR(32) not null comment '日志ID',
   order_id             VARCHAR(32) not null comment '订单ID',
   rider_id             VARCHAR(32) not null comment '骑手ID',
   result               TINYINT not null comment '结果：1成功 0失败',
   fail_reason          VARCHAR(100) default NULL comment '失败原因：已被抢走/超出接单上限/工作状态非接单中',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   key idx_order_id (order_id),
   key idx_rider_id (rider_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='抢单日志表';

/*==============================================================*/
/* Table: knowledge_base                                        */
/*==============================================================*/
create table knowledge_base
(
   id                   VARCHAR(32) not null comment '知识ID',
   category             VARCHAR(50) default NULL comment '分类：订单/退款/配送/账号等',
   question             VARCHAR(255) not null comment '标准问题',
   answer               TEXT not null comment '标准答案',
   keywords             VARCHAR(255) default NULL comment '关键词，逗号分隔，用于检索召回',
   hit_count            INT not null default 0 comment '命中次数',
   status               TINYINT not null default 1 comment '状态：0停用 1启用',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   key idx_category (category)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='客服知识库表';

/*==============================================================*/
/* Table: merchant                                              */
/*==============================================================*/
create table merchant
(
   id                   VARCHAR(32) not null comment '商户ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   merchant_name        VARCHAR(100) not null comment '商户名称',
   contact_name         VARCHAR(50) default NULL comment '联系人',
   contact_phone        VARCHAR(20) default NULL comment '联系电话',
   license_no           VARCHAR(64) default NULL comment '营业执照/校园经营许可编号',
   audit_status         TINYINT not null default 0 comment '审核状态：0待审核 1已通过 2已驳回',
   audit_remark         VARCHAR(255) default NULL comment '审核意见/驳回原因',
   audit_time           DATETIME default NULL comment '审核时间',
   audit_by             VARCHAR(32) default NULL comment '审核人（管理员ID）',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_user_id (user_id),
   key idx_audit_status (audit_status)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商户表';

/*==============================================================*/
/* Table: operation_log                                         */
/*==============================================================*/
create table operation_log
(
   id                   VARCHAR(32) not null comment '日志ID',
   user_id              VARCHAR(32) default NULL comment '操作人ID',
   role                 VARCHAR(20) default NULL comment '操作人角色',
   module               VARCHAR(50) default NULL comment '模块',
   action               VARCHAR(100) default NULL comment '操作描述',
   target_id            VARCHAR(32) default NULL comment '操作对象ID',
   detail               TEXT default NULL comment '操作明细（JSON）',
   ip                   VARCHAR(64) default NULL comment '来源IP',
   cost_ms              INT default NULL comment '耗时（毫秒）',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '操作时间',
   primary key (id),
   key idx_user_id (user_id),
   key idx_module (module),
   key idx_create_time (create_time)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';

/*==============================================================*/
/* Table: order_item                                            */
/*==============================================================*/
create table order_item
(
   id                   VARCHAR(32) not null comment '明细ID',
   order_id             VARCHAR(32) not null comment '所属订单ID',
   dish_id              VARCHAR(32) not null comment '菜品ID',
   dish_name            VARCHAR(50) not null comment '菜品名称（下单时快照，防止菜品改名影响历史订单）',
   dish_image           VARCHAR(255) default NULL comment '菜品图片快照',
   spec_name            VARCHAR(50) default NULL comment '规格名称快照',
   price                DECIMAL(10,2) not null comment '成交单价快照',
   quantity             INT not null comment '数量',
   amount               DECIMAL(10,2) not null comment '小计金额 = price * quantity',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   key idx_order_id (order_id),
   key idx_dish_id (dish_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细表';

/*==============================================================*/
/* Table: order_status_log                                      */
/*==============================================================*/
create table order_status_log
(
   id                   VARCHAR(32) not null comment '记录ID',
   order_id             VARCHAR(32) not null comment '订单ID',
   from_status          TINYINT default NULL comment '变更前状态',
   to_status            TINYINT not null comment '变更后状态',
   operator_id          VARCHAR(32) default NULL comment '操作人ID',
   operator_role        VARCHAR(20) default NULL comment '操作人角色',
   reason               VARCHAR(255) default NULL comment '变更原因',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '变更时间',
   primary key (id),
   key idx_order_id (order_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单状态流转记录表';

/*==============================================================*/
/* Table: orders                                                */
/*==============================================================*/
create table orders
(
   id                   VARCHAR(32) not null comment '订单ID',
   order_no             VARCHAR(32) not null comment '订单编号（对外展示，全局唯一）',
   user_id              VARCHAR(32) not null comment '下单学生ID',
   shop_id              VARCHAR(32) not null comment '店铺ID',
   rider_id             VARCHAR(32) default NULL comment '配送骑手ID（抢单成功后写入）',
   goods_amount         DECIMAL(10,2) not null default 0.00 comment '商品金额',
   delivery_fee         DECIMAL(10,2) not null default 0.00 comment '配送费',
   total_amount         DECIMAL(10,2) not null default 0.00 comment '订单实付总金额',
   pay_type             TINYINT default NULL comment '支付方式：1余额 2模拟第三方',
   pay_status           TINYINT not null default 0 comment '支付状态：0未支付 1已支付 2已退款',
   pay_time             DATETIME default NULL comment '支付时间',
   order_status         TINYINT not null default 0 comment '订单状态：0待接单 1备餐中 2待取餐 3配送中 4已完成 5已取消 6申诉中',
   delivery_type        TINYINT not null default 1 comment '配送方式：1骑手配送 2宿舍楼下自取',
   receiver             VARCHAR(50) default NULL comment '收货人',
   receiver_phone       VARCHAR(20) default NULL comment '收货电话',
   address              VARCHAR(255) default NULL comment '收货地址',
   remark               VARCHAR(255) default NULL comment '学生备注（忌口等）',
   pickup_code          VARCHAR(6) default NULL comment '取餐码（商户出餐时生成，000000-999999）',
   cancel_reason        VARCHAR(255) default NULL comment '取消/拒单原因',
   expect_time          DATETIME default NULL comment '预计送达时间（AI 估算）',
   finish_time          DATETIME default NULL comment '订单完成时间',
   version              INT not null default 0 comment '乐观锁版本号（防止非法状态变更）',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '下单时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_order_no (order_no),
   key idx_user_status (user_id, order_status),
   key idx_shop_status (shop_id, order_status),
   key idx_rider_status (rider_id, order_status),
   key idx_create_time (create_time)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单主表';

/*==============================================================*/
/* Table: payment_record                                        */
/*==============================================================*/
create table payment_record
(
   id                   VARCHAR(32) not null comment '支付记录ID',
   order_id             VARCHAR(32) not null comment '订单ID',
   user_id              VARCHAR(32) not null comment '支付人',
   amount               DECIMAL(10,2) not null comment '支付金额',
   pay_type             TINYINT not null comment '支付方式：1余额 2模拟第三方',
   status               TINYINT not null default 0 comment '状态：0待支付 1成功 2失败 3已退款',
   trade_no             VARCHAR(64) default NULL comment '模拟交易号',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_order_id (order_id),
   key idx_user_id (user_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='模拟支付记录表';

/*==============================================================*/
/* Table: recommend_record                                      */
/*==============================================================*/
create table recommend_record
(
   id                   VARCHAR(32) not null comment '记录ID',
   user_id              VARCHAR(32) not null comment '用户ID',
   scene                VARCHAR(30) not null comment '场景：HOME首页 / AI_ORDER点餐 / SEARCH搜索',
   input_text           VARCHAR(500) default NULL comment '用户自然语言输入（点餐/搜索场景）',
   parsed_condition     VARCHAR(500) default NULL comment '解析出的结构化条件（JSON）',
   dish_ids             VARCHAR(500) default NULL comment '推荐菜品ID列表，逗号分隔',
   reason               TEXT default NULL comment 'AI 推荐理由',
   model_version        VARCHAR(50) default NULL comment '模型版本',
   verify_status        TINYINT not null default 0 comment '校验状态：0未校验 1通过 2降级',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '生成时间',
   primary key (id),
   key idx_user_scene (user_id, scene)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 推荐记录表';

/*==============================================================*/
/* Table: review                                                */
/*==============================================================*/
create table review
(
   id                   VARCHAR(32) not null comment '评价ID',
   order_id             VARCHAR(32) not null comment '关联订单ID',
   user_id              VARCHAR(32) not null comment '评价人（学生）',
   shop_id              VARCHAR(32) not null comment '被评价店铺ID',
   rider_id             VARCHAR(32) default NULL comment '被评价骑手ID（配送评价）',
   taste_score          TINYINT not null comment '口味评分：1-5',
   service_score        TINYINT default NULL comment '商家服务评分：1-5',
   delivery_score       TINYINT default NULL comment '配送评分：1-5',
   content              TEXT default NULL comment '评价内容（不超过500字）',
   is_anonymous         TINYINT not null default 0 comment '是否匿名：0否 1是',
   ai_tags              VARCHAR(200) default NULL comment 'AI提取的评价标签，逗号分隔',
   sentiment            TINYINT default NULL comment '情感倾向：0差评 1中评 2好评（AI生成）',
   ai_model_version     VARCHAR(50) default NULL comment '生成标签的模型版本',
   ai_analyze_time      DATETIME default NULL comment 'AI分析时间',
   merchant_reply       VARCHAR(500) default NULL comment '商家回复',
   reply_time           DATETIME default NULL comment '回复时间',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_order_id (order_id),
   key idx_shop_id (shop_id),
   key idx_user_id (user_id),
   key idx_sentiment (sentiment)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='评价表';

/*==============================================================*/
/* Table: review_image                                          */
/*==============================================================*/
create table review_image
(
   id                   VARCHAR(32) not null comment '图片ID',
   review_id            VARCHAR(32) not null comment '所属评价ID',
   url                  VARCHAR(255) not null comment '图片URL（对象存储）',
   sort                 INT not null default 0 comment '排序值',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   key idx_review_id (review_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='评价图片表';

/*==============================================================*/
/* Table: review_tag                                            */
/*==============================================================*/
create table review_tag
(
   id                   VARCHAR(32) not null comment '标签ID',
   review_id            VARCHAR(32) not null comment '所属评价ID',
   shop_id              VARCHAR(32) not null comment '冗余店铺ID，便于按店铺聚合',
   tag_name             VARCHAR(50) not null comment '标签名，如"口味好""出餐慢"',
   tag_type             VARCHAR(20) not null comment '维度：TASTE口味 / PORTION分量 / SPEED速度 / HYGIENE卫生 / SERVICE服务 / PRICE价格',
   sentiment            TINYINT not null comment '情感：0负面 1中性 2正面',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   key idx_shop_tag (shop_id, tag_name),
   key idx_review_id (review_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='评价标签表（AI 标签聚合的数据源）';

/*==============================================================*/
/* Table: rider                                                 */
/*==============================================================*/
create table rider
(
   id                   VARCHAR(32) not null comment '骑手ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   real_name            VARCHAR(50) not null comment '真实姓名',
   id_card              VARCHAR(32) default NULL comment '身份证号（加密存储，展示脱敏）',
   student_card_no      VARCHAR(32) default NULL comment '学生证/工号',
   vehicle_type         VARCHAR(20) default NULL comment '配送车辆：步行/自行车/电动车',
   work_status          TINYINT not null default 0 comment '工作状态：0休息中 1接单中',
   delivering_count     INT not null default 0 comment '当前配送中订单数（抢单成功+1，完成-1）',
   max_delivering       INT not null default 3 comment '同时最大接单数（默认3单）',
   total_income         DECIMAL(10,2) not null default 0.00 comment '累计收入',
   avg_delivery_time    INT default NULL comment '历史平均配送时长（分钟）',
   audit_status         TINYINT not null default 0 comment '审核状态：0待审核 1已通过 2已驳回',
   audit_remark         VARCHAR(255) default NULL comment '审核意见',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_user_id (user_id),
   key idx_work_status (work_status),
   key idx_audit_status (audit_status)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='骑手表';

/*==============================================================*/
/* Table: shop                                                  */
/*==============================================================*/
create table shop
(
   id                   VARCHAR(32) not null comment '店铺ID',
   merchant_id          VARCHAR(32) not null comment '所属商户ID（数据行级隔离依据）',
   category_id          VARCHAR(32) default NULL comment '店铺分类ID',
   shop_name            VARCHAR(100) not null comment '店铺名称',
   logo                 VARCHAR(255) default NULL comment '店铺Logo URL',
   description          VARCHAR(500) default NULL comment '店铺介绍',
   address              VARCHAR(255) default NULL comment '店铺地址（校园内）',
   phone                VARCHAR(20) default NULL comment '联系电话',
   notice               VARCHAR(500) default NULL comment '店铺公告',
   open_time            VARCHAR(50) default NULL comment '营业时间，如"07:00-21:00"',
   business_status      TINYINT not null default 1 comment '营业状态：0休息中 1营业中',
   ban_status           TINYINT not null default 0 comment '封禁状态：0正常 1已封禁',
   min_price            DECIMAL(10,2) not null default 0.00 comment '起送价',
   delivery_fee         DECIMAL(10,2) not null default 0.00 comment '配送费',
   delivery_time        INT default NULL comment '预计配送时长（分钟）',
   score                DECIMAL(3,2) not null default 5.00 comment '综合评分（0.00-5.00）',
   monthly_sales        INT not null default 0 comment '月销量',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   deleted              TINYINT not null default 0 comment '逻辑删除：0未删除 1已删除',
   primary key (id),
   key idx_merchant_id (merchant_id),
   key idx_category_id (category_id),
   key idx_business_status (business_status)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='店铺表';

/*==============================================================*/
/* Table: shop_category                                         */
/*==============================================================*/
create table shop_category
(
   id                   VARCHAR(32) not null comment '分类ID',
   name                 VARCHAR(50) not null comment '分类名称',
   icon                 VARCHAR(255) default NULL comment '分类图标URL',
   sort                 INT not null default 0 comment '排序值，越小越靠前',
   status               TINYINT not null default 1 comment '状态：0停用 1启用',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='店铺分类表';

/*==============================================================*/
/* Table: student                                               */
/*==============================================================*/
create table student
(
   id                   VARCHAR(32) not null comment '学生ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   student_no           VARCHAR(20) not null comment '学号（用于身份验证）',
   real_name            VARCHAR(50) default NULL comment '姓名',
   college              VARCHAR(100) default NULL comment '学院',
   grade                VARCHAR(20) default NULL comment '年级',
   balance              DECIMAL(10,2) not null default 0.00 comment '账户余额（模拟支付用，>=0.00）',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_user_id (user_id),
   unique key uk_student_no (student_no)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学生用户表';

/*==============================================================*/
/* Table: sys_config                                            */
/*==============================================================*/
create table sys_config
(
   id                   VARCHAR(32) not null comment '配置ID',
   config_key           VARCHAR(64) not null comment '配置键',
   config_value         VARCHAR(500) default NULL comment '配置值',
   remark               VARCHAR(255) default NULL comment '说明',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   primary key (id),
   unique key uk_config_key (config_key)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统配置表';

/*==============================================================*/
/* Table: sys_role_permission                                   */
/*==============================================================*/
create table sys_role_permission
(
   id                   VARCHAR(32) not null comment '主键',
   role                 VARCHAR(20) not null comment '角色：STUDENT/MERCHANT/RIDER/ADMIN',
   permission           VARCHAR(64) not null comment '权限标识，如 order:read / dish:write',
   data_scope           VARCHAR(32) not null default 'SELF' comment '数据范围：ALL全部 / SHOP本店 / SELF本人',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   unique key uk_role_permission (role, permission)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限表（权限数据缓存到 Redis）';

/*==============================================================*/
/* Table: sys_user                                              */
/*==============================================================*/
create table sys_user
(
   id                   VARCHAR(32) not null comment '用户ID',
   username             VARCHAR(50) not null comment '登录账号（学生用学号，商户/骑手用手机号，管理员自定义）',
   password             VARCHAR(100) not null comment '密码（BCrypt 加盐哈希，禁止明文）',
   role                 VARCHAR(20) not null comment '角色：STUDENT/MERCHANT/RIDER/ADMIN',
   nickname             VARCHAR(50) default NULL comment '昵称',
   phone                VARCHAR(20) default NULL comment '手机号（返回前端时脱敏）',
   avatar               VARCHAR(255) default NULL comment '头像URL（对象存储）',
   status               TINYINT not null default 1 comment '账号状态：0禁用 1正常',
   last_login_time      DATETIME default NULL comment '最近登录时间',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   deleted              TINYINT not null default 0 comment '逻辑删除：0未删除 1已删除',
   primary key (id),
   unique key uk_username (username),
   key idx_role (role)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='账号表（四种角色共用登录底座）';

/*==============================================================*/
/* Table: ticket                                                */
/*==============================================================*/
create table ticket
(
   id                   VARCHAR(32) not null comment '工单ID',
   ticket_no            VARCHAR(32) not null comment '工单编号',
   user_id              VARCHAR(32) not null comment '提交人',
   session_id           VARCHAR(32) default NULL comment '关联对话会话ID',
   order_id             VARCHAR(32) default NULL comment '关联订单ID',
   category             VARCHAR(50) default NULL comment '问题分类（AI自动分类）',
   question             TEXT not null comment '问题描述',
   status               TINYINT not null default 0 comment '状态：0待处理 1处理中 2已关闭',
   handler_id           VARCHAR(32) default NULL comment '处理人（管理员ID）',
   reply                TEXT default NULL comment '处理回复',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   close_time           DATETIME default NULL comment '关闭时间',
   primary key (id),
   unique key uk_ticket_no (ticket_no),
   key idx_status (status),
   key idx_user_id (user_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='客服工单表';

/*==============================================================*/
/* Table: user_address                                          */
/*==============================================================*/
create table user_address
(
   id                   VARCHAR(32) not null comment '地址ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   receiver             VARCHAR(50) not null comment '收货人',
   phone                VARCHAR(20) not null comment '联系电话',
   campus_area          VARCHAR(100) default NULL comment '校区/园区',
   building             VARCHAR(100) default NULL comment '宿舍楼/教学楼',
   detail               VARCHAR(255) default NULL comment '详细地址',
   is_default           TINYINT not null default 0 comment '是否默认：0否 1是',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   update_time          DATETIME not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
   deleted              TINYINT not null default 0 comment '逻辑删除：0未删除 1已删除',
   primary key (id),
   key idx_user_id (user_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户收货地址表';

/*==============================================================*/
/* Table: user_favorite                                         */
/*==============================================================*/
create table user_favorite
(
   id                   VARCHAR(32) not null comment '收藏ID',
   user_id              VARCHAR(32) not null comment '关联 sys_user.id',
   target_type          TINYINT not null comment '收藏类型：1店铺 2菜品',
   target_id            VARCHAR(32) not null comment '收藏对象ID',
   create_time          DATETIME not null default CURRENT_TIMESTAMP comment '创建时间',
   primary key (id),
   unique key uk_user_target (user_id, target_type, target_id)
)
ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户收藏表';

