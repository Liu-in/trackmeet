#!/usr/bin/env bash
# ============================================================================
# TrackMeet 部署冒烟验收脚本（Linux / macOS / WSL / Git Bash）
# ----------------------------------------------------------------------------
# 用途：对「已启动」的 Compose 环境按序调用关键接口，输出用例编号 + 结果表格，
#       任一用例失败即返回非 0 退出码，可直接接入 CI / 发版检查单。
#
# Usage:
#   bash deploy/scripts/smoke-test.sh [选项]
#
# 选项:
#   --base <URL>       后端 API 根地址（默认 http://127.0.0.1:<BACKEND_PORT>/api）
#                      脚本按 RFC 3986 语义用 "$API_BASE/../actuator/health" 取后端健康检查，
#                      curl 会自动归一化该相对路径；如你的客户端不归一化，请直接用
#                      --base http://127.0.0.1:8080 并在调用处手工拼 /actuator/health。
#   --web <URL>        前端（Nginx）根地址（默认 http://127.0.0.1:<WEB_PORT>），用于整链路验收
#   --meet-id <ID>     指定届次 ID（默认调用 /meets 自动取第一条，取不到则用 1）
#   --user <用户名>    登录账号（默认 admin）
#   --password <口令>  登录口令（默认取 .env 的 DEFAULT_ACCOUNT_PASSWORD，否则 Admin@123）
#   --keep-going       全部用例跑完（默认失败即停止，便于快速定位）
#   --timeout <秒>     单请求超时（默认 20）
#   -h, --help         显示本帮助
#
# 示例:
#   bash deploy/scripts/smoke-test.sh
#   bash deploy/scripts/smoke-test.sh --base http://127.0.0.1:8080/api --web http://127.0.0.1
#   bash deploy/scripts/smoke-test.sh --meet-id 1 --keep-going
#
# 依赖: bash + curl。JSON 解析优先用 jq → python3 → grep/sed（三者皆有其一即可，不强制安装）。
# 退出码: 0 全部通过 / 1 存在失败用例 / 2 环境不可用（后端未启动等）。
# 前置: 后端已启动且 DefaultAccountInitializer 已初始化默认口令（首启后约 5~20 秒）。
# ============================================================================
set -uo pipefail
# 注意：不使用 set -e —— 用例失败需要继续统计，错误由函数显式处理。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

log()  { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
die()  { printf '[%s] [错误] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; exit "${2:-2}"; }
usage() { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; }

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
ARG_BASE=""
ARG_WEB=""
ARG_MEET_ID=""
USERNAME="admin"
PASSWORD=""
KEEP_GOING="no"
TIMEOUT=20

while [ "$#" -gt 0 ]; do
  case "$1" in
    --base)      [ "$#" -ge 2 ] || die "--base 需要 URL" 1; ARG_BASE="$2"; shift 2 ;;
    --base=*)    ARG_BASE="${1#*=}"; shift ;;
    --web)       [ "$#" -ge 2 ] || die "--web 需要 URL" 1; ARG_WEB="$2"; shift 2 ;;
    --web=*)     ARG_WEB="${1#*=}"; shift ;;
    --meet-id)   [ "$#" -ge 2 ] || die "--meet-id 需要数字" 1; ARG_MEET_ID="$2"; shift 2 ;;
    --meet-id=*) ARG_MEET_ID="${1#*=}"; shift ;;
    --user)      [ "$#" -ge 2 ] || die "--user 需要用户名" 1; USERNAME="$2"; shift 2 ;;
    --user=*)    USERNAME="${1#*=}"; shift ;;
    --password)  [ "$#" -ge 2 ] || die "--password 需要口令" 1; PASSWORD="$2"; shift 2 ;;
    --password=*) PASSWORD="${1#*=}"; shift ;;
    --timeout)   [ "$#" -ge 2 ] || die "--timeout 需要秒数" 1; TIMEOUT="$2"; shift 2 ;;
    --timeout=*) TIMEOUT="${1#*=}"; shift ;;
    --keep-going) KEEP_GOING="yes"; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           usage; die "未知参数：$1" 1 ;;
  esac
done

# ---------------------------------------------------------------------------
# 读取 .env
# ---------------------------------------------------------------------------
if [ -f "$DEPLOY_DIR/.env" ]; then
  # shellcheck disable=SC1091
  set -a; . "$DEPLOY_DIR/.env"; set +a
fi

