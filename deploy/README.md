# TrackMeet 部署手册（deploy/）

田径运动会编排软件 TrackMeet 的容器化部署、升级、备份恢复与验收手册。
适用版本：MySQL 8.0 + Spring Boot 3（prod，容器 8080）+ Vue 3 + Nginx 1.25。

---

## 1. 架构与端口

```
                    ┌──────────────────────────── 宿主机（服务器） ────────────────────────────┐
 浏览器 / 大屏      │                                                                          │
      │             │   ┌──────────────────────────┐                                          │
      │  HTTP       │   │ frontend（nginx:1.25）    │   /            → /usr/share/nginx/html   │
      └────────────►│   │ 容器名 trackmeet-frontend │   /assets/**   → 静态资源（30 天缓存）    │
                    │   │ 容器内 :80                │   /health      → 200 ok（健康检查）       │
                    │   │ 宿主 ${WEB_PORT}=80       │   /api/**      → proxy_pass backend:8080 │
                    │   └───────────┬──────────────┘                                          │
                    │               │ Docker 网络 trackmeet-net（bridge）                     │
                    │   ┌───────────▼──────────────┐      ┌────────────────────────────────┐ │
                    │   │ backend（temurin 17 JRE） │      │ mysql（mysql:8.0）              │ │
                    │   │ 容器名 trackmeet-backend  │─────►│ 容器名 trackmeet-mysql          │ │
                    │   │ prod profile，容器内 :8080│ JDBC │ 容器内 :3306，宿主 ${MYSQL_PORT}│ │
                    │   │ /actuator/health 健康检查 │      │ 卷 trackmeet-mysql-data         │ │
                    │   └──────────────────────────┘      └────────────────────────────────┘ │
                    │   卷 trackmeet-backend-logs（/app/logs）     初始化：../sql → initdb.d    │
                    └──────────────────────────────────────────────────────────────────────────┘
```

| 服务 | 容器名 | 镜像 / 构建 | 端口（宿主 → 容器） | 健康检查 |
| --- | --- | --- | --- | --- |
| `mysql` | `trackmeet-mysql` | `mysql:8.0` | `${MYSQL_PORT:-3306}` → 3306 | `mysqladmin ping` |
| `backend` | `trackmeet-backend` | 构建自 `backend/Dockerfile` | `${BACKEND_PORT:-8080}` → 8080 | `curl`/`wget`/TCP → `/actuator/health` |
| `frontend` | `trackmeet-frontend` | 构建自 `frontend/Dockerfile` | `${WEB_BIND:-0.0.0.0}:${WEB_PORT:-80}` → 80 | `wget http://127.0.0.1/health` |
| `adminer`（可选） | `trackmeet-adminer` | `adminer:4` | `127.0.0.1:${ADMINER_PORT:-8082}` → 8080 | 依赖 mysql 健康 |

**启动顺序**（由 `depends_on: condition: service_healthy` 保证）：`mysql` 健康 → `backend` 启动 → 健康 → `frontend` 启动。

## 2. 前置条件

| 项目 | 最低要求 | 建议 |
| --- | --- | --- |
| Docker Engine | 24.0+ | 最新稳定版；Windows/macOS 用 Docker Desktop，Linux 用 docker-ce + compose 插件 |
| Docker Compose | v2（`docker compose` 子命令） | v2.20+ |
| CPU / 内存 | 2 核 / 4 GB | **4 核 / 8 GB**（后端 Maven + 前端 Vite 构建阶段较吃内存，MySQL 8 建议 ≥ 2 GB） |
| 磁盘 | 10 GB | **20 GB+**：镜像约 2–3 GB、MySQL 数据卷、`deploy/backups` 备份 |
| 网络 | 构建时需访问 Maven 中央仓库 / npm 镜像源 | 离线环境请预先 `docker save`/`docker load` 镜像 |
| 端口 | `WEB_PORT`(80)、`BACKEND_PORT`(8080)、`MYSQL_PORT`(3306) 未被占用 | Windows 上 80 常被 IIS/其他服务占用，建议 `WEB_PORT=8088` |
| 时区 | 容器内统一 `TZ=Asia/Shanghai` | 由 `.env` 的 `TZ` 控制 |

> 首次 `docker compose up -d --build` 需下载基础镜像并编译前后端，视网络约 **5–15 分钟**；
> MySQL 首次初始化（执行 `sql/01~09`）约 **30–90 秒**。

