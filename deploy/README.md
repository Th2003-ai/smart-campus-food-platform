# 部署与负载均衡（Nginx 双实例 + 四端隔离）

本目录用于演示**工程级部署形态**：Nginx 作为唯一入口，后面挂两个完全相同的
Spring Boot 实例，并用四个子域名让四个端在同一浏览器里各自登录、互不覆盖。

```
                       ┌── student.campus.local  ┐
浏览器（4 个窗口）──>  │   merchant.campus.local │  Nginx :8088
                       │   rider.campus.local    │      │
                       │   admin.campus.local    ┘      │
                                                       ▼
                                       upstream campus_backend
                                         ├── 127.0.0.1:8080
                                         └── 127.0.0.1:8081
                                                       │
                                              MySQL 8 + Redis（共用）
```

- 四个端共用**同一份** `frontend/dist` 和**同一个** upstream —— 端是权限视图，不是部署单元
- 实例数取决于并发与可用性，**与端数无关**
- 两个实例必须连同一个 MySQL、同一个 Redis（抢单锁 / Token 黑名单 / 抢单池都在 Redis）

---

## 已验证（2026-10-21 实测）

本套配置已在当前机器上跑通，以下结果均为真实命令输出：

| 验证项 | 结果 |
|---|---|
| Nginx 配置语法 | `syntax is ok` / `test is successful` |
| Nginx 进程 | 3 个（1 master + 2 worker），8088 监听 |
| 四个子域名路由 | 四个 `server_name` 均返回 HTTP 200，页面标题正确，共用同一个入口脚本 |
| 静态资源 | `/assets/index-*.js` → HTTP 200（`root` 指向 dist 生效） |
| **轮询** | 连续 6 次请求，`实际服务` 在 `127.0.0.1:8080` / `:8081` 之间交替 |
| **故障转移** | 杀掉 8081 后请求仍全部 HTTP 200；日志显示先试 `8081` 再自动重试 `8080` |
| **恢复** | 重启 8081 并等过 `fail_timeout`，轮询自动恢复 |
| 四端登录 | 四个子域名分别登录成功，role 分别为 STUDENT / MERCHANT / RIDER / ADMIN |
| **无状态验证** | 同一个学生 JWT 连续请求，在 8080 与 8081 上都返回 200（无需粘性会话） |
| 后端实例 | 8080 / 8081 各自直连 `/api/shops` 均 200 |

---

## 零、两种入口模式（先看这里）

四个端要"各登各的账号"，靠的是**不同 origin**。本目录提供两套等价入口：

| 模式 | 配置文件 | 入口地址 | 需要管理员权限 |
|---|---|---|---|
| **端口模式**（推荐先用） | `nginx-ports.conf` | `http://localhost:8088` / `:8089` / `:8090` / `:8091` | **不需要** |
| **子域名模式** | `nginx.conf` | `http://student.campus.local:8088` 等 | 需要改 hosts |

两种模式背后的 upstream、静态托管、`/api` 与 `/api/ws` 规则完全相同，只是入口形式不同。

### 切换命令

```powershell
$nginx  = 'D:\SE\nginx-1.28.3\nginx.exe'
$prefix = 'D:\SE\nginx-1.28.3'
$dir    = 'D:\SE\smart-campus-food-platform-main\deploy'

# 切到「端口模式」
& $nginx -p $prefix -c "$dir\nginx-ports.conf" -s stop
Start-Process $nginx -ArgumentList '-p', $prefix, '-c', "$dir\nginx-ports.conf" -WindowStyle Hidden

# 切到「子域名模式」
& $nginx -p $prefix -c "$dir\nginx.conf" -s stop
Start-Process $nginx -ArgumentList '-p', $prefix, '-c', "$dir\nginx.conf" -WindowStyle Hidden
```

> 切换前先停掉当前配置，否则两套配置会抢同一个 8088 端口。

### 端口模式下的四个窗口

| 窗口 | 地址 | 账号 |
|---|---|---|
| 学生 | `http://localhost:8088` | `20210001` / `Stu@Campus2026` |
| 商户 | `http://localhost:8089` | `13900000001` / `Mch@Campus2026` |
| 骑手 | `http://localhost:8090` | `13700000001` / `Rid@Campus2026` |
| 管理员 | `http://localhost:8091` | `admin` / `Adm@Campus#2026` |

> 关键：**必须用四个不同端口**。如果在同一端口开四个标签页，
> 它们共享同一份 `localStorage`，最后登录的账号会覆盖前面所有窗口。

---

## 一、环境说明（本机实际情况）

