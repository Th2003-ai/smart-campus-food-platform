# 06-Nginx 负载均衡与团队环境同步

> 面向：本项目全体成员
> 目的：把「Nginx 负载均衡 + 四端同时登录」这套改动同步给队友，并列出每台机器需要装什么软件。
> 适用分支：`dev`

---

## 零、先看这三句话

1. **Nginx 本身不进仓库**（二进制有 6MB，且各人系统不同），队友需要自己下载解压，本仓库只提供配置文件与脚本。
2. **端口用 8088，不是 80** —— 本机 80 被禅道（httpd）占用。队友如果 80 空闲也可以改回，但为了全员一致，统一用 8088。
3. **拉代码后必须做三件事**：重启后端、重新 `npm install`/`npm run build`、可选给 hosts 加四行域名。详见第八节。

---

## 一、本次改动清单

### 1.1 新增 `deploy/` 目录（Nginx 部署配置 + 脚本）

| 文件 | 作用 | 谁需要改 |
|---|---|---|
| `deploy/nginx.conf` | **子域名模式**主配置：`upstream` 双实例 + 四个 `server_name` | 一般不用改 |
| `deploy/nginx-ports.conf` | **端口模式**主配置：8088/8089/8090/8091 各对应一个端 | 一般不用改 |
| `deploy/campus-locations.conf` | 四个端共用的 location 规则（静态资源、`/api`、`/api/ws`） | 改转发规则时改这里 |
| `deploy/start-instances.ps1` | 一键启动两个后端实例（8080/8081） | 换 JDK 路径时改参数 |
| `deploy/stop-instances.ps1` | 按端口停止实例（演示故障转移用） | — |
| `deploy/check-lb.ps1` | 验证轮询与故障转移，输出实际命中的实例 | — |
| `deploy/README.md` | 完整操作手册：步骤、验证、常见问题、答辩口径 | — |

### 1.2 两种访问模式（同一套 upstream，不同入口）

| 模式 | 配置文件 | 访问地址 | 需要管理员权限 |
|---|---|---|---|
| **端口模式**（推荐先用） | `nginx-ports.conf` | `http://localhost:8088` / `:8089` / `:8090` / `:8091` | 不需要 |
| **子域名模式** | `nginx.conf` | `http://student.campus.local:8088` 等 | 需要改 hosts |

四个端分别对应：

| 端 | 子域名 | 端口模式 |
|---|---|---|
| 学生 | `student.campus.local:8088` | `localhost:8088` |
| 商户 | `merchant.campus.local:8088` | `localhost:8089` |
| 骑手 | `rider.campus.local:8088` | `localhost:8090` |
| 管理员 | `admin.campus.local:8088` | `localhost:8091` |

> 四个地址是**四个独立 origin**（主机名或端口不同），浏览器的 `localStorage` 按 origin 隔离，
> 所以可以四个窗口各登一个账号、互不覆盖。**同一个地址开四个标签页仍然会互相覆盖。**

### 1.3 后端配置改动（必须同步，否则 403）

`backend/src/main/resources/application.yml` 的 CORS 白名单新增了子域名与端口模式地址：

```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173,http://*.campus.local:8088,http://localhost:8088,http://localhost:8089,http://localhost:8090,http://localhost:8091}
```

**为什么必须加**：浏览器对 `POST`/`PUT`/`DELETE` 请求**一定会带 `Origin` 头，即使是同源请求**，
后端会因此走 CORS 校验，`Origin` 不在白名单就直接返回 `403 Invalid CORS request`。
典型表现：登录页点"登录"弹出 `Request failed with status code 403`。

### 1.4 本次一并提交的其他后端改动（影响队友拉代码后的行为）