## 3. 三步启动

```bash
# 1) 准备配置（.env 与 docker-compose.yml 同目录）
cd deploy
cp .env.example .env            # Windows: Copy-Item .env.example .env
#   按需修改：WEB_PORT / BACKEND_PORT / MYSQL_PORT / MYSQL_ROOT_PASSWORD / DB_PASSWORD / JWT_SECRET

# 2) 构建并启动（首次会编译前后端）
docker compose up -d --build

# 3) 等待就绪并验收
docker compose ps               # 三个服务都应为 healthy
bash scripts/smoke-test.sh      # 或 powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

访问：

| 入口 | 地址 |
| --- | --- |
| 管理端（前端） | `http://<服务器IP>:${WEB_PORT}` |
| 后端健康检查 | `http://<服务器IP>:${BACKEND_PORT}/actuator/health` |
| 大屏（匿名） | `http://<服务器IP>:${WEB_PORT}/screen/board?meetId=1&token=<大屏令牌>` |
| Adminer（可选） | `http://127.0.0.1:${ADMINER_PORT}`，启动：`docker compose --profile tools up -d adminer` |

默认账号（口令由后端 `DefaultAccountInitializer` 首次启动时用 BCrypt 写入，SQL 中只有占位符 `INIT_PLACEHOLDER`）：

| 账号 | 默认口令 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `Admin@123` | 超级管理员 | 内置账号，首登强制改密（`force_pwd_change=1`） |
| `scheduler` | `Admin@123` | 编排员 | 演示数据（`sql/06-demo-data.sql`） |
| `referee` | `Admin@123` | 裁判 | 演示数据 |
| `leader1` | `Admin@123` | 领队 | 演示数据，绑定单位 1 |

> 生产环境请把 `.env` 的 `DEFAULT_ACCOUNT_PASSWORD` 改成强口令，或首登后立即改密并删除演示账号。

## 4. `.env` 变量说明

复制模板：`cp .env.example .env`（**只在 `.env` 中改配置，不要改 `docker-compose.yml`**）。

### 4.1 时区

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `TZ` | `Asia/Shanghai` | 三个容器及备份脚本的时区；影响日志时间、`date` 时间戳文件名 |

### 4.2 MySQL

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | 无（必填） | root 口令，仅容器内初始化与运维使用；**不要与业务账号共用** |
| `DB_NAME` | `trackmeet` | 业务库名；与 `sql/*.sql` 中 `CREATE DATABASE`/`USE` 保持一致，修改需同步改 SQL |
| `DB_USERNAME` | `trackmeet` | 业务账号，官方镜像首启自动创建并授权该库 |
| `DB_PASSWORD` | 无（必填） | 业务账号口令，后端与运维脚本使用 |
| `MYSQL_PORT` | `3306` | 宿主映射端口；本机已有 MySQL 时改 `3307` |
| `MYSQL_CONTAINER` | `trackmeet-mysql` | 运维脚本（备份/恢复/补丁）操作的容器名 |

### 4.3 后端

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `BACKEND_PORT` | `8080` | 后端宿主映射端口（直连 API 与健康检查用） |
| `SPRING_PROFILES_ACTIVE` | `prod` | 激活 profile；生产务必 `prod`（`dev` 会连 `localhost:3306`） |
| `JWT_SECRET` | 无（必填） | JWT 签名密钥，**长度 ≥ 32 字节**；生成：`openssl rand -base64 48` |
| `CORS_ORIGINS` | `http://localhost,http://127.0.0.1` | 允许的跨域来源（逗号分隔）；同源部署（Nginx 反代）保持默认即可 |
| `LOG_LEVEL` | `info` | 日志级别（`trace/debug/info/warn/error`），通过 `SPRING_APPLICATION_JSON` 覆盖 root 与 `com.trackmeet` |
| `DEFAULT_ACCOUNT_PASSWORD` | `Admin@123` | 首登默认口令；由 `trackmeet.default-password` 的 relaxed binding 生效 |

> ⚠️ **后端配合项（本次未修改 backend，需人工确认）**：`backend/src/main/resources/application-prod.yml`
> 中 `logging.level` 是**硬编码**的，没有环境变量占位符。因此本部署用 Compose 注入
> `SPRING_APPLICATION_JSON={"logging":{"level":{...}}}` 来让 `LOG_LEVEL` 生效。
> 若后续把 yml 改成 `level: ${LOG_LEVEL:info}`，可移除该环境变量（见第 11 节）。