| 项目 | 值 |
|---|---|
| Nginx | `D:\SE\nginx-1.28.3\nginx.exe`（1.28.3 稳定版，便携版，未加 PATH） |
| Nginx 端口 | **8088**（本机 80 已被告禅道 httpd 占用，不能监听 80） |
| 后端实例 | 8080 / 8081 |
| 前端产物 | `frontend/dist`（`npm run build` 生成） |
| JDK | `C:\Program Files\Java\jdk-17` |

> 端口用 8088 不影响"四端隔离"：隔离依据是 **origin**（主机名 + 端口），
> 四个子域名虽然端口相同，但主机名不同，依然是四个独立 origin。

---

## 二、启动步骤

### 1. 打包后端

```powershell
cd backend
.\mvnw.cmd -o -DskipTests package
```

### 2. 构建前端

```powershell
cd frontend
npm run build
```

### 3. 启动两个后端实例

```powershell
# 先在另一个窗口启动 MySQL（服务）与 Redis
D:\SE\redis-5.0.14.1\redis-server.exe D:\SE\redis-5.0.14.1\redis.windows.conf

# 回到仓库根目录
.\deploy\start-instances.ps1
```

两个实例的日志分别在 `logs\instance-8080.log` 与 `logs\instance-8081.log`。

### 4. 启动 Nginx

```powershell
D:\SE\nginx-1.28.3\nginx.exe -p D:\SE\nginx-1.28.3 -c D:\SE\smart-campus-food-platform-main\deploy\nginx.conf
```

- 校验配置：把最后换成 `-t`
- 重载配置：把最后换成 `-s reload`
- 停止：把最后换成 `-s stop`

### 5. 配置 hosts（需要管理员权限，仅一次）

用**管理员** PowerShell：

```powershell
Add-Content C:\Windows\System32\drivers\etc\hosts @"

# campus delivery demo
127.0.0.1  student.campus.local
127.0.0.1  merchant.campus.local
127.0.0.1  rider.campus.local
127.0.0.1  admin.campus.local
"@
```

> Windows 的 hosts **不支持** `*.campus.local` 通配，必须写四行。
> 不加 hosts 也能用 `curl --resolve` 验证，但浏览器无法按域名访问。

### 6. 打开四个窗口

| 窗口 | 地址 | 账号 |
|---|---|---|
| 学生 | `http://student.campus.local:8088` | `20210001` / `Stu@Campus2026` |
| 商户 | `http://merchant.campus.local:8088` | `13900000001` / `Mch@Campus2026` |
| 骑手 | `http://rider.campus.local:8088` | `13700000001` / `Rid@Campus2026` |
| 管理员 | `http://admin.campus.local:8088` | `admin` / `Adm@Campus#2026` |

登录后会自动跳到对应端首页（`ROLE_HOME`），不需要手动输路径。

---

## 三、验证（答辩演示顺序）

### ① 轮询是否生效

```powershell
.\deploy\check-lb.ps1
```

期望看到 `upstream=127.0.0.1:8080` 与 `127.0.0.1:8081` 交替。

也可以直接看响应头：

```powershell
curl.exe -s -D - -o NUL http://student.campus.local:8088/api/shops
```

### ② 故障转移

```powershell
.\deploy\stop-instances.ps1 -Ports 8081   # 干掉一个实例
.\deploy\check-lb.ps1                     # 期望全部落到 8080，仍然 HTTP 200
.\deploy\start-instances.ps1 -Ports 8081  # 重新拉起
.\deploy\check-lb.ps1                     # 期望恢复交替
```

演示要点：**请求不会失败**，因为 Nginx 的 `proxy_next_upstream` 会把当前请求换台重试。

### ③ 四端隔离

四个窗口各登录一个账号，刷新任意窗口，登录态互不影响。
在其中一个窗口点退出，其他三个仍然在线（登出只清当前 origin 的 `localStorage`）。

### ④ 业务闭环

学生下单 → 商户「订单处理」5 秒内出现 → 接单 → 出餐完成（生成取餐码）
→ 骑手「抢单大厅」出现 → 抢单 → 取餐 → 送达 → 学生端状态变已完成。

---

## 四、常见问题

