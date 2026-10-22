-- =====================================================================
--  任务2：树形数据结构可视化 —— 系统菜单权限表（sys_menu）增量脚本
--
--  作用：在已建好的 campus_ai_delivery 库上，新增「菜单表 + 演示菜单数据」。
--  说明：
--    1) 表通过 parent_id 自引用表达树形层级（0 表示根），
--       共三层：目录(menu_type=0) → 菜单(menu_type=1) → 按钮(menu_type=2)；
--    2) 脚本可重复执行（建表 IF NOT EXISTS，数据先按演示 ID 清理再插入）；
--    3) 执行：mysql -u root -p campus_ai_delivery < menu_tree.sql
--       或在 Navicat / IDEA 数据库控制台里整段运行。
-- =====================================================================

USE campus_ai_delivery;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 建表（与同步追加到 schema.sql 的定义保持一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id           VARCHAR(32) NOT NULL COMMENT '菜单ID',
    parent_id    VARCHAR(32) NOT NULL DEFAULT '0' COMMENT '父菜单ID，0表示根节点',
    menu_name    VARCHAR(50) NOT NULL COMMENT '菜单名称',
    menu_type    TINYINT     NOT NULL DEFAULT 1 COMMENT '节点类型：0目录 1菜单 2按钮',
    path         VARCHAR(200)         DEFAULT NULL COMMENT '前端路由地址',
    component    VARCHAR(255)         DEFAULT NULL COMMENT '前端组件路径',
    perms        VARCHAR(100)         DEFAULT NULL COMMENT '权限标识，如 admin:audit:pass',
    icon         VARCHAR(50)          DEFAULT NULL COMMENT '图标（Element Plus 图标名）',
    sort         INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    visible      TINYINT     NOT NULL DEFAULT 1 COMMENT '是否显示：0隐藏 1显示',
    status       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统菜单权限表（树形结构）';

-- ---------------------------------------------------------------------
-- 演示数据（清理旧演示数据后重插，保证脚本可重复执行）
-- ---------------------------------------------------------------------
DELETE FROM sys_menu WHERE id IN (
    '100','101','102','103','1031','1032','104','105',
    '200','201','202','2021','2022','203','2031','2032','204','205',
    '300','301','302','303',
    '400','401','4011','4012','402','4021','4022','403','404','405','4051','4052'
);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
-- ===== 学生端（目录） =====
('100',  '0',   '学生端',       0, NULL, NULL, NULL, 'User',          10, 1, 1),
('101',  '100', '首页点餐',     1, '/student/home',    'views/student/Home',     NULL, 'Dish',          10, 1, 1),
('102',  '100', '购物车',       1, '/student/cart',    'views/student/Cart',     NULL, 'ShoppingCart',  20, 1, 1),
('103',  '100', '我的订单',     1, '/student/orders',  'views/student/Orders',   NULL, 'List',          30, 1, 1),
('1031', '103', '去评价',       2, NULL, NULL, 'student:order:comment', NULL, 10, 1, 1),
('1032', '103', '申请售后',     2, NULL, NULL, 'student:order:refund',  NULL, 20, 1, 1),
('104',  '100', '饮食画像',     1, '/student/profile', 'views/student/Profile',  NULL, 'Food',          40, 1, 1),
('105',  '100', '个人中心',     1, '/student/me',      'views/student/Me',       NULL, 'UserFilled',    50, 1, 1),

-- ===== 商户端（目录） =====
('200',  '0',   '商户端',       0, NULL, NULL, NULL, 'Shop',          20, 1, 1),
('201',  '200', '店铺管理',     1, '/merchant/shop',       'views/merchant/Shop',     NULL, 'Shop',           10, 1, 1),
('202',  '200', '菜品管理',     1, '/merchant/dish',       'views/merchant/Dish',     NULL, 'Dish',           20, 1, 1),
('2021', '202', '上架菜品',     2, NULL, NULL, 'dish:create', NULL, 10, 1, 1),
('2022', '202', '库存调整',     2, NULL, NULL, 'dish:stock',  NULL, 20, 1, 1),
('203',  '200', '订单处理',     1, '/merchant/orders',     'views/merchant/Orders',   NULL, 'Tickets',        30, 1, 1),
('2031', '203', '接单',         2, NULL, NULL, 'order:accept', NULL, 10, 1, 1),
('2032', '203', '出餐',         2, NULL, NULL, 'order:ready',  NULL, 20, 1, 1),
('204',  '200', '评价管理',     1, '/merchant/reviews',    'views/merchant/Reviews',  NULL, 'ChatDotRound',   40, 1, 1),
('205',  '200', 'AI经营分析',   1, '/merchant/ai-analysis','views/merchant/AiAnalysis', NULL, 'TrendCharts',  50, 1, 1),

-- ===== 骑手端（目录） =====
('300',  '0',   '骑手端',       0, NULL, NULL, NULL, 'Bicycle',       30, 1, 1),
('301',  '300', '订单大厅',     1, '/rider/hall',       'views/rider/Hall',       NULL, 'Flag',           10, 1, 1),
('302',  '300', '我的配送',     1, '/rider/delivering', 'views/rider/Delivering', NULL, 'Bicycle',        20, 1, 1),
('303',  '300', '收入统计',     1, '/rider/income',     'views/rider/Income',     NULL, 'Wallet',         30, 1, 1),

-- ===== 管理端（目录） =====
('400',  '0',   '管理端',       0, NULL, NULL, NULL, 'Setting',       40, 1, 1),
('401',  '400', '商户审核',     1, '/admin/audit-merchant', 'views/admin/AuditMerchant', NULL, 'CircleCheck', 10, 1, 1),
('4011', '401', '审核通过',     2, NULL, NULL, 'admin:audit:pass',   NULL, 10, 1, 1),
('4012', '401', '审核驳回',     2, NULL, NULL, 'admin:audit:reject', NULL, 20, 1, 1),
('402',  '400', '骑手审核',     1, '/admin/audit-rider',    'views/admin/AuditRider',    NULL, 'Avatar',      20, 1, 1),
('4021', '402', '审核通过',     2, NULL, NULL, 'admin:rider:pass',   NULL, 10, 1, 1),
('4022', '402', '审核驳回',     2, NULL, NULL, 'admin:rider:reject', NULL, 20, 1, 1),
('403',  '400', '数据看板',     1, '/admin/dashboard',  'views/admin/Dashboard', NULL, 'DataAnalysis',   30, 1, 1),
('404',  '400', '客服工单',     1, '/admin/tickets',    'views/admin/Tickets',   NULL, 'Service',        40, 1, 1),
('405',  '400', '系统管理',     0, NULL, NULL, NULL, 'Tools',         50, 1, 1),
('4051', '405', '菜单管理',     1, '/admin/menu',       'views/admin/MenuTree',  NULL, 'Menu',          10, 1, 1),
('4052', '405', '权限配置',     1, '/admin/permission', 'views/admin/Permission',NULL, 'Lock',          20, 1, 1);

-- ---------------------------------------------------------------------
-- 验证：应返回 4 个根节点；统计共 34 个菜单节点
-- ---------------------------------------------------------------------
SELECT id, parent_id, menu_name, menu_type FROM sys_menu WHERE parent_id = '0' ORDER BY sort;
SELECT COUNT(*) AS total_menu_nodes FROM sys_menu;