### 4.4 前端与运维

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `WEB_PORT` | `80` | 前端访问端口；被占用时改 `8088` 等 |
| `WEB_BIND` | `0.0.0.0` | 前端监听地址；只想本机访问改 `127.0.0.1` |
| `BACKUP_DIR` | `backups` | 备份输出目录（相对 `deploy/`，可写绝对路径） |
| `BACKUP_KEEP` | `30` | 备份保留份数（按修改时间保留最新 N 份）；命令行 `--keep` 优先 |
| `ADMINER_PORT` | `8082` | Adminer 端口（仅绑定 127.0.0.1，需 `--profile tools` 启动） |

## 5. 首次初始化与增量补丁

### 5.1 全新环境（数据卷为空）

`docker compose up -d mysql` 时，官方镜像会把 `/docker-entrypoint-initdb.d` 下的脚本**按文件名字典序**执行一次：

| 顺序 | 脚本 | 内容 |
| --- | --- | --- |
| 01 | `sql/01-schema-system.sql` | 系统层 16 张表（RBAC / 菜单 / 字典 / 日志 / 参数 / 文件 / 导入 / 打印留痕） |
| 02 | `sql/02-schema-meet-athlete.sql` | 届次基础层 + 人员报名层 |
| 03 | `sql/03-schema-event-schedule.sql` | 项目规则层 + 编排层 + 赛前层 |
| 04 | `sql/04-schema-result-standing.sql` | 成绩层 + 统计输出层 |
| 05 | `sql/05-init-data.sql` | 菜单 / 权限点 / 内置角色 / 角色授权 / 字典 / 系统参数 / 内置账号（含 `screen:board`、`event:query`、`schedule:add` 等） |
| 06 | `sql/06-demo-data.sql` | 演示数据：1 届次 + 4 组别 + 4 单位 + 16 运动员 + 演示账号（**生产环境可删除该文件或导入后清理**） |
| 07 | `sql/07-patch-stage2.sql` | 增量补丁：`event:query` 权限点 + 报名表字段宽度 |
| 08 | `sql/08-patch-stage3.sql` | 增量补丁：`schedule:add` / `schedule:delete` / `schedule:field:edit` |
| 09 | `sql/09-patch-stage5.sql` | 增量补丁：`screen:board` / `screen:config:add` / `notice:edit` / `notice:delete` |

**关于 07/08/09 的幂等性（已逐条核对源码）**

| 补丁 | 语句形态 | 是否幂等 | 结论 |
| --- | --- | --- | --- |
| 07 | `INSERT ... SELECT ... WHERE NOT EXISTS`（菜单、权限点）、`INSERT IGNORE`（角色授权）、`ALTER TABLE ... MODIFY COLUMN`（同类型改写） | 是 | 重复执行不会产生重复行，也不会报错 |
| 08 | `INSERT ... SELECT ... WHERE NOT EXISTS`（菜单、权限点）、`INSERT IGNORE`（角色授权） | 是 | 同上 |
| 09 | `INSERT IGNORE INTO sys_menu ... VALUES` | 是 | 同上 |

因此 `01→09` 全量顺序执行在**全新环境**下是安全的（07/08/09 属于「05 已包含、此处再执行一遍」的冗余覆盖）。
三个补丁文件内部均自带 `USE \`trackmeet\`;`，无需额外指定库。

> 07/08/09 中**没有** `CREATE TABLE`/`DROP TABLE`，不会破坏数据；但 07 的 `ALTER TABLE registration MODIFY COLUMN`
> 会短暂锁表，业务高峰期请在维护窗口执行。

### 5.2 已有库环境（数据卷非空）

`/docker-entrypoint-initdb.d` **只会在数据卷为空时执行一次**；重启容器、重建镜像都不会再跑 SQL。
因此升级时必须手工执行新补丁：

```bash
cd deploy
bash scripts/backup.sh                       # 1) 先备份（务必）
bash scripts/apply-patches.sh --list         # 2) 查看哪些补丁已生效
bash scripts/apply-patches.sh                # 3) 依次执行 07、08、09（幂等）
docker compose restart backend               # 4) 重启后端（权限点变更后建议重启）
bash scripts/smoke-test.sh                   # 5) 冒烟验收
```

也可手工执行（不依赖脚本）：

