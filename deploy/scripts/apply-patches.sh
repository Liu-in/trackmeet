#!/usr/bin/env bash
# ============================================================================
# TrackMeet 增量补丁执行脚本（Linux / macOS / WSL / Git Bash）
# ----------------------------------------------------------------------------
# 适用场景：数据库卷中已存在数据（/docker-entrypoint-initdb.d 不再执行），
#           但代码升级引入了新的 DDL / 权限点补丁（sql/07、08、09 …）。
#
# Usage:
#   bash deploy/scripts/apply-patches.sh [选项]
#
# 选项:
#   --from <NN>       从编号 NN 及之后的补丁开始执行（默认 07）
#   --only <NN,NN>    只执行指定编号的补丁，例如 --only 08,09
#   --list            仅列出补丁文件与「是否已生效」探测结果（不写库）
#   --dry-run         打印将要执行的补丁清单与文件首部（不写库）
#   --yes, -y         跳过交互确认
#   --container <名>  MySQL 容器名（默认 .env 的 MYSQL_CONTAINER）
#   --sql-dir <DIR>   补丁目录（默认 <仓库>/sql）
#   -h, --help        显示本帮助
#
# 示例:
#   bash deploy/scripts/apply-patches.sh --list
#   bash deploy/scripts/apply-patches.sh                 # 依次执行 07、08、09
#   bash deploy/scripts/apply-patches.sh --from 09       # 只执行 09
#   bash deploy/scripts/apply-patches.sh --only 08,09 --yes
#
# 说明:
#   1) sql/07、08、09 均为幂等脚本（INSERT ... WHERE NOT EXISTS / INSERT IGNORE /
#      MODIFY COLUMN），重复执行安全；执行前仍建议先备份： bash scripts/backup.sh
#   2) 补丁文件内部自行 USE `trackmeet`；若改了 DB_NAME，请同步修改
#      sql/0X-patch-*.sql 顶部的 USE 语句（本脚本按文件原样执行）；
#   3) 探测标记：07→event:query、08→schedule:add、09→screen:board（取自 sys_permission）；
#   4) 退出码：0 全部成功 / 1 参数或环境错误 / 2 某补丁执行失败（立即停止）/ 3 用户取消。
# ============================================================================
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
DEFAULT_SQL_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)/sql"

log()  { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
warn() { printf '[%s] [警告] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; }
die()  { printf '[%s] [错误] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; exit "${2:-1}"; }

usage() { sed -n '2,33p' "$0" | sed 's/^# \{0,1\}//'; }

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
ARG_FROM="07"
ARG_ONLY=""
ARG_CONTAINER=""
ARG_SQL_DIR=""
DO_LIST="no"
DO_DRYRUN="no"
ASSUME_YES="no"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --from)        [ "$#" -ge 2 ] || die "--from 需要一个编号（如 07）" 1; ARG_FROM="$2"; shift 2 ;;
    --from=*)      ARG_FROM="${1#*=}"; shift ;;
    --only)        [ "$#" -ge 2 ] || die "--only 需要编号列表（如 08,09）" 1; ARG_ONLY="$2"; shift 2 ;;
    --only=*)      ARG_ONLY="${1#*=}"; shift ;;
    --container)   [ "$#" -ge 2 ] || die "--container 需要一个容器名参数" 1; ARG_CONTAINER="$2"; shift 2 ;;
    --container=*) ARG_CONTAINER="${1#*=}"; shift ;;
    --sql-dir)     [ "$#" -ge 2 ] || die "--sql-dir 需要一个目录参数" 1; ARG_SQL_DIR="$2"; shift 2 ;;
    --sql-dir=*)   ARG_SQL_DIR="${1#*=}"; shift ;;
    --list)        DO_LIST="yes"; shift ;;
    --dry-run)     DO_DRYRUN="yes"; shift ;;
    --yes|-y)      ASSUME_YES="yes"; shift ;;
    -h|--help)     usage; exit 0 ;;
    *)             usage; die "未知参数：$1" 1 ;;
  esac
