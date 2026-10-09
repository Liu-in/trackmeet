# TrackMeet 运维脚本说明（deploy/scripts）

本目录提供部署与运维脚本，**bash 与 PowerShell 两端行为一致**：命名、参数、输出与退出码一一对应，可任选其一。

| 脚本 | 用途 | 对应脚本（Windows） |
| --- | --- | --- |
| `start.sh` | 一键 up / down / restart / logs / ps | `start.ps1` |
| `backup.sh` | 逻辑备份（mysqldump + gzip + 保留策略） | `backup.ps1` |
| `restore.sh` | 恢复（二次确认 + 安全备份 + 表数量校验） | `restore.ps1` |
| `apply-patches.sh` | 已建库环境的增量补丁执行（07/08/09…） | `apply-patches.ps1` |
| `smoke-test.sh` | 冒烟验收（TC-SMOKE-01…16） | `smoke-test.ps1` |

> 所有脚本都可直接读懂：文件顶部都有 `Usage:` 用法注释块（参数 + 示例 + 退出码）。
> 执行脚本前请确保 `deploy/.env` 已存在（`cp .env.example .env`）。

## 1. 通用约定

- **口令来源**：一律从 `deploy/.env`（或同名环境变量，环境变量优先）读取，脚本内不硬编码任何口令；缺失时直接报错退出。
- **口令不落命令行**：备份 / 恢复 / 补丁脚本会在 **MySQL 容器内**生成临时 `--defaults-extra-file`（`umask 077`，路径 `/tmp/.trackmeet_*.cnf`），脚本结束时（含 Ctrl+C 与出错退出）自动删除，因此 `ps`、shell 历史、PowerShell 记录中都看不到口令。
  - bash 端用 `trap cleanup EXIT INT TERM`；
  - PowerShell 端的错误路径统一收敛到 `catch` 中再 `exit`，保证 `finally`（清理逻辑）必定执行。
- **退出码**：`0` 成功；`1` 参数或环境错误；`2` 数据库操作失败（备份/恢复/补丁）；`3` 用户取消。
- **临时文件**：bash 用 `mktemp`，PowerShell 用 `$env:TEMP`；均在 `finally`/`trap` 中清理。
- **Windows 注意**：bash 脚本需在 Git Bash / WSL 中运行（`docker` 命令可正常调用宿主 Docker）；Windows 原生请用 `.ps1`。

## 2. start.sh / start.ps1

```bash
bash deploy/scripts/start.sh [up|down|restart|logs|ps]     # 默认 up
powershell -ExecutionPolicy Bypass -File deploy\scripts\start.ps1 -Action up
```

- 若 `deploy/.env` 不存在，会先由 `.env.example` 复制生成并提示修改。
- `up` 会执行 `docker compose up -d --build`，随后打印服务状态与访问地址。
- 退出码：`0` 成功；`1` 未知动作或 compose 失败。

## 3. backup.sh / backup.ps1（数据库备份）

```bash
bash deploy/scripts/backup.sh [--keep N] [--out-dir DIR] [--container NAME]
powershell -ExecutionPolicy Bypass -File deploy\scripts\backup.ps1 [-Keep N] [-OutDir DIR] [-Container NAME]
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `--keep N` / `-Keep N` | 30（`BACKUP_KEEP`） | 仅保留最新 N 份，`0` 表示不清理 |
| `--out-dir DIR` / `-OutDir DIR` | `deploy/backups`（`BACKUP_DIR`） | 备份输出目录，支持绝对路径 |
| `--container NAME` / `-Container NAME` | `trackmeet-mysql`（`MYSQL_CONTAINER`） | MySQL 容器名 |

**产出**：`trackmeet_<库名>_<yyyyMMdd_HHmmss>.sql.gz`（两端命名一致，可交叉恢复）。

**流程**：容器内 `mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF --default-character-set=utf8mb4 --hex-blob --no-create-db` → gzip 压缩 → `gzip -t`/GZipStream 完整性校验 → 打印压缩前后大小与 `CREATE TABLE` 数量 → 按数量保留策略清理旧备份。

**示例**

```bash
bash deploy/scripts/backup.sh --keep 7 --out-dir /data/backup
```

```powershell
.\backup.ps1 -Keep 7 -OutDir D:\backup
```

**建议的定时任务**

```bash
# crontab -e  每天 02:30 备份，保留 30 份
30 2 * * * cd /opt/trackmeet/deploy && bash scripts/backup.sh --keep 30 >> /var/log/trackmeet-backup.log 2>&1
```

```powershell
# 任务计划程序：每天 02:30
powershell -ExecutionPolicy Bypass -File D:\trackmeet\deploy\scripts\backup.ps1 -Keep 30
```

退出码：`0` 成功 / `1` 参数或环境错误 / `2` 备份失败。

## 4. restore.sh / restore.ps1（数据库恢复）

```bash
bash deploy/scripts/restore.sh <备份文件.sql.gz|.sql> [--yes] [--no-safety] [--keep N] [--container NAME]
powershell -ExecutionPolicy Bypass -File deploy\scripts\restore.ps1 -BackupFile <路径> [-Yes] [-NoSafety] [-Keep N] [-Container NAME]
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| 位置参数 / `-BackupFile` | 必填 | 待恢复的 `.sql.gz` 或 `.sql` |
| `--yes` / `-Yes` | 关 | 跳过交互二次确认（自动化用） |
| `--no-safety` / `-NoSafety` | 关 | 跳过恢复前的自动安全备份（不推荐） |
| `--keep N` / `-Keep N` | 30 | 安全备份的保留份数 |
| `--container NAME` | `trackmeet-mysql` | MySQL 容器名 |

