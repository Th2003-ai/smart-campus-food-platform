-- =============================================================================
-- 角色数据化：把「角色」从 Java 枚举迁移到数据库表（RBAC 角色表）
--
-- 目标：新增/停用/改名角色只改数据库，不改代码、不重新发版。
-- 说明：
--   1) sys_role 是角色字典；sys_user.role、sys_role_permission.role 都引用它的 code；
--   2) builtin=1 的四个角色是「四端入口」用的内置角色，不允许删除；
--   3) allow_register=1 的角色才允许自助注册（管理员不开注册）；
--   4) 脚本可重复执行（IF NOT EXISTS + ON DUPLICATE KEY UPDATE）。
--
-- 执行：mysql -u root -p campus_ai_delivery < upgrade-2026-11-17-sys-role.sql
-- =============================================================================

USE campus_ai_delivery;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    code           VARCHAR(32)  NOT NULL COMMENT '角色编码（大写字母/数字/下划线）',
    name           VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description    VARCHAR(255)          DEFAULT NULL COMMENT '角色说明',
    sort           INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    builtin        TINYINT      NOT NULL DEFAULT 0 COMMENT '1=内置角色（四端入口用，不可删除）',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用（停用后不能登录）',
    allow_register TINYINT      NOT NULL DEFAULT 0 COMMENT '1=允许自助注册',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色表（RBAC 角色字典，数据驱动）';

INSERT INTO sys_role (code, name, description, sort, builtin, status, allow_register)
VALUES ('STUDENT', '学生', '学生端用户：浏览、下单、评价、查看饮食报告', 10, 1, 1, 1),
       ('MERCHANT', '商户', '商户端用户：管理店铺、菜品、订单与评价', 20, 1, 1, 1),
       ('RIDER', '骑手', '骑手端用户：抢单、取餐与配送', 30, 1, 1, 1),
       ('ADMIN', '管理员', '管理端用户：审核、配置、角色与权限管理', 40, 1, 1, 0)
ON DUPLICATE KEY UPDATE name           = VALUES(name),
                        description    = VALUES(description),
                        sort           = VALUES(sort),
                        builtin        = VALUES(builtin),
                        allow_register = VALUES(allow_register),
                        status         = 1;

-- 校验：四个内置角色都在
SELECT code, name, builtin, status, allow_register FROM sys_role ORDER BY sort;

-- 角色编码上限 32：把引用方列一起加宽（原来只有 20，新增长编码角色会写不进去）
ALTER TABLE sys_user
    MODIFY COLUMN role VARCHAR(32) NOT NULL COMMENT '角色编码，引用 sys_role.code';
ALTER TABLE sys_role_permission
    MODIFY COLUMN role VARCHAR(32) NOT NULL COMMENT '角色编码，引用 sys_role.code';

-- ---------------------------------------------------------------------
-- 新角色「进系统」需要的两列：
--   portal    所属端（STUDENT/MERCHANT/RIDER/ADMIN），决定能用哪套页面与接口；
--   home_path 登录后首页（如 /admin/dashboard），留空则落到「暂无工作台」提示页。
-- 新角色只要选一个 portal + 首页路径，就能像内置角色一样进入对应端。
-- ---------------------------------------------------------------------
ALTER TABLE sys_role
    ADD COLUMN portal    VARCHAR(20)  NULL COMMENT '所属端：STUDENT/MERCHANT/RIDER/ADMIN，决定可用页面与接口' AFTER allow_register,
    ADD COLUMN home_path VARCHAR(200) NULL COMMENT '登录后首页路由，如 /admin/dashboard' AFTER portal;

UPDATE sys_role SET portal = 'STUDENT',  home_path = '/student/home'      WHERE code = 'STUDENT';
UPDATE sys_role SET portal = 'MERCHANT', home_path = '/merchant/dashboard' WHERE code = 'MERCHANT';
UPDATE sys_role SET portal = 'RIDER',    home_path = '/rider/hall'         WHERE code = 'RIDER';
UPDATE sys_role SET portal = 'ADMIN',    home_path = '/admin/dashboard'    WHERE code = 'ADMIN';

-- 已存在的自定义角色：先给个安全的默认值，管理员再去「角色管理」里改
UPDATE sys_role SET portal = 'ADMIN', home_path = '/admin/dashboard'
WHERE portal IS NULL AND code <> 'ADMIN';

SELECT code, name, portal, home_path, builtin, status FROM sys_role ORDER BY sort;
