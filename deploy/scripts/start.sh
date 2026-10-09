#!/usr/bin/env bash
# ============================================================================
# TrackMeet 一键启动脚本（Linux / macOS）
# 用法： bash deploy/scripts/start.sh [up|down|restart|logs|ps]
# ============================================================================
set -euo pipefail

ACTION="${1:-up}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

if [ ! -f "$DEPLOY_DIR/.env" ]; then
  cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
  echo "已根据 .env.example 生成 $DEPLOY_DIR/.env，请按需修改后重新执行。"
fi

cd "$DEPLOY_DIR"

case "$ACTION" in
  up)      docker compose up -d --build ;;
  down)    docker compose down ;;
  restart) docker compose restart ;;
  logs)    docker compose logs -f --tail=200 ;;
  ps)      docker compose ps ;;
  *)       echo "用法: $0 [up|down|restart|logs|ps]"; exit 1 ;;
esac

if [ "$ACTION" = "up" ]; then
  sleep 10
  docker compose ps
  echo
  echo "后端健康检查: http://localhost:8080/actuator/health"
  echo "默认账号: admin / Admin@123（首次登录请立即修改）"
  echo "首次启动会自动执行 sql/01~06 脚本，MySQL 初始化约需 30-60 秒。"
fi