PASSWORD="${PASSWORD:-${DEFAULT_ACCOUNT_PASSWORD:-Admin@123}}"
API_BASE="${ARG_BASE:-http://127.0.0.1:${BACKEND_PORT:-8080}/api}"
API_BASE="${API_BASE%/}"
WEB_BASE="${ARG_WEB:-${WEB_BASE:-http://127.0.0.1:${WEB_PORT:-80}}}"
WEB_BASE="${WEB_BASE%/}"

command -v curl >/dev/null 2>&1 || die "未找到 curl 命令：请先安装 curl" 2

# ---------------------------------------------------------------------------
# 极简 JSON 解析（优先 jq → python3 → grep/sed）
# ---------------------------------------------------------------------------
JSON_READER="grep"
if command -v jq >/dev/null 2>&1; then JSON_READER="jq"
elif command -v python3 >/dev/null 2>&1; then JSON_READER="python3"; fi

# json_code <body>  → 顶层 code（解析失败输出 "-"）
json_code() {
  case "$JSON_READER" in
    jq) printf '%s' "$1" | jq -r 'if type=="object" and has("code") then (.code|tostring) else "-" end' 2>/dev/null ;;
    python3) printf '%s' "$1" | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    print("-"); sys.exit(0)
print(d.get("code","-") if isinstance(d,dict) else "-")' 2>/dev/null ;;
    *) printf '%s' "$1" | tr -d '\n' | grep -o '"code"[[:space:]]*:[[:space:]]*-*[0-9]\+' | head -n1 | grep -o '\-*[0-9]\+$' ;;
  esac
}

# json_token <body> → data.accessToken / accessToken（解析失败输出空）
json_token() {
  case "$JSON_READER" in
    jq) printf '%s' "$1" | jq -r '(.data.accessToken // .accessToken) // empty' 2>/dev/null ;;
    python3) printf '%s' "$1" | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    sys.exit(0)
if not isinstance(d,dict): sys.exit(0)
d=d.get("data",d)
print(d.get("accessToken","") if isinstance(d,dict) else "")' 2>/dev/null ;;
    *) printf '%s' "$1" | tr -d '\n' | grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n1 | sed 's/.*"\([^"]*\)"$/\1/' ;;
  esac
}

# json_first_id <body> → data.records[0].id（解析失败输出空）
json_first_id() {
  case "$JSON_READER" in
    jq) printf '%s' "$1" | jq -r '(.data.records // .records // []) | .[0].id // empty' 2>/dev/null ;;
    python3) printf '%s' "$1" | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    sys.exit(0)
if not isinstance(d,dict): sys.exit(0)
d=d.get("data",d)
recs=d.get("records") if isinstance(d,dict) else None
if isinstance(recs,list) and recs and isinstance(recs[0],dict) and recs[0].get("id") is not None:
    print(recs[0]["id"])' 2>/dev/null ;;
    *) printf '%s' "$1" | tr -d '\n' | grep -o '"records"[[:space:]]*:[[:space:]]*\[[[:space:]]*{[^}]*"id"[[:space:]]*:[[:space:]]*[0-9]\+' | head -n1 | grep -o '"id"[[:space:]]*:[[:space:]]*[0-9]\+$' | grep -o '[0-9]\+$' ;;
  esac
}

# ---------------------------------------------------------------------------
# HTTP
#   说明：本脚本用 curl 发请求（-m 控制超时，-w 单独取 HTTP 状态码），
#         JSON 解析优先 jq → python3 → grep/sed 兜底，因此不强制安装任何解析器。
# ---------------------------------------------------------------------------
TOKEN=""
MEET_ID=""

# http_call <METHOD> <URL> <BODY|->  → 设置 HTTP_STATUS / HTTP_BODY
http_call() {
  local method="$1" url="$2" body="${3:--}"
  local raw
  HTTP_STATUS="000"; HTTP_BODY=""
  if [ "$method" = "GET" ]; then
    raw="$(curl -sS -m "$TIMEOUT" -w $'\n%{http_code}' \
      ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
      ${MEET_ID:+-H "X-Meet-Id: $MEET_ID"} \
      "$url" 2>/dev/null)" || raw=""
  else
    raw="$(curl -sS -m "$TIMEOUT" -X "$method" -w $'\n%{http_code}' \
      -H "Content-Type: application/json" \
      ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
      ${MEET_ID:+-H "X-Meet-Id: $MEET_ID"} \
      ${body:+-d "$body"} \
      "$url" 2>/dev/null)" || raw=""
  fi
  if [ -n "$raw" ]; then
    HTTP_STATUS="$(printf '%s' "$raw" | tail -n1 | tr -d '[:space:]')"
    HTTP_BODY="$(printf '%s' "$raw" | sed '$d')"
  fi
  [ -n "$HTTP_STATUS" ] || HTTP_STATUS="000"
}

