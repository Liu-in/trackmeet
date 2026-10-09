# TrackMeet —— 田径运动会编排软件

> 一站式田径运动会业务系统：**运动会创建 → 报名 → 项目设置 → 自动编排 → 秩序册 → 检录 → 成绩录入 → 晋级 → 积分 → 公告 → 证书打印**。
> 后端 Spring Boot 3 + Java 17 + MyBatis-Plus + MySQL 8，前端 Vue 3 + TypeScript + Vite + Element Plus，Nginx + Docker Compose 一键部署。

| 项目信息 | 内容 |
| --- | --- |
| 当前阶段 | **P6 已完成**：后端单元/集成测试、前端关键页面测试、验收数据集、Docker Compose 与 Nginx 加固、备份恢复脚本、冒烟验收脚本、验收测试清单 |
| 代码规模 | 后端 533+ Java 文件（10 个业务模块 + 37 类纯算法引擎）、前端 55 文件 / 26 个页面、68 张表、203 个权限编码、测试 123+ 用例 |
| 质量门禁 | `tools/check_project.py`（后端静态一致性）、`tools/check_frontend.py`（前端页面测试）、`deploy/scripts/smoke-test.*`（运行期冒烟） |

---

## 一、技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.5、Java 17、Spring Security + JWT（JJWT 0.12）、MyBatis-Plus 3.5.7（多租户/分页/乐观锁）、EasyExcel 3.3.4、OpenPDF 1.3.39（中文 PDF） |
| 前端 | Vue 3.4、TypeScript 5.4（strict）、Vite 5、Pinia 2、Vue Router 4、Element Plus 2.7、Axios 1.7、ECharts 5 |
| 数据库 | MySQL 8.0（utf8mb4），68 张表，逻辑删除 + 乐观锁 + 多租户（`meet_id`） |
| 部署 | Docker Compose（mysql / backend / frontend）、Nginx（SPA + `/api` 反代）、mysqldump 备份恢复 |

---

## 二、阶段交付总览

| 阶段 | 交付内容 | 状态 |
| --- | --- | --- |
| P0 | 设计文档 22 篇（需求/用例/ER/表清单/API/页面/目录/计划/风险/权限矩阵/字段清单） | ✅ |
| P1 | 建库脚本 68 表、初始化数据、后端骨架（统一返回/异常/JWT/RBAC/多租户）、基础 CRUD | ✅ |
| P2 | 运动员 Excel 导入、报名管理与审核、项目/轮次/规则配置、前端工程与 4 组页面 | ✅ |
| P3 | 自动编排引擎（日程装箱/蛇形分组/道次/田赛顺序/冲突检测）、编排模块、秩序册输出、4 个页面、50 个算法单测 | ✅ |
| P4 | 成绩引擎（名次与平局/自动晋级/积分/团体总分/奖牌榜/风速/全能查表）、成绩与统计模块、6 个页面、41 个单测 | ✅ |
| P5 | 服务端 PDF（秩序册/成绩公告/检录分道/积分奖牌/证书）、统一 Excel 导出、打印模板、打印留痕、公告与大屏、6 个页面 | ✅ |
| P6 | 后端集成测试、前端关键页面测试、验收数据集、部署加固、备份恢复、冒烟脚本、验收清单 | ✅ |

---

## 三、安装

### 3.1 前置条件

| 方式 | 要求 |
| --- | --- |
| **Docker 部署（推荐）** | Docker 24+ 与 Docker Compose v2；建议 4 核 / 8 GB 内存 / 20 GB 磁盘 |
| 本地开发 | JDK 17、Maven 3.9+（或使用仓库自带 `mvnw`）、Node.js 18+ 与 pnpm 8+、MySQL 8.0 |

### 3.2 获取代码与配置

```bash
git clone <仓库地址> trackmeet && cd trackmeet
cd deploy
cp .env.example .env          # Windows: Copy-Item .env.example .env
# 编辑 .env：至少修改 MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD / JWT_SECRET
```

`.env` 关键变量（完整说明见 [`deploy/README.md`](deploy/README.md)）：

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | 数据库 root / 应用账号口令 | 必填 |
| `DB_NAME` / `DB_USERNAME` | 库名与业务账号 | `trackmeet` |
| `MYSQL_PORT` / `BACKEND_PORT` / `WEB_PORT` | 端口映射 | 3306 / 8080 / 80 |
| `JWT_SECRET` | 令牌签名密钥（≥32 字符） | 必填 |
| `CORS_ORIGINS` | 允许跨域来源 | `*` |
| `SPRING_PROFILES_ACTIVE` | 后端 profile | `prod` |
| `TZ` | 时区 | `Asia/Shanghai` |

### 3.3 中文字体（PDF 需要）