```bash
docker exec -i trackmeet-mysql mysql -uroot -p trackmeet < ../sql/07-patch-stage2.sql
docker exec -i trackmeet-mysql mysql -uroot -p trackmeet < ../sql/08-patch-stage3.sql
docker exec -i trackmeet-mysql mysql -uroot -p trackmeet < ../sql/09-patch-stage5.sql
```

> 只有 `--list` 会去探测补丁是否已生效（探测 `sys_permission.perm_code`：07→`event:query`、08→`schedule:add`、09→`screen:board`）。
> 补丁本身可重复执行，所以「重复执行」不是错误，只是无意义。

## 6. 升级步骤

```bash
# 0) 通知停服（赛事期间请选择空闲窗口）
cd deploy

# 1) 备份（数据库 + 可选上传附件）
bash scripts/backup.sh --keep 30
#    附件（若存在 upload/ 目录）：tar -czf backups/upload_$(date +%Y%m%d_%H%M%S).tar.gz -C .. upload

# 2) 拉取新代码
git pull            # 或解压新的发布包覆盖

# 3) 重建并滚动更新（仅重建有变化的镜像）
docker compose up -d --build

# 4) 执行本次版本新增的 SQL 补丁（按发布说明的编号，例如 09）
bash scripts/apply-patches.sh --from 09

# 5) 重启后端并确认健康
docker compose restart backend
docker compose ps

# 6) 冒烟验收（必须全绿才算升级成功）
bash scripts/smoke-test.sh

# 7) 回滚（仅在升级失败时）
bash scripts/restore.sh backups/trackmeet_trackmeet_<升级前时间戳>.sql.gz
git checkout <上一个可用版本>
docker compose up -d --build
```

## 7. 冒烟验收用例（TC-SMOKE-01 … 16）

命令：`bash scripts/smoke-test.sh`（或 `scripts\smoke-test.ps1`）。
除 01/02/16 外，所有用例均通过登录 `admin` 拿到的 Token 调用，并携带 `X-Meet-Id`（取自 `/api/meets` 第一条，可用 `--meet-id` 覆盖）。

| 编号 | 接口 / 动作 | 断言 |
| --- | --- | --- |
| TC-SMOKE-01 | `GET /actuator/health`（直连后端） | HTTP 200 且响应含 `"UP"` |
| TC-SMOKE-02 | `GET /health`（直连前端 Nginx） | HTTP 200 且正文为 `ok` |
| TC-SMOKE-03 | `POST /api/auth/login`（admin） | HTTP 200 + `code=0` 且 `data.accessToken` 非空；失败时提示检查后端启动、`DefaultAccountInitializer` 初始化口令 |
| TC-SMOKE-04 | `GET /api/meets?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-05 | `GET /api/units?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-06 | `GET /api/athletes?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-07 | `GET /api/events?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-08 | `GET /api/registrations/statistics?dimension=UNIT` | HTTP 200 + `code=0` |
| TC-SMOKE-09 | `GET /api/sessions?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-10 | `GET /api/schedule?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-11 | `GET /api/results?page=1&size=10` | HTTP 200 + `code=0` |
| TC-SMOKE-12 | `GET /api/print/pdf/capability` | HTTP 200 且 `code=0`；若容器缺中文字体，接受 `code=50003`（此时前端会禁用 PDF 按钮，A4 打印仍可用） |
| TC-SMOKE-13 | `GET /api/print/reports`（不带 `X-Meet-Id`） | HTTP 200 + `code=0` |
| TC-SMOKE-14 | `GET /api/screen/config?meetId=<ID>` | HTTP 200 + `code=0` |
| TC-SMOKE-15 | `POST /api/schedule/auto`，体 `{"meetId":<ID>,"apply":false}` | HTTP 200 + `code=0`（预览不落库；无竞赛单元时 `scheduledCount=0` 属正常） |
| TC-SMOKE-16 | `POST <前端>/api/auth/login` | HTTP 200 + `code=0`，验证 Nginx `/api/` 反代整链路 |

脚本最后打印 `通过 x/总 y`；任一失败退出码非 0（默认失败即停，加 `--keep-going` 跑完）。

## 8. 备份与恢复

### 8.1 策略建议

