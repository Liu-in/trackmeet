#!/usr/bin/env bash
# ============================================================================
# TrackMeet 数据库逻辑备份脚本（Linux / macOS / WSL / Git Bash）
# ----------------------------------------------------------------------------
# Usage:
#   bash deploy/scripts/backup.sh [选项]
#
# 选项:
#   --keep <N>        保留最近 N 份备份，超出的旧备份自动删除（默认 30，或 .env 的 BACKUP_KEEP）
#   --out-dir <DIR>   备份输出目录（默认 .env 的 BACKUP_DIR，否则 deploy/backups）
#   --container <名>  MySQL 容器名（默认 .env 的 MYSQL_CONTAINER，否则 trackmeet-mysql）
#   -h, --help        显示本帮助
#
# 示例:
#   bash deploy/scripts/backup.sh
#   bash deploy/scripts/backup.sh --keep 7 --out-dir /data/backup
#   bash deploy/scripts/backup.sh --keep 0          # 0 = 不做清理，全部保留
#
# 产出:
#   <out-dir>/trackmeet_<库名>_<yyyyMMdd_HHmmss>.sql.gz
#
# 说明:
#   1) 口令不落命令行：容器内生成临时 --defaults-extra-file（0600），脚本退出时自动删除，
#      因此 docker ps / history 中看不到口令；
#   2) 使用 --single-transaction（InnoDB 一致性快照，不锁表）、--routines --triggers --events、
#      --set-gtid-purged=OFF（便于恢复到非 GTID 环境）、--default-character-set=utf8mb4；
#   3) 备份完成后做 gzip -t 完整性校验，并打印压缩前后大小；
#   4) upload/ 附件目录（如存在）不在本脚本范围，请另行打包：tar -czf upload_<时间戳>.tar.gz upload/
#   5) 退出码：0 成功 / 1 参数或环境错误 / 2 备份执行失败。
# ============================================================================
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

# ---------------------------------------------------------------------------
# 日志
# ---------------------------------------------------------------------------
log()  { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
warn() { printf '[%s] [警告] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; }
die()  { printf '[%s] [错误] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; exit "${2:-1}"; }

usage() { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; }

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
ARG_KEEP=""
ARG_OUT_DIR=""
ARG_CONTAINER=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --keep)      [ "$#" -ge 2 ] || die "--keep 需要一个数字参数" 1; ARG_KEEP="$2"; shift 2 ;;
    --keep=*)    ARG_KEEP="${1#*=}"; shift ;;
    --out-dir)   [ "$#" -ge 2 ] || die "--out-dir 需要一个目录参数" 1; ARG_OUT_DIR="$2"; shift 2 ;;
    --out-dir=*) ARG_OUT_DIR="${1#*=}"; shift ;;
    --container) [ "$#" -ge 2 ] || die "--container 需要一个容器名参数" 1; ARG_CONTAINER="$2"; shift 2 ;;
    --container=*) ARG_CONTAINER="${1#*=}"; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           usage; die "未知参数：$1" 1 ;;
  esac
done

# ---------------------------------------------------------------------------
# 读取 .env（若存在；导出的变量优先级高于 .env）
# ---------------------------------------------------------------------------
if [ -f "$DEPLOY_DIR/.env" ]; then
  # shellcheck disable=SC1091
  set -a; . "$DEPLOY_DIR/.env"; set +a
else
  warn "未找到 $DEPLOY_DIR/.env，将仅使用环境变量/默认值（建议先 cp .env.example .env）"
fi

# 命令行参数 > .env > 默认值
KEEP="${ARG_KEEP:-${BACKUP_KEEP:-30}}"
CONTAINER="${ARG_CONTAINER:-${MYSQL_CONTAINER:-trackmeet-mysql}}"
DB_NAME="${DB_NAME:-trackmeet}"