服务端 PDF 需内嵌中文字体：**Docker 镜像已内置** `DroidSansFallbackFull.ttf`，**Windows 开发机**会自动命中 `C:/Windows/Fonts/simhei.ttf`。
极简环境请挂载字体文件并在 `.env` 配置 `PDF_FONT_PATH`，或改用浏览器 A4 打印另存为 PDF。可访问 `GET /api/print/pdf/capability` 查看字体加载状态。

---

## 四、启动

### 4.0 Windows 安装包（EXE，免 Java 环境）

面向「一台 Windows 服务器 + 一个 MySQL」的交付方式，安装后为**单进程单端口**（后端同时托管前端界面）：

```powershell
# 在有构建工具链的 Windows 机器上产出安装包（JDK 17/21 + Maven + Node/pnpm + Inno Setup 6）
pwsh installer/build-installer.ps1                 # → dist\TrackMeet-Setup-P6.exe
pwsh installer/build-installer.ps1 -Portable       # → dist\TrackMeet-Portable-P6.zip（免安装绿色包）
```

双击安装包 → 向导填写 MySQL 连接与 Web 端口 → 安装程序自动建库建表导入数据、注册开机自启、放行端口、
启动服务并做健康检查 → 浏览器打开管理端。静默安装与参数、升级、卸载、常见问题见
[`installer/README.md`](installer/README.md)（含 CI 自动构建工作流 `.github/workflows/build-installer.yml`）。

> 说明：安装包必须在**具备构建工具链的环境**（本地或 CI）产出；本仓库的离线环境不含 Maven 依赖与外网，无法直接编译出 jar/dist。

### 4.1 全栈一键启动（Docker Compose）

```bash
cd deploy
docker compose up -d --build          # 首次构建约 5~10 分钟
docker compose ps                     # 三个服务应为 healthy
docker compose logs -f backend        # 观察启动日志
```

启动后访问：

| 入口 | 地址 |
| --- | --- |
| 管理端 | <http://localhost/>（`WEB_PORT` 默认 80） |
| 后端 API | <http://localhost:8080/api> |
| 健康检查 | <http://localhost:8080/actuator/health>、<http://localhost/health> |
| 大屏公告（匿名） | <http://localhost/screen/public-board?meetId=1&token=demo0000000000000000000000000001> |

> **首次初始化**：MySQL 容器在**数据卷为空**时自动按顺序执行 `sql/01…10`（建表 → 初始化 → 演示数据 → 验收数据）。
> 需要全新库时执行 `docker compose down -v` 后再 `up`（**会删除全部数据**）。
> 已有库升级请用 `deploy/scripts/apply-patches.sh`（或手工执行 `sql/07/08/09` 补丁）。

### 4.2 本地开发（前后端分离）

```bash
# 1) 数据库（可直接用 compose 只起 mysql）
cd deploy && docker compose up -d mysql

# 2) 后端（dev profile，端口 8080）
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # Windows: mvnw.cmd

# 3) 前端（Vite，端口 5173，代理 /api → localhost:8080）
cd frontend && pnpm install && pnpm dev
```

### 4.3 冒烟验收（部署后自检）

```bash
cd deploy
./scripts/smoke-test.sh                 # Linux/macOS
pwsh ./scripts/smoke-test.ps1           # Windows
```

按用例编号 `TC-SMOKE-01…` 输出通过情况；任一失败返回非 0 退出码。

### 4.4 停止与卸载

```bash
docker compose down            # 停止（保留数据）
docker compose down -v         # 停止并删除数据卷（不可恢复）
```

---

## 五、默认账号

首次启动时 `DefaultAccountInitializer` 会把初始化脚本中的口令占位符统一初始化为 **`Admin@123`**（可用 `trackmeet.default-password` 覆盖）。

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| `admin` | 超级管理员 | 全部权限（`SUPER_ADMIN` 绕过权限校验） |
| `scheduler` | 编排员 | 竞赛单元、自动编排、分组分道、冲突处理 |
| `referee` | 裁判 | 检录、成绩录入与审核 |
| `leader1` | 领队（绑定“示例市第一中学”） | 查看本单位报名/成绩/公告 |

> ⚠️ 生产环境请立即修改全部默认口令，并删除演示账号与演示数据（不要导入 `sql/06`、`sql/10`）。

---

## 六、使用流程（10 步走通全流程）