done

[[ "$ARG_FROM" =~ ^[0-9]{2}$ ]] || die "--from 必须是两位编号（如 07）" 1

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
SQL_DIR="${ARG_SQL_DIR:-$DEFAULT_SQL_DIR}"

[ -d "$SQL_DIR" ] || die "补丁目录不存在：$SQL_DIR（可用 --sql-dir 指定）" 1
[ -n "${DB_USERNAME:-}" ] || die "缺少 DB_USERNAME：请在 $DEPLOY_DIR/.env 中配置" 1
[ -n "${DB_PASSWORD:-}" ] || die "缺少 DB_PASSWORD：请在 $DEPLOY_DIR/.env 中配置" 1

# ---------------------------------------------------------------------------
# 收集补丁文件（sql/[0-9][0-9]-patch-*.sql）
# ---------------------------------------------------------------------------
ALL_PATCHES="$(ls -1 "$SQL_DIR"/[0-9][0-9]-patch-*.sql 2>/dev/null | sort || true)"
[ -n "$ALL_PATCHES" ] || die "未找到补丁文件（$SQL_DIR/[0-9][0-9]-patch-*.sql）" 1

SELECTED=""
while IFS= read -r f; do
  [ -n "$f" ] || continue
  num="$(basename "$f" | cut -c1-2)"
  if [ -n "$ARG_ONLY" ]; then
    case ",$ARG_ONLY," in
      *",$num,"*) SELECTED="${SELECTED}${f}"$'\n' ;;
    esac
  elif [ "$num" \> "$ARG_FROM" ] || [ "$num" = "$ARG_FROM" ]; then
    SELECTED="${SELECTED}${f}"$'\n'
  fi
done <<< "$ALL_PATCHES"
SELECTED="${SELECTED%$'\n'}"

[ -n "$SELECTED" ] || die "没有匹配的补丁（--from ${ARG_FROM} / --only ${ARG_ONLY:-未指定}）" 1

# ---------------------------------------------------------------------------
# 容器与口令准备（--list 也尝试探测「是否已生效」，探测失败则显示 unknown）
# ---------------------------------------------------------------------------
CONTAINER_OK="no"
if command -v docker >/dev/null 2>&1 \
   && docker inspect -f '{{.State.Running}}' "$CONTAINER" >/dev/null 2>&1 \
   && [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" = "true" ]; then
  CONTAINER_OK="yes"
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
CONTAINER_CNF="/tmp/.trackmeet_patch_${STAMP}.cnf"
cleanup() { docker exec "$CONTAINER" sh -c "rm -f '$CONTAINER_CNF'" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

MYSQL_BASE="mysql --defaults-extra-file='$CONTAINER_CNF' --default-character-set=utf8mb4"

prepare_cnf() {
  [ "$CONTAINER_OK" = "yes" ] || die "MySQL 容器不存在或未运行：$CONTAINER（先 docker compose up -d mysql）" 1
  printf '[client]\nuser=%s\npassword="%s"\n' "$DB_USERNAME" "$DB_PASSWORD" \
    | docker exec -i "$CONTAINER" sh -c "umask 077; cat > '$CONTAINER_CNF'" \
    || die "写入容器内临时口令文件失败：$CONTAINER" 2
}

query_scalar() {
  docker exec "$CONTAINER" sh -c \
    "$MYSQL_BASE -N -B -e \"$1\"" 2>/dev/null | tr -d '[:space:]'
}

# 补丁编号 → 探测标记（权限编码）
marker_of() {
  case "$1" in
    07) printf 'event:query' ;;
    08) printf 'schedule:add' ;;
    09) printf 'screen:board' ;;
    *)  printf '' ;;
  esac
}

