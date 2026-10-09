# TrackMeet Windows 安装包（EXE）构建与安装说明

本目录提供把本项目打包为 **Windows 可安装 EXE** 的完整方案：

```
installer/
├─ TrackMeet.iss            Inno Setup 6 安装脚本（界面、数据库配置向导、快捷方式、卸载）
├─ build-installer.ps1      一键构建：后端 jar → 前端 dist → jlink 运行时 → stage → 编译 EXE
├─ scripts/
│  ├─ configure.ps1         安装期：写配置 + 自动建库初始化 + 注册自启 + 防火墙 + 健康检查
│  ├─ start-backend.cmd     启动（读 config\application-prod.yml，日志写入 logs\）
│  ├─ stop-backend.cmd      停止（仅结束 app.jar 的 java 进程）
│  └─ uninstall.ps1         卸载清理（停服务 / 删计划任务 / 删防火墙规则，保数据）
└─ README.md                本文件
```

产物（`dist/`）：
- `TrackMeet-Setup-<版本>.exe` —— 安装包（含精简 JRE、后端 jar、前端资源、数据库脚本、脚本与文档）
- `TrackMeet-Setup-<版本>.exe.sha256` —— 校验文件
- `TrackMeet-Portable-<版本>.zip` —— 免安装绿色包（`-Portable` 时生成）

---

## 一、安装包包含什么

| 目录（安装后位于 `C:\Program Files\TrackMeet`） | 内容 |
| --- | --- |
| `backend\app.jar` | 后端可执行 jar（Spring Boot，内嵌 Tomcat） |
| `frontend\` | 前端构建产物（由后端以单进程模式托管，**无需 Nginx**） |
| `runtime\` | `jlink` 生成的精简 JRE（约 60~90 MB），目标机**无需安装 Java** |
| `sql\` | 建表与初始化脚本 `01…10`（安装时由后端自动执行） |
| `scripts\` | 配置/启停/卸载脚本 |
| `config\application-prod.yml` | 安装向导生成的配置（数据源 / 端口 / 密钥 / 初始口令） |
| `logs\`、`backup\` | 运行日志与备份目录 |

安装后：

| 功能 | 说明 |
| --- | --- |
| 访问地址 | `http://localhost:<Web 端口>/`（默认 8080） |
| 单进程 | 后端同时提供 REST API 与前端界面，**只需放行一个端口** |
| 数据库 | 连接已有的 MySQL 8；安装向导自动建库、建表、导入初始与演示数据（可重复执行、有执行记录表） |
| 开机自启 | 通过 Windows 计划任务 `TrackMeet-Backend`（无需第三方服务包装器） |
| 默认账号 | `admin` / 安装时设置的管理员口令（默认 `Admin@123`） |

---

## 二、构建前置条件

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17 或 21**（需含 `javac`、`jlink`） | 建议 Eclipse Temurin；JDK 27 等超前版本未验证（Lombok 兼容性风险） |
| Maven | 3.9+ | 可用 `backend\mvnw.cmd`（若已提供）或 PATH 中的 `mvn` |
| Node.js + pnpm | Node 18+ / **pnpm 10**（与 `frontend/pnpm-lock.yaml` 的 `lockfileVersion 9.0` 匹配，pnpm 8 无法读取） | 构建前端静态资源；`package.json` 已声明 `packageManager: pnpm@10.32.1` |
| Inno Setup | 6.x | <https://jrsoftware.org/isdl.php>（仅编译 EXE 需要） |
| MySQL | 8.0 | 目标机需可访问（本机或远程），安装时填入连接信息 |

---

## 三、构建（一条命令）

```powershell
pwsh installer/build-installer.ps1                 # 完整构建 → dist\TrackMeet-Setup-P6.exe
pwsh installer/build-installer.ps1 -Version P6.1   # 指定版本号
pwsh installer/build-installer.ps1 -Portable       # 免安装绿色包（无需 Inno Setup）
pwsh installer/build-installer.ps1 -StageOnly      # 只组装 installer\stage（再用 ISCC 手工编译）
pwsh installer/build-installer.ps1 -SkipFrontend   # 复用已有 frontend\dist（前端已构建过）
pwsh installer/build-installer.ps1 -JdkHome "C:\Program Files\Eclipse Adoptium\jdk-17.0.11+9"
```