| 项目 | 建议 |
| --- | --- |
| 频率 | 每日 1 次全量；比赛日赛前/赛后各 1 次 |
| 保留 | 30 份（约 1 个月）；可季度归档到异地 |
| 内容 | 数据库（`.sql.gz`）+ 上传附件（`upload/`，需另行打包） |
| 异地 | `rsync`/对象存储同步 `deploy/backups` |
| 校验 | 每次备份脚本自动做 gzip 完整性校验；**每季度做一次恢复演练** |

### 8.2 备份

```bash
bash scripts/backup.sh --keep 30                     # 默认输出到 deploy/backups
bash scripts/backup.sh --keep 7 --out-dir /data/bak   # 指定目录与份数
```

产出：`trackmeet_<库名>_<yyyyMMdd_HHmmss>.sql.gz`；脚本会打印压缩前后大小、`CREATE TABLE` 数量，并按份数清理旧备份。

### 8.3 恢复演练（建议每季度一次）

```bash
cd deploy
bash scripts/backup.sh                                        # 1) 现网备份
cp backups/trackmeet_trackmeet_<时间戳>.sql.gz /tmp/drill.sql.gz
bash scripts/restore.sh /tmp/drill.sql.gz                     # 2) 输入 yes；恢复前自动安全备份
docker compose restart backend                                # 3) 重启后端
bash scripts/smoke-test.sh                                    # 4) 冒烟通过即演练成功
docker exec trackmeet-mysql mysql -uroot -p -e \
  "SELECT COUNT(*) AS tables_cnt FROM information_schema.tables WHERE table_schema='trackmeet';"
```

**注意**：`restore.sh` 是覆盖式导入（同名表结构+数据被覆盖），生产请停机维护；
恢复不会自动重启后端，需手动 `docker compose restart backend`（Hikari 连接池会持有旧连接）。

## 9. 常见故障排查

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| `docker compose up` 报端口占用 | 80/8080/3306 被占用（Windows 上 80 常被 IIS 占用） | 改 `.env` 的 `WEB_PORT`/`BACKEND_PORT`/`MYSQL_PORT`；查占用：`netstat -ano \| findstr :80`（Windows）/ `ss -lntp \| grep :80`（Linux） |
| `mysql` 一直 `starting` / 健康检查不过 | 首次初始化耗时长、磁盘慢；root 口令为空；数据卷被旧版本初始化过 | `docker compose logs mysql`；确认 `.env` 的 `MYSQL_ROOT_PASSWORD` 非空；必要时 `docker compose down -v`（**会删除数据**）后重建 |
| `backend` 启动即退出 | 连不上 MySQL、`JWT_SECRET` 缺失/过短、`spring.profiles.active` 非 prod | `docker compose logs backend`；确认 `.env` 的必填项；`TRACKMEET_JWT_SECRET` ≥ 32 字节 |
| `frontend` unhealthy | nginx 配置语法错误（改过 `nginx/default.conf`） | `docker exec trackmeet-frontend nginx -t`；`docker compose logs frontend` |
| 页面 404 / 刷新后白屏 | SPA 回退失效（`try_files` 被改）或资源未构建 | 确认 `nginx/default.conf` 含 `try_files $uri $uri/ /index.html`；重新 `up -d --build frontend` |
| 接口 502 / 504 | `backend` 未就绪或崩溃重启中 | `docker compose ps` 看 backend 是否 healthy；`docker compose logs -f backend`；`proxy_read_timeout` 默认 300s，长报表导出可放宽 |
| 中文乱码（页面或数据库） | 客户端/库字符集不是 utf8mb4 | 确认 `my.cnf` 与 compose 均含 `character-set-server=utf8mb4`；`docker exec trackmeet-mysql mysql -uroot -p -e "SHOW VARIABLES LIKE 'character_set_%'"`；导入 `.sql` 用 `--default-character-set=utf8mb4` |
| 时间差 8 小时 | 容器 `TZ` 或 MySQL `default-time-zone` 不一致 | 确认 `.env` 的 `TZ=Asia/Shanghai`；`docker exec trackmeet-mysql mysql -uroot -p -e "SELECT @@global.time_zone, NOW()"` |
| PDF 中文显示为方框/空白，或 `code=50003` | 运行镜像缺中文字体 | 镜像已装 `fonts-droid-fallback`；排查：`docker exec trackmeet-backend sh -c "fc-list \| grep -i droid"`；缺字体时 `docker compose build --no-cache backend`，或在 `application.yml` 配置 `trackmeet.pdf.font-path` |
| 登录 401 | 后端未启动 / 口令未初始化 / 口令已改 | 先 `curl http://127.0.0.1:8080/actuator/health`；`docker compose logs backend \| grep 口令已初始化`；用 `.env` 的 `DEFAULT_ACCOUNT_PASSWORD` 登录，已改密则用新口令（冒烟支持 `--password`） |
| 登录 403 / 菜单按钮缺失 | 账号无权限点（`sql/05` 未执行或 `sql/07~09` 未打补丁） | `bash scripts/apply-patches.sh --list` 检查；执行后 `docker compose restart backend` 并重新登录（令牌内权限在登录时签发） |
| 大屏匿名访问 403 | 令牌错误或未生成；`meetId` 与令牌不匹配 | 登录后在「大屏配置」重新生成令牌：`PUT /api/screen/config/token`；确认 `GET /api/screen/public/board?meetId=<ID>&token=<令牌>`；注意 `/api/screen/config` 是**登录态**接口，匿名 403 属正常 |
| 大屏数据不刷新（长连接被缓冲） | 代理缓冲/缓存导致 SSE 卡住 | 确认 `/api/screen/` location 含 `proxy_buffering off; proxy_cache off;` 与 `proxy_read_timeout 3600s` |
| Excel 导入失败（413） | `client_max_body_size` 过小 | 默认 20m，与后端 `max-file-size: 20MB` 对齐；更大文件需同时改 nginx 与后端配置 |
| 备份脚本报「容器不存在或未运行」 | MySQL 容器名不符 / 未启动 | 默认 `trackmeet-mysql`；用 `--container` 或 `.env` 的 `MYSQL_CONTAINER` 指定 |
| 恢复后数据没变 | 恢复到容器内旧库、或后端连接池缓存 | 确认 `DB_NAME` 正确；恢复后 `docker compose restart backend`；必要时硬刷新浏览器 |
| `docker compose` 提示 `version` 已弃用 | Compose v2 不再需要顶层 `version` | 本文件已移除该字段；升级 Compose 到 v2.20+ |