| 现象 | 原因与处理 |
|---|---|
| **`403 Invalid CORS request`** | 后端 `campus.cors.allowed-origins` 里没有当前访问的 origin。**浏览器对 POST/PUT/DELETE 一定会带 `Origin` 头，即使是同源请求**，所以经 Nginx 子域名/端口访问时后端会走 CORS 校验。已在 `application.yml` 默认值里补上 `http://*.campus.local:8088` 与端口模式四个地址，**改完必须重启应用** |
| `bind() to 0.0.0.0:80 failed (10013)` | 80 被禅道占用，本配置已改用 8088，不要改回 80 |
| `CreateFile() "...cnf/nginx.conf" failed` | 忘了 `-p D:\SE\nginx-1.28.3`，Nginx 把当前目录当成了 prefix |
| `unknown directive "﻿#"` | nginx.conf 带了 UTF-8 BOM。用编辑器另存为「UTF-8 无 BOM」，或重跑生成脚本 |
| `.ps1` 报「字符串缺少终止符」 | 脚本带了中文但没 BOM，PowerShell 按 GBK 解析。用 UTF-8 **带 BOM** 保存 |
| 502 Bad Gateway | 两个实例都没起来，或端口不是 8080/8081 |
| 页面白屏 / 404 | `frontend/dist` 没构建，或 `root` 路径不对 |
| 四个窗口登录态仍然互相覆盖 | 还在用 `localhost:5173` 访问，必须走 `*.campus.local:8088` |
| 浏览器打不开子域名 | hosts 没配（需管理员权限），可先用 `check-lb.ps1` 的 Host 头方式验证 |
| 实时通知不弹 | `location /api/ws/` 少了 `Upgrade` 头；或实例崩溃后 WS 已断，等前端 3 秒重连 |
| `X-Upstream` 只有一个值 | 另一台没启动，或刚被 `fail_timeout=10s` 摘除 |
| IDEA 启动报 `Port 8080 was already in use` | 是 `start-instances.ps1` 起的实例还在跑。先 `.\deploy\stop-instances.ps1`，或让 IDEA 用 8081 |

---

## 五、已知限制（答辩时主动说明）

| 限制 | 说明 |
|---|---|
| Nginx 本身是单点 | 真高可用需 `keepalived` + VIP 双机热备 |
| MySQL / Redis 是单点 | 应用层双活不覆盖数据层故障；Redis 挂则抢单锁与 Token 黑名单失效 |
| WebSocket 广播在进程内 | `OrderNotifyHandler` 用 `static ConcurrentHashMap` 存会话，跨实例不共享。实例崩溃时连接会断、由前端重连；跨实例的推送目前靠**商户订单页 5 秒轮询**兜底。彻底方案是 Redis Pub/Sub |
| 健康检查是被动的 | 没有 `/actuator/health`，只能靠 `max_fails`/`fail_timeout` 在失败后摘除 |
| 子域名不锁定角色 | 前端路由守卫按 `token.role` 判断，与域名无关；正常登录流程下每个窗口会停在自己端 |

---

## 六、答辩口径

> 四个端是**权限视图**，不是部署单元，所以后端实例数是 2（按可用性扩），不是 4。
> 因为后端无状态（JWT + Redis 共享锁与缓存），任意角色的请求都能落到任意实例。
> Nginx 负责统一入口、轮询分发与故障转移；四个子域名负责隔离浏览器登录态。
> 接入层与数据层的高可用（keepalived、主从复制）作为后续演进。

---

## 七、当前运行状态与停止方式

配置完成时的运行状态：

| 组件 | 状态 | 端口 |
|---|---|---|
| Nginx | 运行中（3 进程） | 8088 / 8089 / 8090 / 8091（端口模式） |
| 后端实例 A | 运行中 | 8080 |
| 后端实例 B | 运行中 | 8081 |
| Redis | 运行中 | 6379 |
| MySQL | 运行中（Windows 服务） | 3306 |

日志位置：

- Nginx：`D:\SE\nginx-1.28.3\logs\campus-access.log`、`error.log`
- 实例：`logs\instance-8080.log`、`logs\instance-8081.log`（另有 `.err`）

### 停止顺序

```powershell
# 1. 停后端实例
.\deploy\stop-instances.ps1

# 2. 停 Nginx
D:\SE\nginx-1.28.3\nginx.exe -p D:\SE\nginx-1.28.3 -c D:\SE\smart-campus-food-platform-main\deploy\nginx.conf -s stop
```

### 重新启动顺序

```powershell
# 1. Redis（若未运行）
D:\SE\redis-5.0.14.1\redis-server.exe D:\SE\redis-5.0.14.1\redis.windows.conf

# 2. 两个后端实例
.\deploy\start-instances.ps1

# 3. Nginx
D:\SE\nginx-1.28.3\nginx.exe -p D:\SE\nginx-1.28.3 -c D:\SE\smart-campus-food-platform-main\deploy\nginx.conf

# 4. 验证
.\deploy\check-lb.ps1
```

> 注意：Windows 下 `nginx.exe` 是**前台运行**，用命令行直接启动会占住终端。
> 想让它在后台跑，用 `Start-Process`（本 README 的写法），
> 或者用仓库里的方式：`Start-Process nginx -ArgumentList '-p',...,'-c',... -WindowStyle Hidden`。

> ⚠️ `start-instances.ps1` 跑的是 `backend\target` 里的 jar，**配置是打包时烘进去的**。
> 改了 `application.yml`（比如 CORS 白名单）之后，必须先 `.\mvnw.cmd package` 重新打包，
> 否则第二个实例用的还是旧配置。