构建过程：`mvn -DskipTests clean package` → `pnpm install --frozen-lockfile && pnpm build`
→ `jlink --add-modules ... --strip-debug --no-header-pages --compress=2` → 组装 `stage\` → `ISCC TrackMeet.iss`。

---

## 四、安装

### 4.1 图形界面安装

双击 `TrackMeet-Setup-<版本>.exe`，向导流程：

1. 选择安装目录（默认 `C:\Program Files\TrackMeet`）、附加任务（桌面图标 / 开机自启 / 防火墙放行）；
2. **数据库配置**：MySQL 主机、端口、账号、口令；
3. **应用配置**：Web 端口、管理员初始口令（≥6 位）；
4. 安装程序自动完成：写配置 → 建库建表导入数据 → 注册自启 → 放行端口 → 启动服务 → 健康检查；
5. 结束后浏览器打开管理端；若健康检查未通过，日志位于 `安装目录\logs\`。

### 4.2 静默安装（批量部署）

```powershell
.\TrackMeet-Setup-P6.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART `
    /DIR="D:\TrackMeet" /DBHOST=127.0.0.1 /DBPORT=3306 /DBUSER=root /DBPASSWORD=*** `
    /WEBPORT=8080 /ADMINPWD=Admin@123 /INITDB=1 /AUTOSTART=1 /FIREWALL=1
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `/DIR` | `C:\Program Files\TrackMeet` | 安装目录 |
| `/DBHOST` `/DBPORT` `/DBUSER` `/DBPASSWORD` | `127.0.0.1` `3306` `root` 空 | MySQL 连接信息 |
| `/WEBPORT` | `8080` | Web 端口（同时用于防火墙规则） |
| `/ADMINPWD` | `Admin@123` | admin 初始口令 |
| `/INITDB` | `1` | 是否自动建库导入（`0` 表示已有库，跳过） |
| `/AUTOSTART` | `1` | 是否注册开机自启 |
| `/FIREWALL` | `1` | 是否添加入站规则 |

### 4.3 免安装绿色包

解压 `TrackMeet-Portable-<版本>.zip` → 编辑 `config\`（或用 `scripts\configure.ps1` 生成）
→ 双击 `scripts\start-backend.cmd` → 访问 `http://localhost:8080/`。

---

## 五、升级与卸载

**升级**：直接运行新版安装包覆盖安装（`/INITDB=0` 跳过建库）；配置与数据保留，服务会重启。
**卸载**：控制面板 → 程序和功能 → TrackMeet，或运行安装目录下的 `unins000.exe`。
卸载会自动停服务、删计划任务、删防火墙规则；**数据库不会删除**，如需清理：

```sql
DROP DATABASE IF EXISTS trackmeet;
```

---

## 六、常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 安装后打开页面 502/无法访问 | 服务未启动或端口被占用：查看 `logs\backend.out.log`；换 `/WEBPORT` 重装或用 `netstat -ano \| findstr :8080` 排查占用 |
| 健康检查失败、日志提示数据库连接 | MySQL 未启动 / 账号口令错误 / 远程库未放行；修改 `config\application-prod.yml` 后执行 `scripts\stop-backend.cmd` + `scripts\start-backend.cmd` |
| 初始化失败提示「Access denied」 | 数据库账号权限不足（建库需要 `CREATE` 权限）；用 root 或授权账号重试：`scripts\configure.ps1 -InitDb 1 ...` |
| 页面显示但接口 500 | 检查 `logs\trackmeet.log`；多为库结构未初始化（重跑初始化脚本，脚本有执行记录表不会重复导入） |
| 中文 PDF 导出失败（错误码 50003） | 目标机缺中文字体：把 `simhei.ttf` 放入安装目录并设置 `trackmeet.pdf.font-path`（`config\application-prod.yml`），或用浏览器 A4 打印另存为 PDF |
| 忘记 admin 口令 | 修改 `config\application-prod.yml` 的 `trackmeet.default-password`，停止服务后删除库中 admin 行的口令或以新口令重装初始化 |
| 卸载后 8080 仍被占用 | 手工结束残留进程：`scripts\stop-backend.cmd` 或任务管理器结束 `java.exe` |

---

## 七、在本仓库环境之外构建（重要）

本仓库的离线开发环境**无法**产出可安装 EXE：缺少 Maven 与离线依赖仓库（编不出后端 jar）、缺少 `esbuild/rollup`
且无外网（编不出前端 dist）。请在下述任一环境执行第三节的命令获得 EXE：

1. **有网络的 Windows 开发机**：安装 JDK 17/21、Maven、Node+pnpm、Inno Setup 6，然后
   `pwsh installer/build-installer.ps1`；
2. **CI（推荐，零本地环境）**：仓库已内置 `.github/workflows/build-installer.yml`，
   推送到 GitHub 后从 Actions 的 Artifacts 直接下载 `TrackMeet-Setup-<版本>.exe`；
3. **内网构建机**：预先准备 Maven 依赖与 pnpm store（离线镜像），再加 `-SkipFrontend`/`-SkipBackend` 复用已有产物。

> 无管理员权限或不想安装时，用 `-Portable` 生成免安装绿色包分发给用户即可（同样双击即用，仅需手工启动）。