**流程**：文件存在性/完整性校验 → 交互确认（输入 `yes`）→ 安全备份当前库 → 解压到临时 `.sql` → `docker exec -i mysql` 导入 → 打印 `information_schema` 实际表数量与备份内 `CREATE TABLE` 数量对比 → 提示重启后端与跑冒烟。

**恢复后动作**

```bash
docker compose restart backend
bash scripts/smoke-test.sh
```

退出码：`0` 成功 / `1` 参数或环境错误 / `2` 备份或恢复失败 / `3` 用户取消。

## 5. apply-patches.sh / apply-patches.ps1（增量补丁）

用于**卷中已有数据**（`/docker-entrypoint-initdb.d` 不再执行）时执行 `sql/07`、`08`、`09` 等增量补丁。

```bash
bash deploy/scripts/apply-patches.sh [--from NN] [--only NN,NN] [--list] [--dry-run] [--yes] [--container NAME] [--sql-dir DIR]
powershell -ExecutionPolicy Bypass -File deploy\scripts\apply-patches.ps1 [-From NN] [-Only NN,NN] [-List] [-DryRun] [-Yes] [-Container NAME] [-SqlDir DIR]
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `--from NN` / `-From NN` | `07` | 执行编号 ≥ NN 的补丁 |
| `--only NN,NN` / `-Only` | 空 | 只执行指定编号 |
| `--list` / `-List` | 关 | 只列出补丁与「是否已生效」探测结果（探测 `sys_permission.perm_code`） |
| `--dry-run` / `-DryRun` | 关 | 打印待执行清单与文件首部，不写库 |
| `--yes` / `-Yes` | 关 | 跳过交互确认 |
| `--sql-dir DIR` / `-SqlDir DIR` | `<仓库>/sql` | 补丁目录 |

```bash
bash deploy/scripts/apply-patches.sh --list        # 先看哪些已生效
bash deploy/scripts/apply-patches.sh               # 依次执行 07、08、09
bash deploy/scripts/apply-patches.sh --from 09     # 只执行 09
```

> `sql/07`、`08`、`09` 均为幂等脚本（`INSERT ... WHERE NOT EXISTS` / `INSERT IGNORE` / `MODIFY COLUMN`），
> 重复执行安全；但**仍然建议先 `backup.sh`**。补丁文件内部自行 `USE \`trackmeet\``，
> 若修改了 `DB_NAME`，需同步修改补丁文件顶部的 `USE` 语句。

退出码：`0` 全部成功 / `1` 参数或环境错误 / `2` 某补丁失败 / `3` 用户取消。

## 6. smoke-test.sh / smoke-test.ps1（冒烟验收）

```bash
bash deploy/scripts/smoke-test.sh [--base URL] [--web URL] [--meet-id ID] [--user 账号] [--password 口令] [--keep-going] [--timeout 秒]
powershell -ExecutionPolicy Bypass -File deploy\scripts\smoke-test.ps1 [-Base URL] [-Web URL] [-MeetId ID] [-User 账号] [-Password 口令] [-KeepGoing] [-Timeout 秒]
```

- 默认后端 `http://127.0.0.1:${BACKEND_PORT}/api`、前端 `http://127.0.0.1:${WEB_PORT}`（取自 `.env`）。
- 默认账号 `admin` / `Admin@123`（或 `.env` 的 `DEFAULT_ACCOUNT_PASSWORD`）。
- 默认**失败即停止**，加 `--keep-going` / `-KeepGoing` 跑完全部用例。
- 输出「用例编号 + 结果 + 断言」表格，末尾打印 `通过 x/总 y`。
- 依赖：bash + `curl`（JSON 解析优先 `jq` → `python3` → `grep/sed`，无需额外安装）；
  PowerShell 端自带 `Invoke-Api` 封装（基于 `System.Net.HttpWebRequest`，便于精确控制超时与读取非 200 的响应体），
  仅用 `ConvertFrom-Json` 解析，**不依赖 jq / python**。

退出码：`0` 全部通过 / `1` 存在失败用例 / `2` 环境不可用（后端未启动等）。

用例清单（编号 + 断言）见 [`deploy/README.md` 第 7 节](../README.md)。

## 7. 端到端演练（备份 → 恢复 → 冒烟）

```bash
cd deploy

# 1) 备份
bash scripts/backup.sh --keep 10
cp backups/trackmeet_trackmeet_<时间戳>.sql.gz /tmp/演练备份.sql.gz

# 2) 制造一点差异（可选，便于观察恢复效果）
docker exec -it trackmeet-mysql \
  mysql -uroot -p -e "USE trackmeet; SELECT COUNT(*) FROM athlete;"

# 3) 恢复（需要输入 yes；恢复前会自动再做一次安全备份）
bash scripts/restore.sh /tmp/演练备份.sql.gz

# 4) 重启后端 + 冒烟
docker compose restart backend
bash scripts/smoke-test.sh
```

```powershell
Set-Location deploy
.\scripts\backup.ps1 -Keep 10
Copy-Item .\backups\trackmeet_trackmeet_*.sql.gz $env:TEMP\演练备份.sql.gz
.\scripts\restore.ps1 -BackupFile $env:TEMP\演练备份.sql.gz
docker compose restart backend
.\scripts\smoke-test.ps1
```
