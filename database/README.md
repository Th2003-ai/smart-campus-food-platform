# 数据库说明

## 一、环境要求

- MySQL 8.0（兼容 5.7+），字符集 `utf8mb4`
- Redis 6+（缓存、抢单池、分布式锁）

## 二、初始化步骤

```bash
# 1. 建库建表（脚本内含 DROP DATABASE，重复执行会清空数据，请谨慎）
mysql -u root -p < schema.sql

# 2. 初始化演示数据
mysql -u root -p campus_ai_delivery < data.sql
```

或在客户端（Navicat / DBeaver / DataGrip）中依次打开并执行两个脚本。

### 已经建过库的本地环境怎么升级

除口令外，表结构有增量改动时按时间顺序执行 `upgrade-*.sql`：

```bash
# 订单幂等（orders.request_id + 唯一索引）
mysql -u root -p campus_ai_delivery < upgrade-2026-10-21-order-idempotent.sql

# 角色数据化（新增 sys_role 表；sys_user.role / sys_role_permission.role 加宽到 32）
mysql -u root -p campus_ai_delivery < upgrade-2026-11-17-sys-role.sql
```

## 三、演示账号

| 角色 | 登录账号 | 初始密码 | 说明 |
| --- | --- | --- | --- |
| 学生 | `20210001` | `Stu@Campus2026` | 学号登录，含饮食档案（减脂、忌香菜花生） |
| 商户 | `13900000001` | `Mch@Campus2026` | 对应「第八食堂·二楼档口」 |
| 骑手 | `13700000001` | `Rid@Campus2026` | 工作状态为接单中 |
| 管理员 | `admin` | `Adm@Campus#2026` | 超级管理员 |

> 这四个口令**仅用于本地开发与答辩演示**：四个角色各不相同（不用同一口令，避免一个账号泄露牵连其余角色）；
> 口令在库中以 BCrypt 加盐哈希存储（`$2a$10$` 前缀、60 位），不存明文；
> 正式部署必须删除演示账号、改用个人账号，并把口令放到环境变量或配置中心。

`data.sql` 中 `password` 列为空，**不伪造哈希值**。密码在后端以 `dev` profile 启动时由
`DevDataInitializer` 按用户名分别初始化为上表口令的 BCrypt 密文（启动日志只打印条数，不打印口令明文）。
如需手动生成密文：

```bash
cd ../backend
mvn -q compile exec:java -Dexec.mainClass=com.campus.delivery.common.util.PasswordGenerator -Dexec.args="你的口令"
```

### 已经建过库的本地环境怎么更新口令

口令升级前就建过库的同学，`sys_user.password` 里还是旧口令的密文。执行一次
`reset-demo-password.sql`（它只把这四个演示账号的密码清空），再重启后端，
`DevDataInitializer` 会自动写入新口令的密文：

```bash
mysql -u root -p campus_ai_delivery < reset-demo-password.sql
# 然后重启后端（profiles = dev,local），再用上表口令登录验证
```

不要为了改口令重跑 `schema.sql` —— 它开头是 `DROP DATABASE`，会清空整库。

## 四、表清单（36 张，按数据责任域分组）

| 责任域 | 主责 | 表 |
| --- | --- | --- |
| 用户与画像域 | 成员1（权限协助：成员4） | `sys_user`、`sys_role`、`sys_role_permission`、`student`、`diet_profile`、`user_address`、`user_favorite`、`merchant`、`rider`、`admin` |
| 商品与经营域 | 成员2 | `shop_category`、`shop`、`dish_category`、`dish`、`dish_spec`、`dish_image`、`review`、`review_image`、`review_tag` |
| 交易与配送域 | 成员4（协同：成员2） | `cart_item`、`orders`、`order_item`、`order_status_log`、`delivery_record`、`grab_log`、`payment_record` |
| AI 与运营域 | 成员3（成员1/2 提供业务数据） | `ai_config`、`ai_chat_session`、`ai_chat_message`、`knowledge_base`、`ticket`、`recommend_record`、`diet_report` |
| 日志与审计 | 成员4 | `operation_log` |
| 系统配置 | 成员3 | `sys_config`、`banner` |

## 五、关键设计说明

1. **主键**统一 `VARCHAR(32)`，与需求文档「32 位字符串主键」一致；订单号 `order_no` 对外展示，与主键分离。
2. **金额**统一 `DECIMAL(10,2)`，禁止使用浮点类型。
3. **状态**统一 `TINYINT`，取值范围写在字段注释里（如 `order_status` 0-6）。
4. **订单状态不可逆跳**：由后端 `OrderStateMachine` 校验，表上另有 `version` 乐观锁字段。
5. **库存不可为负**：`dish.stock` 扣减使用 `stock >= quantity` 条件更新，配合事务保证原子性。
6. **评价标签**拆成 `review.ai_tags`（汇总展示）与 `review_tag`（明细聚合）两处，兼顾展示性能与统计需求。
7. **AI 结果可追溯**：`review`、`recommend_record`、`diet_report` 均保存 `model_version` 与生成时间。
8. **软删除**：`sys_user`、`shop`、`dish`、`user_address` 使用 `deleted` 字段逻辑删除。
9. **索引**：订单表按「用户+状态」「店铺+状态」「骑手+状态」建立组合索引，对应三端的主要查询路径。
10. **角色是数据**：`sys_role` 存角色字典（编码/名称/启停/是否可注册/**所属端 portal**/**首页 home_path**），
    `sys_user.role` 与 `sys_role_permission.role` 引用其 `code`。新增角色只需插一行 + 在管理端勾权限码，
    不需要改 Java 枚举或前端代码；`builtin=1` 的四个内置角色供四端入口使用，不可删除。
    `portal` 决定角色能进哪套页面与接口（STUDENT/MERCHANT/RIDER/ADMIN），`home_path` 决定登录后首页；
    不配置 portal 的角色登录后只会看到「暂无工作台」提示页。

## 六、变更规则

1. 表结构只通过 `schema.sql` 变更，由数据责任域负责人提交。
2. 已建表结构变更需在 PR 描述中说明：改了哪张表、加了什么字段、是否影响他人。
3. 提交前在本地重建一次库，确认脚本可直接执行。
