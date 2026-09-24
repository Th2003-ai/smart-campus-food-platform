# 智能 AI 校园外卖平台（campus-ai-delivery）

> 软件工程课程设计 · 第 8 组
> 面向高校校园的 PC Web 外卖业务原型系统，将大语言模型能力与校园外卖业务结合。

本仓库是项目的**工程骨架**：目录结构、模块边界、公共组件与协作约定已经就位，业务代码在各模块的
`TODO` 处按分工填充。需求依据见《第8组_智能AI校园外卖平台需求分析文档_按最初计划修订版_v3.docx》。

---

## 一、项目定位与范围

| 项目 | 内容 |
| --- | --- |
| 系统形态 | PC Web 业务原型（学生端 / 商户端 / 骑手端 / 管理员端） |
| 范围基线 | 需求文档 1.4「最初计划与实施范围」 |
| 必做（P0） | 身份与权限、店铺菜品、购物车与模拟支付、订单状态流转、AI 自然语言点餐、语义搜索、个性化推荐、AI 智能客服、评价分析、饮食分析、商户经营辅助、管理员数据看板 |
| 体验增强（P1） | WebSocket 状态推送、Redis 热点缓存、对象存储、Excel 报表导出 |
| 可选加分（P2） | 拼单、图片识别菜品、模拟骑手派单/抢单 |
| 明确不做 | 真实支付、真实骑手硬件与定位、本地大模型训练、生产级资金结算 |

---

## 二、技术栈

| 层次 | 选型 |
| --- | --- |
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios + ECharts |
| 后端 | Java 17 + Spring Boot 3 + MyBatis-Plus + RESTful API |
| 安全 | JWT Token + RBAC 角色权限 + BCrypt 密码加密 + 敏感字段脱敏 |
| 存储 | MySQL 8（业务数据）、Redis（热点缓存 / 抢单池 / 分布式锁）、对象存储（图片） |
| 实时 | WebSocket（订单状态推送、骑手订单大厅广播） |
| AI | 公有大模型 API（语义理解 / 推荐理由 / 智能客服 / 评价分析），统一封装在 `ai` 模块 |

---

## 三、目录结构

```text
campus-ai-delivery/
├── backend/                    后端 Spring Boot 工程
│   └── src/main/
│       ├── java/com/campus/delivery/
│       │   ├── common/         统一响应、异常、枚举（全组共用）
│       │   ├── config/         Web / Redis / WebSocket / 大模型配置
│       │   ├── security/       JWT、角色枚举、登录上下文、权限注解与拦截器
│       │   ├── modules/        业务模块，按模块归属到人
│       │   │   ├── user/       账号与登录、学生资料、饮食档案、地址
│       │   │   ├── shop/       店铺
│       │   │   ├── dish/       菜品与分类、库存
│       │   │   ├── cart/       购物车
│       │   │   ├── order/      订单、订单明细、状态机
│       │   │   ├── delivery/   骑手、抢单、配送（P2）
│       │   │   ├── review/     评价与评价标签
│       │   │   ├── ai/         语义理解/搜索/推荐/客服/评价分析/饮食分析
│       │   │   └── admin/      审核、看板、系统配置、知识库与工单
│       │   └── websocket/      订单与抢单消息推送
│       └── resources/
│           ├── application.yml 主配置
│           ├── application-dev.yml 开发环境配置（本地私密配置不入库）
│           ├── mapper/         MyBatis XML（复杂 SQL）
│           └── prompts/        大模型提示词模板
├── frontend/                   前端 Vue 3 工程（四端同一工程，按路由分区）
│   └── src/
│       ├── api/                接口封装（与后端模块一一对应）
│       ├── layouts/            四端布局
│       ├── router/             路由与角色守卫
│       ├── store/              Pinia 状态（登录态、购物车）
│       ├── utils/              请求、WebSocket、格式化工具
│       └── views/              student / merchant / rider / admin 四个业务分区
├── database/                   数据库脚本
│   ├── schema.sql              建表脚本
│   ├── data.sql                初始化数据
│   └── README.md               建库与执行说明
└── docs/                       工程文档
    ├── 01-项目概述与技术架构.md
    ├── 02-四人分工与模块归属.md
    ├── 03-接口规范与协作约定.md
    ├── 04-开发规范与Git协作.md
    └── architecture.html       架构与分工可视化总览
```