| 改动 | 影响 |
|---|---|
| 新增 `POST /orders/{id}/pickup` 自取确认取餐 | 修好了自取订单卡在「待取餐」的老问题；学生与商户都能点，谁先点谁生效 |
| 订单状态机允许 `待取餐 → 已完成` | 配套上一条；同时给骑手送达加了「必须先取餐」的保护 |
| 修复 `POST /reviews` 一直 401 | `/reviews` 移出公开白名单，改为「仅 GET 公开」 |
| 新增 `DELETE /merchant/dishes/{id}` | 之前菜品删除按钮返回 404 |
| 新增订单超时自动取消（Redis ZSet 延时队列） | 订单 **15 分钟未接单会被系统自动取消**；配置项 `sys_config.order.auto.cancel.minutes`，设 `0` 可关闭 |
| 抢单池/延时队列改用 `StringRedisTemplate` | Redis 里的 member 变成纯订单 ID（之前是带引号的 JSON 串） |

---

## 二、队友需要安装的软件

### 2.1 必需（不装跑不起来）

| 软件 | 版本要求 | 用途 | 下载/获取 | 验证命令 |
|---|---|---|---|---|
| **JDK** | **17**（必须，不要用 21/8） | 运行 Spring Boot 3.2.5 | [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17) 或 Oracle JDK 17 | `java -version` 显示 `17.x` |
| **Node.js** | 18 LTS 或 20 LTS | 前端构建 | [nodejs.org](https://nodejs.org/) | `node -v`、`npm -v` |
| **MySQL** | 8.0.x | 业务数据库 | [MySQL Installer](https://dev.mysql.com/downloads/installer/) | `mysql --version`，服务能启动 |
| **Redis** | 5.0 以上（Windows 可用 5.0.14.1 移植版） | 缓存、抢单池、分布式锁、延时队列 | [tporadowski/redis releases](https://github.com/tporadowski/redis/releases) | `redis-cli ping` 返回 `PONG` |
| **Git** | 任意较新版本 | 拉代码 | [git-scm.com](https://git-scm.com/) | `git --version` |

> **Maven 不用装**：仓库自带 `backend/mvnw.cmd`（Windows）与 `mvnw`（macOS/Linux），
> 首次运行会自动下载 Maven 与依赖（需联网）。

### 2.2 需要做 Nginx 负载均衡/四端同登时才装

| 软件 | 版本 | 说明 | 下载 |
|---|---|---|---|
| **Nginx** | **1.28.3 stable（Windows 版）** | 负载均衡、四端入口 | [nginx.org/download](https://nginx.org/download/nginx-1.28.3.zip) |

解压到任意目录即可（本文以 `D:\SE\nginx-1.28.3` 为例，**不用装成 Windows 服务，也不用加 PATH**）：

```powershell
# 下载并解压
Invoke-WebRequest https://nginx.org/download/nginx-1.28.3.zip -OutFile $env:TEMP\nginx.zip
Expand-Archive $env:TEMP\nginx.zip -DestinationPath D:\SE
# 验证
D:\SE\nginx-1.28.3\nginx.exe -v      # 输出 nginx version: nginx/1.28.3
```

> macOS / Linux 的同学：`brew install nginx` 或 `apt install nginx` 即可，
> 把配置里的 Windows 路径（`D:/SE/...`）改成你们自己的路径。

### 2.3 不需要装的东西（避免重复劳动）

| 东西 | 说明 |
|---|---|
| Maven | 用仓库的 `mvnw` |
| Redis GUI | 命令行 `redis-cli` 足够；想可视化可选 RedisInsight / Another Redis Desktop Manager（非必需） |
| 任何浏览器插件 | 四端隔离靠 origin，不需要插件 |
| Knife4j / Swagger | 后端启动后直接访问 `http://localhost:8080/api/doc.html` |

### 2.4 IDE 插件（可选，提升体验）

| IDE | 插件 | 用途 |
|---|---|---|
| IntelliJ IDEA | **Lombok** | 项目大量用 `@Data`/`@Slf4j`，不装会满屏红 |
| IntelliJ IDEA | MyBatisX（可选） | Mapper 与 XML 跳转 |
| VS Code | **Vue - Official**（原 Volar） | `.vue` 语法支持 |
| VS Code | ESLint + Prettier | 与仓库配置对齐 |

---

## 三、一次性环境准备（按顺序）

### 步骤 1：数据库

```sql
-- 用 root 或自己的账号执行
SOURCE database/schema.sql;   -- 建库建表
SOURCE database/data.sql;     -- 演示数据（4 个账号 + 1 店 4 菜）
```

改自己的数据库口令：编辑 `backend/src/main/resources/application-local.yml`
（该文件在 `.gitignore` 中，每人一份，**不要提交**）。

### 步骤 2：Redis

```powershell
# Windows
D:\SE\redis-5.0.14.1\redis-server.exe D:\SE\redis-5.0.14.1\redis.windows.conf
# macOS / Linux
redis-server
```

验证：`redis-cli ping` → `PONG`

### 步骤 3：后端

```powershell
cd backend
.\mvnw.cmd -o -DskipTests package      # 首次可能要去掉 -o 联网拉依赖
java -jar target\campus-ai-delivery-backend-0.1.0.jar
```

或用 IDEA 直接运行 `CampusDeliveryApplication`（默认 8080）。

验证：`http://localhost:8080/api/doc.html` 能打开。

### 步骤 4：前端

```powershell
cd frontend
npm install
npm run build          # 生成 dist，给 Nginx 用
# 或者开发模式
npm run dev            # http://localhost:5173
```

### 步骤 5：Nginx（可选）

```powershell
# 端口模式（不需要 hosts，推荐先用）
D:\SE\nginx-1.28.3\nginx.exe -p D:\SE\nginx-1.28.3 `
  -c D:\SE\smart-campus-food-platform-main\deploy\nginx-ports.conf

# 子域名模式（需要先配 hosts）
D:\SE\nginx-1.28.3\nginx.exe -p D:\SE\nginx-1.28.3 `
  -c D:\SE\smart-campus-food-platform-main\deploy\nginx.conf
```

> ⚠️ Windows 下 `nginx.exe` 是**前台运行**，会占住终端。
> 想后台跑用 `Start-Process ... -WindowStyle Hidden`。
> `-p` 不能省，否则 Nginx 会把当前目录当 prefix，报找不到 `conf/nginx.conf`。

### 步骤 6：hosts（仅子域名模式需要）

**管理员** PowerShell：

```powershell
Add-Content C:\Windows\System32\drivers\etc\hosts @"

# campus-delivery demo
127.0.0.1  student.campus.local
127.0.0.1  merchant.campus.local
127.0.0.1  rider.campus.local
127.0.0.1  admin.campus.local
"@
```

> Windows hosts **不支持** `*.campus.local` 通配，必须四行。

### 步骤 7：双实例（仅演示负载均衡时）

```powershell
.\deploy\start-instances.ps1        # 8080 + 8081
.\deploy\check-lb.ps1               # 看请求是否在 8080/8081 之间交替
.\deploy\stop-instances.ps1         # 停止
```

> 如果 IDEA 已经占了 8080，脚本会自动跳过，只启动 8081 —— 正好组成双实例。

---

## 四、验证清单

| 验证项 | 命令 | 期望 |
|---|---|---|
| Nginx 配置语法 | `nginx -p <prefix> -c <conf> -t` | `syntax is ok` / `test is successful` |
| 端口监听 | `Get-NetTCPConnection -LocalPort 8088 -State Listen` | 有结果 |
| 四端页面 | 浏览器打开四个地址 | 都能进入登录页 |
| 四端登录 | 四个窗口各登一个账号 | 互不覆盖，刷新不串 |
| 负载均衡 | `.\deploy\check-lb.ps1` | 命中 8080 与 8081 交替 |
| 故障转移 | `.\deploy\stop-instances.ps1 -Ports 8081` 后再检查 | 仍 HTTP 200，全部落到 8080 |
| WebSocket | 学生下单后商户端订单页 | 5 秒内出现新单 |

---

## 五、常见问题（都是实际踩过的）

| 现象 | 原因 | 解决 |
|---|---|---|
| `bind() to 0.0.0.0:80 failed (10013)` | 80 被其他程序占用（本机是禅道） | 用 8088，不要改回 80 |
| `CreateFile() "...cnf/nginx.conf" failed` | 启动时没带 `-p` | 加上 `-p <nginx目录>` |
| `unknown directive "﻿#"` | `nginx.conf` 带 UTF-8 **BOM** | 另存为「UTF-8 无 BOM」；**不要用 PowerShell `Set-Content` 改这个文件** |
| `.ps1` 报「字符串缺少终止符」 | 脚本含中文但没有 BOM，PowerShell 按 GBK 解析 | 保存为「UTF-8 **带 BOM**」 |
| 登录弹 `403 Invalid CORS request` | 后端 CORS 白名单没有当前 origin | 确认 `application.yml` 已更新，并**重启后端** |
| `502 Bad Gateway` | 后端没启动，或端口不是 8080/8081 | 启动后端 / 改用 `start-instances.ps1` |
| `Port 8080 was already in use` | `start-instances.ps1` 起的实例还在跑 | `.\deploy\stop-instances.ps1` |
| 页面白屏/404 | `frontend/dist` 没构建，或 `root` 路径不对 | `npm run build`；检查 `root` 指向 |
| 改了界面但页面没变 | 浏览器缓存了旧 bundle | `Ctrl + F5` 强制刷新 |
| 四个窗口登录态还是互相覆盖 | 用了同一个地址 | 必须四个不同子域名或四个不同端口 |

---

## 六、架构小结（答辩可用）

```
                        ┌── student.campus.local  ┐
浏览器（4 个窗口）──>   │   merchant.campus.local │  Nginx :8088
                        │   rider.campus.local    │      │
                        │   admin.campus.local    ┘      │
                                                        ▼
                                          upstream campus_backend
                                            ├── 127.0.0.1:8080
                                            └── 127.0.0.1:8081
                                                        │
                                               MySQL 8 + Redis（共用）
```

要点：

- **四个端共用同一份 `dist` 与同一个 upstream** —— 端是权限视图，不是部署单元
- 后端**无状态**（JWT + Redis 共享锁/缓存），所以任意请求可落到任意实例，不需要粘性会话
- 实例数取决于并发与可用性，**与端数无关**（本项目用 2 台）
- 已知限制：Nginx 与 MySQL/Redis 仍是单点；WebSocket 广播在进程内，跨实例推送靠商户订单页 5 秒轮询兜底

---

## 七、Git 提交建议

### 7.1 建议提交的内容

```powershell
git add deploy/
git add "docs/06-Nginx负载均衡与团队环境同步.md"
git add backend/src/main/java/com/campus/delivery/modules/admin/controller/AdminOrderOpsController.java
git add backend/src/main/java/com/campus/delivery/modules/order/service/OrderTimeoutQueue.java
git add backend/src/main/java/com/campus/delivery/modules/order/task/
git add backend/src/main/java/com/campus/delivery/common/constant/RedisKeys.java
git add backend/src/main/java/com/campus/delivery/common/enums/OrderStatus.java
git add backend/src/main/java/com/campus/delivery/config/WebMvcConfig.java
git add backend/src/main/java/com/campus/delivery/modules/delivery/service/DeliveryService.java
git add backend/src/main/java/com/campus/delivery/modules/dish/
git add backend/src/main/java/com/campus/delivery/modules/order/
git add backend/src/main/java/com/campus/delivery/security/AuthInterceptor.java
git add backend/src/main/resources/application.yml
git add frontend/src/api/order.ts frontend/src/utils/dict.ts
git add frontend/src/views/merchant/Orders.vue frontend/src/views/student/OrderDetail.vue
```

### 7.2 注意不要提交

| 文件 | 原因 |
|---|---|
| `README.zip` | 个人打包物，与项目无关 |
| `backend/src/main/resources/application-local.yml` | 含本机数据库口令（已在 `.gitignore`） |
| `D:\SE\nginx-1.28.3\` | Nginx 二进制，不入库 |
| `logs/`、`frontend/dist/`、`node_modules/` | 构建产物与日志 |
| `docs/make_ppt.py`、`docs/*.pptx` | 个人 PPT 材料，按需决定是否提交 |

### 7.3 建议的 commit message

```
feat(deploy): 新增 Nginx 双实例负载均衡与四端 origin 隔离

- deploy/: nginx 配置（子域名/端口两种模式）、双实例启停与负载均衡验证脚本
- 后端 CORS 白名单支持 *.campus.local:8088 与端口模式，修复 403 Invalid CORS request
- 新增 POST /orders/{id}/pickup：自取订单由学生或商户确认取餐，修复卡在「待取餐」
- 修复 /reviews 在公开白名单导致 POST 401
- 新增 DELETE /merchant/dishes/{id}：修复菜品删除 404
- 新增订单超时未接单自动取消（Redis ZSet 延时队列 + 定时扫描）
- 抢单池与延时队列改用 StringRedisTemplate，Redis 中 member 为纯订单 ID
```

> 如果团队习惯小步提交，可拆成 3 个 commit：
> `feat(deploy): nginx 负载均衡与四端隔离` / `fix(order): 自取取餐闭环与评价 401` / `feat(redis): 订单超时延时队列`

---

## 八、队友拉取代码后必须做的三件事

1. **重启后端**：CORS 白名单、自取取餐接口、菜品删除接口、超时队列都是新代码，不重启不生效
2. **前端**：`npm install`（依赖版本未变，但确保本地依赖完整）+ `npm run build`（如果走 Nginx）
3. **hosts**（仅子域名模式）：把自己的四个域名加进去；不想改 hosts 就直接用端口模式

---

## 九、参考资料

| 内容 | 位置 |
|---|---|
| Nginx 完整操作手册（步骤/验证/故障处理） | `deploy/README.md` |
| 换机部署与配置项说明 | `docs/05-换机部署与配置.md` |
| 前端页面完成度与接口对照 | `docs/前端补全清单.md` |
| 接口规范 | `docs/03-接口规范与协作约定.md` |
| 分工与模块归属 | `docs/02-四人分工与模块归属.md` |

---

## 十、接口限流与下单幂等（本次一并新增）

### 10.1 接口限流

**做什么**：限制同一调用方在时间窗口内的请求次数，超限返回业务码 `429`。

**实现**：`@RateLimit` 注解 + `RateLimitInterceptor`（Redis 固定窗口计数，Lua 保证 `INCR`+`EXPIRE` 原子）。
key 里带窗口序号（`rate:{name}:{subject}:{当前秒/窗口秒}`），每个窗口独立 key，天然过期，
不会出现"key 永不过期导致永久限流"的问题。Redis 异常时**放行**（fail-open），优先保业务可用。

**已接入的接口**：

| 接口 | 维度 | 阈值 | 目的 |
|---|---|---|---|
| `POST /auth/login` | IP | 5 次 / 60 秒 | 防密码爆破 |
| `POST /orders` | 用户 | 10 次 / 60 秒 | 防刷单 |
| `POST /reviews` | 用户 | 5 次 / 60 秒 | 防刷评价 |
| `POST /ai/order`、`POST /ai/chat` | 用户 | 20 次 / 300 秒 | 控制大模型调用成本 |
| `POST /rider/grab/{orderId}` | 用户 | 30 次 / 60 秒 | 防脚本抢单 |

超限响应示例：

```json
{ "code": 429, "message": "登录尝试过于频繁（60 秒内最多 5 次）" }
```

**怎么验证**：连续调登录 6 次，第 6 次返回 `429`；Redis 里能看到 `rate:auth:login:ip:127.0.0.1:<窗口序号>`。

**怎么临时关闭**：把注解的 `enabled` 改 `false`；或清掉限流计数：

```powershell
redis-cli KEYS "rate:*" | ForEach-Object { redis-cli DEL $_ }
```

### 10.2 下单幂等

**做什么**：同一笔下单重复提交（双击、超时重试、刷新重发）只产生**一条订单**。

**三层防护**：

| 层 | 机制 | 作用 |
|---|---|---|
| 1 | Redis `SET idempotent:order:{userId}:{requestId} NX EX 300` | 快速拦截；值已是订单ID时**直接返回原订单** |
| 2 | 数据库唯一索引 `uk_user_request (user_id, request_id)` | Redis 失效/并发穿透时的最终兜底 |
| 3 | 订单状态机的前置校验 | 取消、取餐等后续操作天然幂等 |

**前端**：进入结算页时生成一个 UUID，作为请求头 `X-Request-Id` 提交；下单成功后作废。

```
前端                                  后端
openCheckout() → requestId = UUID()
POST /orders  X-Request-Id: xxx  →    SET NX → 创建订单 → 记录 orderId
再次 POST 同一 xxx                →    命中 → 返回同一张订单（不会重复创建）
```

**返回行为**：

| 情况 | 结果 |
|---|---|
| 首次提交 | `200`，创建订单 |
| 重复提交（已成功） | `200`，返回**同一张订单**（订单号相同） |
| 重复提交（上一请求还在处理） | `409`「请求正在处理中，请勿重复提交」 |
| Redis 键丢失后重复提交 | `409`「该订单已提交，请勿重复提交」（数据库唯一索引拦截） |
| 不同 requestId | 正常创建新订单 |

**⚠️ 队友必做：数据库增量升级**

老库需要执行一次（新库直接用 `schema.sql` 即可，已包含）：

```sql
SOURCE database/upgrade-2026-10-21-order-idempotent.sql;
```

它会给 `orders` 加 `request_id` 列和 `uk_user_request` 唯一索引。
不执行会报 `Unknown column 'request_id'`。

### 10.3 本次新增的文件

| 文件 | 说明 |
|---|---|
| `common/annotation/RateLimit.java` | 限流注解 |
| `common/annotation/LimitBy.java` | 计数维度枚举（USER / IP） |
| `security/RateLimitInterceptor.java` | 限流拦截器（Lua + Redis） |
| `database/upgrade-2026-10-21-order-idempotent.sql` | 幂等所需的数据库增量脚本 |

修改的文件：`WebMvcConfig`（注册拦截器 + 顺序）、`AuthInterceptor` 无改动、`OrderService`（幂等包装）、
`OrderController`（读 `X-Request-Id`）、`Order`（`requestId` 字段）、若干 Controller 加 `@RateLimit`、
`frontend/src/api/order.ts` 与 `student/Cart.vue`（生成并发送幂等键）、`ResultCode`（429/409）。

---

## 十一、余额支付与退款（本次新增）

### 11.1 之前的问题

`student.balance` 字段一直存在但**从未被读写**，`payment_record` 表从未写入，
下单只是把 `pay_status` 直接置成"已支付"。也就是说 PPT 里"金额/余额只落磁盘库 + 事务"这条
在代码上并不成立。

### 11.2 现在的实现

新增 `modules/payment`：

| 文件 | 作用 |
|---|---|
| `payment/entity/PaymentRecord.java` | `payment_record` 表映射（一单一条，`uk_order_id`） |
| `payment/mapper/PaymentRecordMapper.java` | 数据访问 |
| `payment/service/PaymentService.java` | `charge()` 收款、`refund()` 退款 |

关键设计（都是"金额只落库、不进 Redis"）：

| 关注点 | 做法 |
|---|---|
| 扣款 | `UPDATE student SET balance = balance - ? WHERE user_id = ? AND balance >= ?`，影响行数 0 → 余额不足 |
| 退款闸门 | `UPDATE orders SET pay_status = 2 WHERE id = ? AND pay_status = 1`，只有抢到这次更新才退钱 |
| 幂等 | 重复退款时闸门返回 0 行，**不会重复加钱** |
| 流水 | 支付写 `payment_record(status=1)`，退款改 `status=3` |

**退款触发点**（三处都已接上）：学生取消、商户拒单、超时未接单自动取消。

### 11.3 支付方式

| payType | 行为 |
|---|---|
| **1 余额** | 扣减 `student.balance`，不足则整单回滚并提示「余额不足：本单需 ¥xx，请先充值或改用模拟第三方支付」 |
| **2 模拟第三方** | 不动余额，仅写流水；退款时也不退余额 |

前端结算弹窗已加「支付方式」单选，并显示当前余额（不足时红字提示）。

### 11.4 ⚠️ 队友必做：给自己的演示学生充值

`data.sql` 已给演示学生加了 `balance = 200.00`，但**已有的库不会自动更新**，
余额为 0 时用余额支付会报"余额不足"。执行一次：

```sql
UPDATE campus_ai_delivery.student SET balance = 200.00 WHERE student_no = '20210001';
```

或者在下单时直接选「模拟第三方」支付。

### 11.5 验证结果（真实请求）

| 场景 | 结果 |
|---|---|
| 初始余额 | `200.00` |
| 余额支付 14.50 | 下单成功，余额 → `185.50` |
| 学生取消 | 余额回到 `200.00`，`pay_status=2`，流水 `status=3` |
| 模拟第三方 14.50 | 余额保持 `200.00`；商户拒单后余额仍 `200.00`，`pay_status=2` |
| 余额仅剩 1.00 时下单 | `code=30050 余额不足：本单需 ¥14.50…`，余额不变，**购物车保留** |
| `payment_record` | 两条流水，`trade_no` 分别为 `BAL…` / `MOCK…` |

---

## 十二、队友零配置入口（8089 / 8090 / 8091）

### 12.1 为什么需要

`*.campus.local` 是 hosts 假域名，队友要访问就得改 hosts 指向你的 IP。为了让他们**什么都不用配**，
在原来四个子域名之外，给三个端各加了一个**独立端口**：

| 端 | 域名入口（你自己用） | IP+端口入口（队友零配置） |
|---|---|---|
| 学生 | `student.campus.local:8088` | `http://<你的IP>:8088` |
| 商户 | `merchant.campus.local:8088` | `http://<你的IP>:8089` |
| 骑手 | `rider.campus.local:8088` | `http://<你的IP>:8090` |
| 管理员 | `admin.campus.local:8088` | `http://<你的IP>:8091` |

> 8088 上如果 Host 没匹配到任何 `server_name`（比如直接输 IP），会落到第一个 server 块（学生端）。

配置只多了三行 `listen`（见 `deploy/nginx.conf` 的 merchant / rider / admin 块）。

### 12.2 配套的两处改动（必须一起做，否则 403）

| 问题 | 改动 |
|---|---|
| 队友 origin 变成 `http://IP:端口`，不在 CORS 白名单 → `403 Invalid CORS request` | ① `application.yml` 白名单加 `http://10.174.130.230:[*]`、`http://26.171.182.93:[*]`（`:[*]` 通配端口） |
| 同上 | ② `campus-locations.conf` 的 `/api/` 里 `proxy_set_header Origin "";`，同源反代直接剥掉 Origin |

两处是**双保险**：① 保证直连后端也放行；② 保证走 Nginx 时根本不触发 CORS 校验。

### 12.3 验证结果（真实请求）

| 验证 | 结果 |
|---|---|
| 直连 8081，`Origin: http://10.174.130.230:8089` | `200` ✅（白名单通配生效） |
| 直连 8081，`Origin: http://evil.example.com:8089` | `403` ✅（白名单仍有意义） |
| 经 Nginx 8089，任意 Origin | `200` ✅（Origin 已剥掉） |
| 四个端口页面 | 8088/8089/8090/8091 全部 `200` |
| 8089 入口登录 | `code=200`，返回 token |

### 12.4 ⚠️ 队友能访问的前置条件

1. **同一虚拟网络**：本机 `campus.local` 这套走的是 ZeroTier / Radmin，队友必须加入同一网络
2. **防火墙放行**（管理员执行，当前机器缺这条规则）：

```powershell
New-NetFirewallRule -DisplayName "Campus Nginx 8088-8091" -Direction Inbound `
  -LocalPort 8088,8089,8090,8091 -Protocol TCP -Action Allow
```

3. **虚拟网卡处于可用状态**：`Get-NetIPAddress -IPAddress <你的IP>` 的 `AddressState` 要是 `Preferred`；
   如果是 `Tentative`，队友 ping 不通（VPN 未完成连接）
4. 8080/8081 **不需要**对外放行，Nginx 在本机代理

### 12.5 Nginx 重载注意事项

用 `Start-Process` 启动时如果 pid 文件为空，`nginx -s reload` 会报
`invalid PID number "" in logs/nginx.pid`。此时直接重启进程即可：

```powershell
Get-Process nginx | Stop-Process -Force      # 可能因权限不足失败，需管理员
Start-Process 'D:\SE\nginx-1.28.3\nginx.exe' -ArgumentList '-p','D:\SE\nginx-1.28.3','-c','D:\SE\smart-campus-food-platform-main\deploy\nginx.conf' -WindowStyle Hidden
```