| 步骤 | 操作位置 | 说明 |
| --- | --- | --- |
| 1 | 运动会管理 | 新建届次（名称/届次/日期/地点/规程），设置报名起止时间，状态置为「报名中」 |
| 2 | 组别与单位 | 维护组别（男/女、初/高中）、参赛单位（含号码前缀、名额）、场地（道数、道次顺序） |
| 3 | 规则配置 | 名次分表（前 8 名 9-7-6-5-4-3-2-1）、破纪录加分、接力倍数、兼项间隔、名次并列策略、风速上限（三层继承：届次 → 项目 → 轮次） |
| 4 | 运动员与报名 | 下载导入模板批量导入运动员 → 报名（个人/接力）→ 限制校验（超项/超人数/组别）→ 审核通过 |
| 5 | 项目与轮次 | 建立项目（径赛/田赛/接力/全能）并生成轮次（预赛/决赛），设置晋级规则 |
| 6 | 竞赛单元与自动编排 | 建立竞赛单元（日期/时段/起止/场地）→ 点「自动编排」先**预览**再**应用** → 冲突面板处理兼项/场地冲突 → 拖拽微调并锁定 |
| 7 | 分组分道 | 对日程执行分组（蛇形/同单位避开/人数均衡）→ 分配道次（8 道 `4,5,3,6,2,7,1,8`）→ 田赛生成出场顺序 |
| 8 | 秩序册与检录 | 打印中心导出秩序册/分组表/分道表/检录表（PDF 或 Excel）；检录执行到检/未到 |
| 9 | 成绩与晋级 | 成绩录入（支持 `11"23`/`1:02.45`/田赛多次试跳）→ 标记 DNS/DNF/DSQ/NM → 录风速 → 提交审核 → 审核通过（自动生成名次）→ 生成晋级名单并确认 |
| 10 | 统计与发布 | 重算积分 → 团体总分/奖牌榜 → 生成证书 → 公告管理发布（可推大屏）→ 打印中心输出成绩公告/成绩册 |

> 演示数据已预置第 1~9 步的结果（`sql/06` + `sql/10`），登录后可直接查看各页面效果，并可用「重算积分」验证计算链路。

---

## 七、演示数据与验收

| 数据脚本 | 内容 |
| --- | --- |
| [`sql/06-demo-data.sql`](sql/06-demo-data.sql) | 1 届运动会、4 组别、4 单位、16 运动员、名次分方案、加分规则、3 个演示账号 |
| [`sql/10-demo-acceptance-data.sql`](sql/10-demo-acceptance-data.sql) | 在其上补齐完整链路：场地 3、运动员 +8、项目 12、轮次 14、报名 46、竞赛单元 3、日程 14、分组 6/成员 30/道次 30/检录 30、成绩 58（含 DNS/DSQ/超风速/破纪录）、试跳 42、风速 2、积分明细与团体总分/奖牌榜（聚合派生）、赛会纪录 2、公告 3、打印模板 2、大屏配置 1（含匿名令牌） |

验收步骤见 [`docs/20-验收测试清单.md`](docs/20-验收测试清单.md)（8 大类、60+ 条用例，含预期结果与判定方式）。

---

## 八、测试

```bash
# ① 后端单元测试（无需数据库）：算法引擎、JWT、统一返回、HTML 净化、PDF 渲染
cd backend && ./mvnw -Dtest='com.trackmeet.engine.**,com.trackmeet.security.**,com.trackmeet.common.**,com.trackmeet.modules.print.**' test

# ② 后端集成测试（需要测试库，先执行 sql/01…10 或用 compose 起 mysql）
cd backend && ./mvnw -Dtest='*IT' test
#    自定义测试库：-DTEST_DB_URL=... -DTEST_DB_USERNAME=... -DTEST_DB_PASSWORD=...

# ③ 前端关键页面测试（静态，无需依赖安装）
python tools/check_frontend.py

# ④ 后端静态一致性检查
python tools/check_project.py

# ⑤ 运行期冒烟验收（部署后）
cd deploy && ./scripts/smoke-test.sh
```

| 测试层 | 覆盖 | 用例规模 |
| --- | --- | --- |
| 单元测试 | 编排/成绩/统计/风速/全能算法，JWT，统一返回，HTML 净化，PDF 渲染与字体发现 | 130+ |
| 集成测试 | 登录鉴权、届次、运动员与报名、项目与规则、编排、成绩与晋级、积分榜单、打印、公告大屏 | 60+ |
| 前端页面测试 | 26 个页面 × 8~14 项结构/契约/权限/打印/定时器/XSS 检查 | 257 项 |
| 冒烟验收 | 健康检查、登录、核心查询、自动编排预览、PDF 能力、大屏配置 | 12~15 条 |

---

## 九、目录结构