# ---------------------------------------------------------------------------
# 用例执行
# ---------------------------------------------------------------------------
PASS=0; FAIL=0; TOTAL=0; FIRST_FAIL=""

# run_case <编号> <说明> <METHOD> <URL> [BODY] [可接受code: 0|0,50003|any] [是否需要token: yes|no] [是否带届次头: yes|no]
run_case() {
  local id="$1" name="$2" method="$3" url="$4" body="${5:--}" accept="${6:-0}" need_token="${7:-yes}" need_meet="${8:-yes}"
  local code detail ok="yes"

  if [ "$need_token" = "yes" ] && [ -z "$TOKEN" ]; then
    ok="no"; code="-"; detail="缺少令牌（登录未成功）"
    TOTAL=$((TOTAL + 1)); FAIL=$((FAIL + 1)); FIRST_FAIL="${FIRST_FAIL:-$id}"
    printf '%-15s %-6s %s | HTTP=%s code=%s %s\n' "$id" "失败" "$name" "-" "$code" "$detail"
    return 1
  fi
  if [ "$need_meet" = "no" ]; then
    local saved_meet="$MEET_ID"; MEET_ID=""
    http_call "$method" "$url" "$body"
    MEET_ID="$saved_meet"
  else
    http_call "$method" "$url" "$body"
  fi
  code="$(json_code "$HTTP_BODY")"

  if [ "$HTTP_STATUS" != "200" ]; then
    ok="no"; detail="HTTP $HTTP_STATUS（期望 200）"
  elif [ "$accept" = "any" ]; then
    if [ -n "$code" ] && [ "$code" != "-" ]; then
      detail="code=$code（本用例允许业务错误码）"
    else
      ok="no"; detail="响应不是统一返回体（无 code 字段）"
    fi
  else
    local matched="no" a
    # 逗号分隔的可接受业务码列表
    local oldifs="$IFS"; IFS=','
    for a in $accept; do
      [ "$code" = "$a" ] && matched="yes"
    done
    IFS="$oldifs"
    if [ "$matched" = "yes" ]; then
      detail="HTTP 200 + code=$code"
    else
      ok="no"
      detail="HTTP 200 但 code=$code（期望 $accept）：$(printf '%s' "$HTTP_BODY" | tr -d '\n' | cut -c1-120)"
    fi
  fi

  TOTAL=$((TOTAL + 1))
  if [ "$ok" = "yes" ]; then
    PASS=$((PASS + 1))
    printf '%-15s %-6s %s | %s\n' "$id" "通过" "$name" "$detail"
  else
    FAIL=$((FAIL + 1)); FIRST_FAIL="${FIRST_FAIL:-$id}"
    printf '%-15s %-6s %s | %s\n' "$id" "失败" "$name" "$detail"
    [ "$KEEP_GOING" = "yes" ] || { echo; log "用例 $id 失败，按默认策略停止（加 --keep-going 可跑完全部用例）"; report_and_exit; }
  fi
  return 0
}

# 自定义断言用例（如前端 /health 的纯文本响应）
run_custom() {
  local id="$1" name="$2" ok="$3" detail="$4"
  TOTAL=$((TOTAL + 1))
  if [ "$ok" = "yes" ]; then
    PASS=$((PASS + 1)); printf '%-15s %-6s %s | %s\n' "$id" "通过" "$name" "$detail"
  else
    FAIL=$((FAIL + 1)); FIRST_FAIL="${FIRST_FAIL:-$id}"
    printf '%-15s %-6s %s | %s\n' "$id" "失败" "$name" "$detail"
    [ "$KEEP_GOING" = "yes" ] || { echo; log "用例 $id 失败，按默认策略停止（加 --keep-going 可跑完全部用例）"; report_and_exit; }
  fi
}