## 10. 日志查看

```bash
docker compose logs -f --tail=200 backend      # 后端（Spring Boot）
docker compose logs -f --tail=200 frontend     # Nginx 访问/错误日志
docker compose logs -f --tail=200 mysql        # MySQL 启动与初始化日志（含首启执行 SQL 的过程）
docker compose logs --since 30m backend        # 最近 30 分钟
docker compose ps                              # 服务与健康状态一览
docker stats --no-stream                       # 资源占用

# 后端日志文件（卷 trackmeet-backend-logs → /app/logs/trackmeet.log）
docker exec trackmeet-backend tail -n 200 /app/logs/trackmeet.log

# 按链路 ID 定位一次请求（响应体里的 traceId）
docker compose logs backend | grep '<traceId>'

# 健康检查详情（响应只含 status，详细信息看日志）
curl -s http://127.0.0.1:8080/actuator/health
```

访问日志格式（`nginx/default.conf` 内 `trackmeet_main`）包含：客户端 IP、请求行、状态码、`rt`（总耗时）、`urt`（上游耗时）、`ua`（上游地址）、`xfwd`（转发链）。

## 11. 备份恢复脚本与检索建议

脚本用法、参数、退出码详见 [`scripts/README.md`](scripts/README.md)。要点：

- 口令全部来自 `.env`，脚本内不硬编码；容器内临时口令文件用完即删。
- 备份文件命名两端一致（`trackmeet_<库名>_<时间戳>.sql.gz`），bash 与 PowerShell 交叉可用。
- 恢复默认会先做一次安全备份，并要求输入 `yes` 确认（`--yes` 跳过）。

## 12. 卸载与数据清理

```bash
cd deploy

# 1) 停止并删除容器 / 网络（保留数据卷与 .env）
docker compose down

# 2) 同时删除数据卷（⚠️ 数据库与后端日志全部丢失，不可恢复）
docker compose down -v

# 3) 删除镜像（可选）
docker rmi trackmeet/backend:1.0.0 trackmeet/frontend:1.0.0
docker image prune -f

# 4) 删除宿主机上的备份与配置（⚠️ 备份删除后无法找回）
rm -rf backups .env
```