# ---------------------------------------------------------------------------
# --list / --dry-run
# ---------------------------------------------------------------------------
if [ "$DO_LIST" = "yes" ]; then
  log "补丁目录：$SQL_DIR"
  log "容器：$CONTAINER（就绪：$CONTAINER_OK）  库：$DB_NAME"
  if [ "$CONTAINER_OK" = "yes" ]; then prepare_cnf; fi
  printf '%-34s %-10s %s\n' '补丁文件' '是否已生效' '说明'
  printf '%s\n' "$SELECTED" | while IFS= read -r f; do
    [ -n "$f" ] || continue
    name="$(basename "$f")"
    num="$(printf '%s' "$name" | cut -c1-2)"
    marker="$(marker_of "$num")"
    state="unknown"
    if [ "$CONTAINER_OK" = "yes" ] && [ -n "$marker" ]; then
      hit="$(query_scalar "SELECT COUNT(*) FROM sys_permission WHERE perm_code='$marker';")"
      if [ "${hit:-0}" != "0" ] && [ -n "${hit:-}" ]; then state="已生效(无需执行)"; else state="未生效(待执行)"; fi
    fi
    printf '%-34s %-10s %s\n' "$name" "$state" "探测标记：${marker:-无}"
  done
  log "--list 模式：未执行任何 SQL。"
  exit 0
fi

if [ "$DO_DRYRUN" = "yes" ]; then
  log "--dry-run 模式：以下为待执行补丁首 25 行预览，未写库。"
  printf '%s\n' "$SELECTED" | while IFS= read -r f; do
    [ -n "$f" ] || continue
    log "----- $(basename "$f") -----"
    sed -n '1,25p' "$f"
  done
  exit 0
fi

# ---------------------------------------------------------------------------
# 确认与执行
# ---------------------------------------------------------------------------
PATCH_COUNT="$(printf '%s\n' "$SELECTED" | grep -c . || true)"
log "补丁目录：$SQL_DIR"
log "将执行 $PATCH_COUNT 个补丁（目标容器 $CONTAINER，库 $DB_NAME）："
printf '%s\n' "$SELECTED" | while IFS= read -r f; do [ -n "$f" ] && log "  - $(basename "$f")"; done

if [ "$ASSUME_YES" != "yes" ]; then
  printf '补丁幂等、可重复执行，但建议先备份。确认继续？输入 yes 继续：'
  read -r ANSWER || ANSWER=""
  case "$ANSWER" in
    y|Y|yes|YES|Yes) ;;
    *) die "用户取消。" 3 ;;
  esac
fi

prepare_cnf

table_count() {
  query_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';"
}

BEFORE="$(table_count)"; BEFORE="${BEFORE:-0}"
log "执行前表数量：$BEFORE"

FAILED=0
COUNT=0
while IFS= read -r f; do
  [ -n "$f" ] || continue
  COUNT=$((COUNT + 1))
  NAME="$(basename "$f")"
  log "== [$COUNT/$PATCH_COUNT] 执行补丁：$NAME =="
  ERRLOG="$(mktemp -t trackmeet_patch_err_XXXXXX.log)"
  set +e
  docker exec -i "$CONTAINER" sh -c "$MYSQL_BASE '$DB_NAME'" < "$f" 2>"$ERRLOG"
  STATUS=$?
  set -e
  if [ -s "$ERRLOG" ]; then
    grep -v -i 'using a password on the command line' "$ERRLOG" | sed 's/^/    [mysql] /' || true
  fi
  rm -f "$ERRLOG"
  if [ "$STATUS" -ne 0 ]; then
    FAILED=1
    warn "补丁 $NAME 执行失败（退出码 $STATUS），已停止后续补丁；请修复后重跑（补丁幂等）。"
    break
  fi
  log "补丁 $NAME 执行成功。"
done <<< "$SELECTED"

AFTER="$(table_count)"; AFTER="${AFTER:-0}"
log "执行后表数量：$AFTER（变化 $((AFTER - BEFORE))）"

if [ "$FAILED" -ne 0 ]; then
  exit 2
fi

log "全部补丁执行完成（共 $COUNT 个）。建议继续："
log "  1) docker compose restart backend"
log "  2) bash scripts/smoke-test.sh"
exit 0