report_and_exit() {
  echo
  echo "-------------------------------------------------------------------------------"
  if [ "$FAIL" -eq 0 ]; then
    log "冒烟验收结果：通过 $PASS/$TOTAL，全部通过 ✔"
    exit 0
  fi
  log "冒烟验收结果：通过 $PASS/$TOTAL，失败条数 $FAIL（首个失败：$FIRST_FAIL）"
  echo "排查建议："
  echo "  1) 后端是否健康： curl $API_BASE/../actuator/health  （或 docker compose ps）"
  echo "  2) 后端日志：     docker compose logs -f --tail=200 backend"
  echo "  3) 登录 401：     确认后端已启动且 DefaultAccountInitializer 已初始化口令（首启 5~20 秒）"
  echo "                    口令见 .env 的 DEFAULT_ACCOUNT_PASSWORD（默认 Admin@123）"
  echo "  4) 403 无权限：   admin 账号权限由 sql/05 初始化；确认已执行 sql/07、08、09 补丁"
  echo "  5) 届次相关报错： 用 --meet-id 指定有效届次（当前使用 $MEET_ID）"
  exit 1
}

# ---------------------------------------------------------------------------
# 探活与登录
# ---------------------------------------------------------------------------
echo "==============================================================================="
log "TrackMeet 部署冒烟验收"
log "后端 API 根地址：$API_BASE"
log "前端根地址：    $WEB_BASE"
log "登录账号：      $USERNAME"
echo "==============================================================================="
printf '%-15s %-6s %s\n' '用例编号' '结果' '说明 / 断言'
echo "-------------------------------------------------------------------------------"

# 用例 01：后端健康检查（不鉴权、不解析统一返回体）
M1="$(curl -sS -m "$TIMEOUT" -w $'\n%{http_code}' "$API_BASE/../actuator/health" 2>/dev/null)" || M1=""
M1_STATUS="$(printf '%s' "$M1" | tail -n1 | tr -d '[:space:]')"
M1_BODY="$(printf '%s' "$M1" | sed '$d' | tr -d '\n')"
if [ "$M1_STATUS" = "200" ] && printf '%s' "$M1_BODY" | grep -q '"UP"'; then
  run_custom "TC-SMOKE-01" "后端健康检查 $API_BASE/../actuator/health" yes "HTTP 200 且 status=UP"
else
  run_custom "TC-SMOKE-01" "后端健康检查 $API_BASE/../actuator/health" no "HTTP=$M1_STATUS body=$(printf '%s' "$M1_BODY" | cut -c1-100)"
  report_and_exit
fi

# 用例 02：前端（Nginx）健康检查
W1="$(curl -sS -m "$TIMEOUT" -w $'\n%{http_code}' "$WEB_BASE/health" 2>/dev/null)" || W1=""
W1_STATUS="$(printf '%s' "$W1" | tail -n1 | tr -d '[:space:]')"
W1_BODY="$(printf '%s' "$W1" | sed '$d' | tr -d '\n' | tr -d '\r')"
if [ "$W1_STATUS" = "200" ] && printf '%s' "$W1_BODY" | grep -qi 'ok'; then
  run_custom "TC-SMOKE-02" "前端健康检查 $WEB_BASE/health" yes "HTTP 200 且返回 ok"
else
  run_custom "TC-SMOKE-02" "前端健康检查 $WEB_BASE/health" no "HTTP=$W1_STATUS body=$(printf '%s' "$W1_BODY" | cut -c1-100)（检查 WEB_PORT 与容器健康状态）"
fi

# 用例 03：登录获取令牌
LOGIN_BODY="{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}"
http_call POST "$API_BASE/auth/login" "$LOGIN_BODY"
LOGIN_CODE="$(json_code "$HTTP_BODY")"
TOKEN="$(json_token "$HTTP_BODY")"
if [ "$HTTP_STATUS" = "200" ] && [ "$LOGIN_CODE" = "0" ] && [ -n "$TOKEN" ]; then
  run_custom "TC-SMOKE-03" "登录 $USERNAME 获取 accessToken" yes "HTTP 200 + code=0 且令牌非空"
else
  run_custom "TC-SMOKE-03" "登录 $USERNAME 获取 accessToken" no "HTTP=$HTTP_STATUS code=$LOGIN_CODE body=$(printf '%s' "$HTTP_BODY" | tr -d '\n' | cut -c1-140)"
  echo "排查提示："
  echo "  1) 确认后端已启动并通过健康检查： docker compose ps / docker compose logs -f backend"
  echo "  2) 确认 DefaultAccountInitializer 已初始化口令（首启后 5~20 秒，日志含「口令已初始化」）"
  echo "  3) 确认账号口令： .env 的 DEFAULT_ACCOUNT_PASSWORD（默认 Admin@123）；如首登已改密请用 --password 指定"
  report_and_exit
fi

