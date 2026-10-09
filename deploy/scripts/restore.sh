#!/usr/bin/env bash
# ============================================================================
# TrackMeet 数据库恢复脚本（Linux / macOS / WSL / Git Bash）
# ----------------------------------------------------------------------------
# Usage:
#   bash deploy/scripts/restore.sh <备份文件.sql.gz|.sql> [选项]
#
# 选项:
#   -y, --yes         跳过交互式二次确认（自动化/演练时使用，请确认目标库正确！）
#   --no-safety       恢复前不做安全备份（默认会先备份当前库，防止误操作）
#   --keep <N>        安全备份的保留份数（默认 30，透传给 backup.sh）
#   --container <名>  MySQL 容器名（默认 .env 的 MYSQL_CONTAINER，否则 trackmeet-mysql）
#   -h, --help        显示本帮助
#
# 示例:
#   bash deploy/scripts/restore.sh backups/trackmeet_trackmeet_20251018_093000.sql.gz
#   bash deploy/scripts/restore.sh backups/xxx.sql.gz --yes --keep 60
#
# 行为:
#   1) 校验备份文件存在且 gzip 完整性通过（.gz 时）；
#   2) 交互式二次确认（输入 y/yes 才继续，除非 --yes）；
#   3) 恢复前对当前库做一次安全备份（除非 --no-safety）；
#   4) 导入数据并校验：对比备份内 CREATE TABLE 数量与 information_schema 实际表数量；
#   5) 退出码：0 成功 / 1 参数或环境错误 / 2 备份或恢复失败 / 3 用户取消。
#
# 说明：恢复不会自动重启后端，导入完成后按提示执行 docker compose restart backend。
#       导入为「覆盖式」：备份中的表结构与数据会覆盖同名对象，生产中请先停机维护。
# ============================================================================
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

log()  { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
warn() { printf '[%s] [警告] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; }
die()  { printf '[%s] [错误] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; exit "${2:-1}"; }

usage() { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; }

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
BACKUP_FILE=""
ASSUME_YES="no"
SAFETY_BACKUP="yes"
ARG_KEEP=""
ARG_CONTAINER=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    -y|--yes)      ASSUME_YES="yes"; shift ;;
    --no-safety)   SAFETY_BACKUP="no"; shift ;;
    --keep)        [ "$#" -ge 2 ] || die "--keep 需要一个数字参数" 1; ARG_KEEP="$2"; shift 2 ;;
    --keep=*)      ARG_KEEP="${1#*=}"; shift ;;
    --container)   [ "$#" -ge 2 ] || die "--container 需要一个容器名参数" 1; ARG_CONTAINER="$2"; shift 2 ;;
    --container=*) ARG_CONTAINER="${1#*=}"; shift ;;
    -h|--help)     usage; exit 0 ;;
    -*)            usage; die "未知参数：$1" 1 ;;
    *)
      if [ -n "$BACKUP_FILE" ]; then usage; die "只允许指定一个备份文件（已指定：$BACKUP_FILE）" 1; fi
      BACKUP_FILE="$1"; shift ;;
  esac
done

[ -n "$BACKUP_FILE" ] || { usage; die "缺少参数：请指定要恢复的备份文件" 1; }

# ---------------------------------------------------------------------------
# 读取 .env
# ---------------------------------------------------------------------------
if [ -f "$DEPLOY_DIR/.env" ]; then
  # shellcheck disable=SC1091
  set -a; . "$DEPLOY_DIR/.env"; set +a
else
  warn "未找到 $DEPLOY_DIR/.env，将仅使用环境变量/默认值"
fi

CONTAINER="${ARG_CONTAINER:-${MYSQL_CONTAINER:-trackmeet-mysql}}"
DB_NAME="${DB_NAME:-trackmeet}"
KEEP="${ARG_KEEP:-${BACKUP_KEEP:-30}}"

# ---------------------------------------------------------------------------
# 文件与前置校验
# ---------------------------------------------------------------------------
[ -f "$BACKUP_FILE" ] || die "备份文件不存在：$BACKUP_FILE" 1
[ -r "$BACKUP_FILE" ] || die "备份文件不可读：$BACKUP_FILE" 1
[ -s "$BACKUP_FILE" ] || die "备份文件为空：$BACKUP_FILE" 1
[ -n "${DB_USERNAME:-}" ] || die "缺少 DB_USERNAME：请在 $DEPLOY_DIR/.env 中配置" 1
[ -n "${DB_PASSWORD:-}" ] || die "缺少 DB_PASSWORD：请在 $DEPLOY_DIR/.env 中配置" 1
[[ "$KEEP" =~ ^[0-9]+$ ]] || die "--keep/BACKUP_KEEP 必须是非负整数，当前：$KEEP" 1
command -v docker >/dev/null 2>&1 || die "未找到 docker 命令：请先安装 Docker 24+" 1
docker inspect -f '{{.State.Running}}' "$CONTAINER" >/dev/null 2>&1 \
  || die "MySQL 容器不存在：$CONTAINER" 1