---

## 四、快速开始

### 1. 数据库

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p campus_ai_delivery < database/data.sql
```

### 2. Redis

```bash
redis-server          # 默认 6379
```

### 3. 后端

```bash
cd backend
mvn spring-boot:run   # 默认 http://localhost:8080/api
```

首次运行前请在 `application-dev.yml` 或本地 `application-local.yml` 中填写 MySQL、Redis 与大模型 API Key。
接口文档（Knife4j）：`http://localhost:8080/api/doc.html`

### 4. 前端

```bash
cd frontend
npm install
npm run dev           # 默认 http://localhost:5173
```

> 需要 Node 18+。开发服务器已配置代理：前端 `/api/**` 自动转发到后端 `http://localhost:8080`，
> 因此前端代码里只写相对路径，不需要硬编码后端地址。

四端入口（同一工程，按路由分区）：

| 端 | 路由前缀 |
| --- | --- |
| 学生端 | `/student` |
| 商户端 | `/merchant` |
| 骑手端 | `/rider` |
| 管理员端 | `/admin` |

---

## 五、四人分工

详细边界、协作接口与数据责任域见 [`docs/02-四人分工与模块归属.md`](docs/02-四人分工与模块归属.md)。

| 成员 | 主要负责 | 主责后端模块 | 主责前端分区 |
| --- | --- | --- | --- |
| 成员1 | 学生端：浏览、语义搜索、AI 点餐、购物车、个人中心与饮食画像 | `modules/user`（画像部分）、`cart`、`ai`（点餐/搜索/推荐） | `views/student` |
| 成员2 | 商户端：店铺菜品、库存、订单处理、评价管理、AI 经营辅助 | `modules/shop`、`dish`、`review`、`ai`（评价/经营分析） | `views/merchant` |
| 成员3 | 管理员端与 AI 客服：审核、数据看板、知识库、客服工单 | `modules/admin`、`ai`（客服/知识库） | `views/admin` |
| 成员4 | 平台基础：登录权限、订单状态机、Redis、WebSocket；可选模拟骑手 | `security`、`modules/order`、`delivery`、`websocket` | `views/rider` |

> 四人共同负责：需求评审、数据库总设计、接口联调、核心流程测试与最终文档。

---

## 六、协作约定

- 分支模型：`main`（可演示）← `dev`（集成）← `feature/<模块>-<简述>`。
- 提交信息：`feat(模块): 说明` / `fix(模块): 说明` / `docs: 说明`。
- 接口先行：先在 `docs/03-接口规范与协作约定.md` 登记接口，再各自开发；统一响应体 `Result<T>`。
- 数据库：表结构变更走 `database/schema.sql`，由数据责任域负责人提交，其他人不得直接改表。

---

## 七、当前状态

已完成（可直接运行并继续开发）：

- [x] 工程骨架与模块边界：`common` / `security` / `config` / `websocket` + 9 个业务模块
- [x] 公共组件：统一响应体 `Result<T>`、分页 `PageResult<T>`、全局异常处理、JWT 鉴权拦截器与 `@RequiresRole`、BCrypt 密码、敏感字段脱敏
- [x] 订单状态机 `OrderStateMachine`（全系统唯一改状态入口，含乐观锁与状态日志）
- [x] 数据库脚本：`schema.sql` 35 张表 + `data.sql` 演示数据（演示账号与初始密码见 `database/README.md`）
- [x] 后端业务骨架：9 个模块的 entity / mapper / service / controller 已就位，业务逻辑处标注 `TODO(成员X)`
- [x] AI 能力骨架：大模型客户端 `LlmClient` + 8 个提示词模板（`resources/prompts/*.md`，可评审、可迭代）
- [x] 前端工程骨架：Vue 3 + Vite + TypeScript + Element Plus，四端布局、路由与角色守卫、接口封装、WebSocket 客户端
- [x] 工程文档与可视化：`docs/01~04` 与 `docs/architecture.html`
- [x] 编译验证：`mvn -DskipTests compile` 通过（110 个源文件）

待完成：

- [ ] 各模块业务实现（按分工推进，见各模块 `TODO(成员X)` 标注）
- [ ] 接口联调与核心流程测试（下单、状态流转、抢单、AI 点餐、客服转工单）
- [ ] 数据看板图表、Excel 报表导出、模拟支付与退款落库