# 用例 04：届次列表（同时解析出后续用例使用的 X-Meet-Id）
http_call GET "$API_BASE/meets?page=1&size=10" -
MEETS_BODY="$HTTP_BODY"
if [ "$HTTP_STATUS" = "200" ] && [ "$(json_code "$MEETS_BODY")" = "0" ]; then
  run_custom "TC-SMOKE-04" "届次列表 GET /api/meets" yes "HTTP 200 + code=0"
else
  run_custom "TC-SMOKE-04" "届次列表 GET /api/meets" no "HTTP=$HTTP_STATUS code=$(json_code "$MEETS_BODY")"
fi

if [ -n "$ARG_MEET_ID" ]; then
  MEET_ID="$ARG_MEET_ID"
else
  MEET_ID="$(json_first_id "$MEETS_BODY")"
  [ -n "$MEET_ID" ] || MEET_ID="1"
fi
log "后续用例使用 X-Meet-Id：$MEET_ID（可用 --meet-id 覆盖）"
echo "-------------------------------------------------------------------------------"

# 用例 05~15：带 X-Meet-Id 的业务接口
run_case "TC-SMOKE-05" "单位列表 GET /api/units"                 GET  "$API_BASE/units?page=1&size=10"            - 0 yes yes
run_case "TC-SMOKE-06" "运动员分页 GET /api/athletes"            GET  "$API_BASE/athletes?page=1&size=10"         - 0 yes yes
run_case "TC-SMOKE-07" "项目分页 GET /api/events"                GET  "$API_BASE/events?page=1&size=10"           - 0 yes yes
run_case "TC-SMOKE-08" "报名统计 GET /api/registrations/statistics" GET "$API_BASE/registrations/statistics?dimension=UNIT" - 0 yes yes
run_case "TC-SMOKE-09" "竞赛单元分页 GET /api/sessions"          GET  "$API_BASE/sessions?page=1&size=10"         - 0 yes yes
run_case "TC-SMOKE-10" "日程分页 GET /api/schedule"              GET  "$API_BASE/schedule?page=1&size=10"         - 0 yes yes
run_case "TC-SMOKE-11" "成绩分页 GET /api/results"               GET  "$API_BASE/results?page=1&size=10"          - 0 yes yes
run_case "TC-SMOKE-12" "PDF 能力探测 GET /api/print/pdf/capability" GET "$API_BASE/print/pdf/capability"          - "0,50003" yes yes
run_case "TC-SMOKE-13" "报表清单 GET /api/print/reports"         GET  "$API_BASE/print/reports"                   - 0 yes no
run_case "TC-SMOKE-14" "大屏配置 GET /api/screen/config"         GET  "$API_BASE/screen/config?meetId=$MEET_ID"   - 0 yes yes
run_case "TC-SMOKE-15" "自动编排预览 POST /api/schedule/auto (apply=false)" POST "$API_BASE/schedule/auto" "{\"meetId\":$MEET_ID,\"apply\":false}" 0 yes yes

# 用例 16：整链路（前端 Nginx 反代 → 后端）登录
if [ "$WEB_BASE" != "$API_BASE" ]; then
  WLOGIN="$(curl -sS -m "$TIMEOUT" -X POST -w $'\n%{http_code}' \
    -H "Content-Type: application/json" \
    -d "$LOGIN_BODY" "$WEB_BASE/api/auth/login" 2>/dev/null)" || WLOGIN=""
  WLOGIN_STATUS="$(printf '%s' "$WLOGIN" | tail -n1 | tr -d '[:space:]')"
  WLOGIN_BODY="$(printf '%s' "$WLOGIN" | sed '$d')"
  WLOGIN_CODE="$(json_code "$WLOGIN_BODY")"
  if [ "$WLOGIN_STATUS" = "200" ] && [ "$WLOGIN_CODE" = "0" ] && [ -n "$(json_token "$WLOGIN_BODY")" ]; then
    run_custom "TC-SMOKE-16" "前端反代整链路 POST $WEB_BASE/api/auth/login" yes "HTTP 200 + code=0（Nginx /api 反代正常）"
  else
    run_custom "TC-SMOKE-16" "前端反代整链路 POST $WEB_BASE/api/auth/login" no "HTTP=$WLOGIN_STATUS code=$WLOGIN_CODE（检查 nginx/default.conf 的 /api/ 反代与 backend 健康）"
  fi
else
  log "跳过 TC-SMOKE-16（未配置前端地址，--web 可指定）"
fi

report_and_exit