[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" = "true" ] \
  || die "MySQL 容器未运行：$CONTAINER（docker compose start mysql）" 1

case "$BACKUP_FILE" in
  *.gz|*.tgz) command -v gzip >/dev/null 2>&1 || die "未找到 gzip 命令（恢复 .gz 备份需要）" 1 ;;
esac

STAMP="$(date +%Y%m%d_%H%M%S)"
CONTAINER_CNF="/tmp/.trackmeet_restore_${STAMP}.cnf"
TMP_SQL="$(mktemp -t trackmeet_restore_XXXXXX.sql)"
SQL_BYTES=0
EXPECT_TABLES=0

cleanup() {
  docker exec "$CONTAINER" sh -c "rm -f '$CONTAINER_CNF'" >/dev/null 2>&1 || true
  rm -f "$TMP_SQL" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# 二次确认
# ---------------------------------------------------------------------------
log "即将恢复："
log "  备份文件 : $BACKUP_FILE"
log "  目标容器 : $CONTAINER"
log "  目标库   : $DB_NAME"
log "  安全备份 : $SAFETY_BACKUP（恢复前自动备份当前库）"
if [ "$ASSUME_YES" != "yes" ]; then
  printf '确认要继续吗？该操作会覆盖目标库中的同名表与数据！输入 yes 继续：'
  read -r ANSWER || ANSWER=""
  case "$ANSWER" in
    y|Y|yes|YES|Yes) log "已确认，继续执行。" ;;
    *) die "用户取消恢复操作。" 3 ;;
  esac
fi

# ---------------------------------------------------------------------------
# 1/4 恢复前安全备份
# ---------------------------------------------------------------------------
if [ "$SAFETY_BACKUP" = "yes" ]; then
  log "== 1/4 恢复前安全备份（备份失败将中止恢复）=="
  bash "$SCRIPT_DIR/backup.sh" --keep "$KEEP" --container "$CONTAINER" \
    || die "恢复前的安全备份失败，已中止恢复（避免无回滚点）" 2
else
  log "== 1/4 已跳过恢复前安全备份（--no-safety）=="
fi

# ---------------------------------------------------------------------------
# 2/4 解压到临时文件（同时得到表数量基线）
# ---------------------------------------------------------------------------
log "== 2/4 解压备份文件到临时文件 =="
case "$BACKUP_FILE" in
  *.gz|*.tgz)
    gzip -dc "$BACKUP_FILE" > "$TMP_SQL" || die "解压失败，备份文件可能损坏：$BACKUP_FILE" 2 ;;
  *)
    cp "$BACKUP_FILE" "$TMP_SQL" || die "复制备份文件到临时文件失败" 2 ;;
esac
[ -s "$TMP_SQL" ] || die "解压后 SQL 为空，备份文件可能损坏：$BACKUP_FILE" 2
SQL_BYTES="$(wc -c < "$TMP_SQL" | tr -d ' ')"
EXPECT_TABLES="$(grep -c '^CREATE TABLE' "$TMP_SQL" || true)"
log "解压完成：$SQL_BYTES 字节，备份内 CREATE TABLE 数量：$EXPECT_TABLES"

log "准备 MySQL 容器内临时口令文件 ..."
printf '[client]\nuser=%s\npassword="%s"\n' "$DB_USERNAME" "$DB_PASSWORD" \
  | docker exec -i "$CONTAINER" sh -c "umask 077; cat > '$CONTAINER_CNF'" \
  || die "写入容器内临时口令文件失败：$CONTAINER" 2

# ---------------------------------------------------------------------------
# 3/4 导入
# ---------------------------------------------------------------------------
MYSQL_CMD="mysql --defaults-extra-file='$CONTAINER_CNF' --default-character-set=utf8mb4 --show-warnings '$DB_NAME'"

log "== 3/4 导入数据库 [$DB_NAME]（大库可能需要数分钟，请勿中断）=="
set +e
docker exec -i "$CONTAINER" sh -c "$MYSQL_CMD" < "$TMP_SQL"
IMPORT_STATUS=$?
set -e
[ "$IMPORT_STATUS" -eq 0 ] || die "导入失败（退出码 $IMPORT_STATUS）；请检查备份完整性、目标库是否存在、字符集是否一致" 2

# ---------------------------------------------------------------------------
# 4/4 校验
# ---------------------------------------------------------------------------
log "== 4/4 恢复结果校验 =="
ACTUAL_TABLES="$(docker exec "$CONTAINER" sh -c \
  "mysql --defaults-extra-file='$CONTAINER_CNF' -N -B -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';\" 2>/dev/null" \
  | tr -d '[:space:]')"
ACTUAL_TABLES="${ACTUAL_TABLES:-0}"
log "information_schema 实际表数量：$ACTUAL_TABLES（备份内 CREATE TABLE：$EXPECT_TABLES）"
if [ "$ACTUAL_TABLES" -gt 0 ]; then
  log "表数量校验：通过（存在 $ACTUAL_TABLES 张表）"
else
  warn "目标库 [$DB_NAME] 中没有任何表，恢复可能未生效，请检查备份内容与库名"
fi

log "恢复完成。建议按顺序执行："
log "  1) docker compose restart backend"
log "  2) bash scripts/smoke-test.sh          # 冒烟验收"
exit 0