| 对象 | 名称 | 清理方式 |
| --- | --- | --- |
| 数据卷（数据库） | `trackmeet-mysql-data` | `docker volume rm trackmeet-mysql-data` |
| 数据卷（后端日志） | `trackmeet-backend-logs` | `docker volume rm trackmeet-backend-logs` |
| 网络 | `trackmeet-net` | `docker network rm trackmeet-net` |
| 备份文件 | `deploy/backups/*.sql.gz` | 手工删除或脚本保留策略自动清理 |
| 配置 | `deploy/.env` | 手工删除（模板 `.env.example` 保留） |

> **升级/重建前务必先备份**：`docker compose down -v` 会连同 `trackmeet-mysql-data` 一起删除。

---

## 13. 需要人工在有 Docker 环境验证的检查点

以下内容在**容器内**才能确认（本仓库的脚本与配置已按官方镜像文档编写，但未实机执行）：

| 编号 | 检查点 | 验证命令 / 预期 |
| --- | --- | --- |
| V-01 | 后端运行镜像是否自带 `curl` 或 `wget` | `docker exec trackmeet-backend sh -c "command -v curl; command -v wget"`；两者皆无时 healthcheck 会走 `bash /dev/tcp` 分支（仍是 200/端口判活），若均不可用需在 `backend/Dockerfile` 补装 `curl` |
| V-02 | 前端容器 healthcheck 的 `wget` 存在（busybox） | `docker exec trackmeet-frontend wget -q -O - http://127.0.0.1/health` → `ok` |
| V-03 | Compose 变量必填校验语法 | 故意删除 `.env` 中 `DB_PASSWORD`，`docker compose config` 应报「请在 deploy/.env 中设置 DB_PASSWORD」 |
| V-04 | `SPRING_APPLICATION_JSON` 是否让 `LOG_LEVEL` 生效 | 设 `LOG_LEVEL=debug` 后 `docker compose exec backend env \| grep SPRING_APPLICATION_JSON`，并在日志中确认 DEBUG 行增多 |
| V-05 | `TRACKMEET_DEFAULT_PASSWORD` 是否被后端识别 | 改 `.env` 后重建/重启 backend，首登用新口令成功即通过（该变量依赖 Spring relaxed binding） |
| V-06 | MySQL 首启是否按 01→09 顺序执行且无报错 | `docker compose logs mysql \| grep -i error`；进入容器 `mysql -uroot -p -e "USE trackmeet; SELECT COUNT(*) FROM sys_permission WHERE perm_code IN ('event:query','schedule:add','screen:board')"` → 3 |
| V-07 | 07/08/09 幂等性（重复执行） | `bash scripts/apply-patches.sh --yes` 连跑两次，第二次仍 exit 0 且 `sys_menu`/`sys_permission` 无重复行 |
| V-08 | 备份脚本全流程 | `bash scripts/backup.sh --keep 3` 生成 `.sql.gz`，`gzip -t` 通过；连续执行 4 次后目录只剩 3 份 |
| V-09 | 恢复脚本全流程 | `bash scripts/restore.sh <备份> --yes` 后表数量与备份内 `CREATE TABLE` 数一致，冒烟全绿 |
| V-10 | 临时口令文件是否被清理 | 运行备份/恢复后 `docker exec trackmeet-mysql ls -a /tmp \| grep trackmeet` → 无输出 |
| V-11 | PowerShell 端压缩/解压正确性 | 在 Windows 用 `backup.ps1` 生成 `.sql.gz`，用 `restore.sh`（Git Bash）恢复到同一库，中文数据无乱码 |
| V-12 | nginx 配置语法 | `docker exec trackmeet-frontend nginx -t` → `syntax is ok` / `test is successful` |
| V-13 | `/api/screen/` 长连接与 SSE 不被缓冲 | 打开大屏页面观察轮询/流式响应是否持续刷新；`docker exec trackmeet-frontend cat /var/log/nginx/access.log \| tail` 中 `rt` 是否随时间增长 |
| V-14 | 冒烟脚本全绿 | `bash scripts/smoke-test.sh` 输出 `通过 16/16`，退出码 0 |
| V-15 | 前端构建可复现 | `frontend/Dockerfile` 只 `COPY package.json`（未复制锁文件），若依赖漂移导致构建失败，需人工改为复制 `pnpm-lock.yaml`（**不在本次允许修改范围内**） |
| V-16 | MySQL 主从/大版本升级 | 当前固定 `mysql:8.0`；升到 8.4+ 需把 `--default-authentication-plugin` 改为 `--authentication-policy`（见 compose 注释） |