OUT_DIR_RAW="${ARG_OUT_DIR:-${BACKUP_DIR:-backups}}"
case "$OUT_DIR_RAW" in
  /*) OUT_DIR="$OUT_DIR_RAW" ;;                 # 绝对路径
  *)  OUT_DIR="$DEPLOY_DIR/$OUT_DIR_RAW" ;;     # 相对 deploy/ 目录
esac

STAMP="$(date +%Y%m%d_%H%M%S)"
TARGET="$OUT_DIR/trackmeet_${DB_NAME}_${STAMP}.sql.gz"
CONTAINER_CNF="/tmp/.trackmeet_backup_${STAMP}.cnf"
TMP_PLAIN="${TARGET%.gz}"

# ---------------------------------------------------------------------------
# 前置校验
# ---------------------------------------------------------------------------
[ -n "${DB_USERNAME:-}" ] || die "缺少 DB_USERNAME：请在 $DEPLOY_DIR/.env 中配置（参考 .env.example）" 1
[ -n "${DB_PASSWORD:-}" ] || die "缺少 DB_PASSWORD：请在 $DEPLOY_DIR/.env 中配置（参考 .env.example）" 1
[[ "$KEEP" =~ ^[0-9]+$ ]] || die "--keep/ BACKUP_KEEP 必须是非负整数，当前：$KEEP" 1
command -v docker >/dev/null 2>&1 || die "未找到 docker 命令：请先安装 Docker 24+ / Docker Desktop" 1
command -v gzip  >/dev/null 2>&1 || die "未找到 gzip 命令：请先安装 gzip（Windows 可用 Git Bash 自带版本）" 1
docker inspect -f '{{.State.Running}}' "$CONTAINER" >/dev/null 2>&1 \
  || die "MySQL 容器不存在：$CONTAINER（请先 docker compose up -d mysql，或用 --container 指定）" 1
[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" = "true" ] \
  || die "MySQL 容器未运行：$CONTAINER（docker compose start mysql）" 1

mkdir -p "$OUT_DIR" || die "无法创建备份目录：$OUT_DIR" 1
[ -w "$OUT_DIR" ] || die "备份目录不可写：$OUT_DIR" 1

# ---------------------------------------------------------------------------
# 容器内口令文件（避免口令出现在命令行 / 历史记录中）
# ---------------------------------------------------------------------------
cleanup() {
  docker exec "$CONTAINER" sh -c "rm -f '$CONTAINER_CNF'" >/dev/null 2>&1 || true
  rm -f "$TMP_PLAIN" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

log "准备 MySQL 容器内临时口令文件 ..."
printf '[client]\nuser=%s\npassword="%s"\n' "$DB_USERNAME" "$DB_PASSWORD" \
  | docker exec -i "$CONTAINER" sh -c "umask 077; cat > '$CONTAINER_CNF'" \
  || die "写入容器内临时口令文件失败：$CONTAINER" 2

# ---------------------------------------------------------------------------
# 执行备份（容器内 mysqldump → 宿主机 gzip，避免挂载目录权限问题）
# ---------------------------------------------------------------------------
log "开始备份数据库 [$DB_NAME]（容器 $CONTAINER）→ $TARGET"
DUMP_CMD="mysqldump --defaults-extra-file='$CONTAINER_CNF' \
--single-transaction --quick --routines --triggers --events \
--set-gtid-purged=OFF --default-character-set=utf8mb4 \
--hex-blob --no-create-db \
'$DB_NAME'"

set +e
docker exec "$CONTAINER" sh -c "$DUMP_CMD" | gzip -c > "$TARGET"
PIPE_STATUS=("${PIPESTATUS[@]}")
set -e

if [ "${PIPE_STATUS[0]:-1}" -ne 0 ]; then
  rm -f "$TARGET"
  die "mysqldump 执行失败（退出码 ${PIPE_STATUS[0]}）；请检查库名 [$DB_NAME] 与账号 [$DB_USERNAME] 是否存在" 2
fi
if [ "${PIPE_STATUS[1]:-1}" -ne 0 ]; then
  rm -f "$TARGET"
  die "gzip 压缩失败（退出码 ${PIPE_STATUS[1]}）；请检查磁盘剩余空间与目录权限" 2
fi
[ -s "$TARGET" ] || { rm -f "$TARGET"; die "备份文件为空，备份失败：$TARGET" 2; }

# ---------------------------------------------------------------------------
# 完整性校验与统计
# ---------------------------------------------------------------------------
if gzip -t "$TARGET" 2>/dev/null; then
  log "gzip 完整性校验：通过"
else
  die "gzip 完整性校验失败，备份文件可能损坏：$TARGET" 2
fi

GZ_BYTES="$(wc -c < "$TARGET" | tr -d ' ')"
RAW_BYTES="$(gzip -dc "$TARGET" | wc -c | tr -d ' ')"
TABLE_COUNT="$(gzip -dc "$TARGET" | grep -c '^CREATE TABLE' || true)"
log "备份完成：$TARGET"
log "文件大小：压缩后 $(awk -v b="$GZ_BYTES" 'BEGIN{printf "%.2f MB", b/1048576}')（$GZ_BYTES 字节），解压后 $(awk -v b="$RAW_BYTES" 'BEGIN{printf "%.2f MB", b/1048576}')（$RAW_BYTES 字节）"
log "备份内 CREATE TABLE 语句数（表数量校验用）：$TABLE_COUNT"

if [ -d "$DEPLOY_DIR/upload" ] || [ -d "$DEPLOY_DIR/../upload" ]; then
  warn "检测到 upload/ 附件目录：本脚本只备份数据库，附件请另行打包，例如：
       tar -czf $OUT_DIR/upload_${STAMP}.tar.gz -C <upload 的父目录> upload"
fi

# ---------------------------------------------------------------------------
# 保留策略：按修改时间保留最新 KEEP 份
# ---------------------------------------------------------------------------
if [ "$KEEP" -eq 0 ]; then
  log "保留策略：--keep 0，跳过清理（历史备份全部保留）"
else
  log "保留策略：仅保留最新 $KEEP 份（匹配 trackmeet_${DB_NAME}_*.sql.gz）"
  # ls -1t 按修改时间倒序；tail -n +$((KEEP+1)) 取第 KEEP 份之后的所有文件
  OLD_FILES="$(ls -1t "$OUT_DIR"/trackmeet_"${DB_NAME}"_*.sql.gz 2>/dev/null | tail -n +"$((KEEP + 1))" || true)"
  if [ -n "$OLD_FILES" ]; then
    printf '%s\n' "$OLD_FILES" | while IFS= read -r f; do
      [ -n "$f" ] || continue
      rm -f "$f" && log "已删除过期备份：$(basename "$f")"
    done
  else
    log "无需清理（当前份数未超过 $KEEP）"
  fi
fi

log "当前备份目录内容："
ls -lht "$OUT_DIR" 2>/dev/null | head -n 12 || true
exit 0