```
trackmeet/
├─ sql/                    01~05 建表与初始化、06 演示数据、07/08/09 增量补丁、10 验收数据集
├─ backend/                Spring Boot 3
│  ├─ src/main/java/com/trackmeet/
│  │  ├─ engine/           纯算法引擎（分组/道次/田赛顺序/冲突/日程装箱/名次/晋级/积分/统计/风速/全能）
│  │  ├─ common/ config/ security/
│  │  └─ modules/          auth / system / meet / athlete / event / schedule / print(+pdf) / result / standing / announcement
│  └─ src/test/            单元测试 + 集成测试（*IT）+ 测试配置
├─ frontend/               Vue 3 + TS（api/components/layout/router/stores/styles/views）
├─ deploy/                 docker-compose、nginx、mysql 配置、部署与备份脚本、冒烟脚本、部署手册
├─ tools/                  check_project.py（后端一致性）、check_frontend.py（前端页面测试）
└─ docs/                   00~20 设计文档、联调说明、验收清单
```

---

## 十、静态一致性检查

```bash
python tools/check_project.py     # 后端：包名/导入/括号/实体列↔DDL/权限编码/路由重复/引擎调用/属性名
python tools/check_frontend.py    # 前端：26 个关键页面的结构与契约测试
```

检查内容涵盖：包路径与类型名一致、`com.trackmeet` 导入可解析、括号配平、Mapper 注解与 `@MapperScan` 覆盖、
实体 `@TableField` 与 DDL 列逐字一致、`@PreAuthorize` 权限编码均已初始化、INSERT 列名存在、
**REST 路由无重复（避免 Spring Ambiguous mapping）**、引擎静态方法调用存在、getter/setter 属性名拼写、
前端 `@/` 导入与 api 导出可解析、菜单 component ↔ 视图文件一致。

当前结果：**全部通过**（533 Java 文件、55 前端文件、68 表、203 权限编码、146 被引用编码、无重复路由、0 属性名可疑）。

---

## 十一、参考文档

| 文档 | 内容 |
| --- | --- |
| [`docs/01-需求规格说明书.md`](docs/01-需求规格说明书.md) | 功能需求、业务规则、非功能需求 |
| [`docs/05-API清单.md`](docs/05-API清单.md) | 全部 REST 接口、权限编码、请求响应示例 |
| [`docs/12-00-字段清单总览.md`](docs/12-00-字段清单总览.md) | 68 张表逐列字段清单（含约束与枚举） |
| [`docs/16`](docs/16-报名与项目模块联调说明与测试用例.md) / [`17`](docs/17-自动编排模块说明与测试用例.md) / [`18`](docs/18-成绩与统计模块说明与测试用例.md) / [`19`](docs/19-打印与导出模块说明与测试用例.md) | 各阶段算法说明、接口契约与端到端测试用例 |
| [`docs/20-验收测试清单.md`](docs/20-验收测试清单.md) | 最终验收清单（8 大类） |
| [`deploy/README.md`](deploy/README.md) | 部署手册：架构、变量、升级、备份恢复、故障排查 |

---

## 十二、已知边界

1. **服务端 PDF 依赖中文字体文件**：Docker 与常见 Windows 环境已具备；缺失时 `/api/print/pdf/*` 返回 `50003`，可改用 A4 网页打印另存为 PDF（功能等价）。
2. **模板中的 HTML 不渲染**，仅去标签作纯文本输出；公告正文在服务端经白名单净化（`HtmlSanitizer`），前端 `v-html` 前再清洗一次。
3. **`screen_config` 无 `access_token` 列**，大屏匿名令牌存于 `theme_json.accessToken`（接口字段仍为 `accessToken`）；
   如需独立列，按 [`docs/19`](docs/19-打印与导出模块说明与测试用例.md) 的三步迁移即可。
4. **设备成绩导入为占位接口**（`POST /api/results/import`），电子计时/测距对接留待后续。
5. **`@DataScope` 行级数据权限尚未实现**，当前为服务层 `meetId` 校验 + 多租户拦截器。
6. 全能计分表仅含样例区间，正式使用需导入完整计分表，否则查表得分为 0。
7. 部分早期页面（成绩公告/积分榜/奖牌榜/秩序册）自带 `@media print` 样式，未统一到 `styles/print.css`（效果一致，仅为统一性建议）。
8. `POST /api/events` 的 `rounds` 字段当前未生效（项目创建后请用 `POST /api/events/{id}/rounds` 生成轮次）；规则「恢复继承」的接口为 `POST /api/events/{id}/rule-config/reset`，`POST /api/standings/snapshot` 需先有榜单数据（否则返回 409 并提示先重算积分）。

---

## 十三、下一步（P7 建议）

1. `@DataScope` 数据权限切面、登录/操作日志页面、系统监控（Actuator + Prometheus 指标）。
2. 电子计时/测距设备对接与成绩自动校验（文件/串口/接口），成绩异议与复核流程。
3. WebSocket 大屏实时推送（替代轮询），公告大屏多屏分区与倒计时组件。
4. 号码布与参赛证打印、成绩册封面模板、证书盖章图片与二维码图形渲染。
5. 压测与容量评估、HTTPS 与域名部署、多实例与读写分离、备份恢复演练常态化。
