#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="$PROJECT_DIR/packages/frontend"
BACKEND_DIR="$PROJECT_DIR/packages/backend"
PIDFILE="$PROJECT_DIR/.sagemaker.pids"
BASE_PATH="/codeeditor/default/absports/3000"

export SAGEMAKER=1
export NEXT_PUBLIC_BASE_PATH="$BASE_PATH"

# SageMaker 環境では mise の Java (corretto-21) を明示的に指定する
# （ローカル環境にはこのパスが存在しないためスキップされる）
if [ -d /opt/mise/installs/java/corretto-21 ]; then
  export JAVA_HOME=/opt/mise/installs/java/corretto-21
fi

# setsid があれば各サービスを独立したプロセスグループで起動する
# （PID = プロセスグループ ID となり、子プロセスごと確実に kill できる）
SETSID="$(command -v setsid || true)"

# 指定ポートを listen しているプロセスを強制 kill する（最終防衛線）
# lsof はこの SageMaker 環境で IPv6 ソケットを検出できないため、
# /proc/net/tcp + tcp6 の local_address から inode → PID を逆引きする方式を併用する
kill_port() {
  local port=$1
  lsof -ti :"$port" 2>/dev/null | xargs -r kill -9 2>/dev/null || true

  [ -r /proc/net/tcp ] || return 0
  local hex_port inode pid_dir pid
  hex_port=$(printf '%04X' "$port")
  # $2 (local_address) のみをマッチ対象にする。行全体への grep だと
  # rem_address（接続元のポート）にも誤マッチするため
  for inode in $(awk -v p=":${hex_port}" '$2 ~ p"$" {print $10}' \
      /proc/net/tcp /proc/net/tcp6 2>/dev/null | sort -u); do
    # inode 0 は TIME_WAIT 等の所有プロセスなしソケット
    [ "$inode" = "0" ] && continue
    for pid_dir in /proc/[0-9]*/fd; do
      pid="${pid_dir#/proc/}"
      pid="${pid%/fd}"
      if find "$pid_dir" -lname "socket:\\[$inode\\]" -print -quit 2>/dev/null | grep -q .; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    done
  done
}

# PID ファイルに記録したプロセスグループを graceful に停止する
stop_pids() {
  [ -f "$PIDFILE" ] || return 0
  local pid
  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    # setsid 起動なら PID = PGID なのでグループごと kill。失敗時は単体 kill にフォールバック
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  done < "$PIDFILE"
  rm -f "$PIDFILE"
}

stop_all() {
  stop_pids
  # TERM での graceful shutdown を待ってから残存プロセスを強制 kill する
  sleep 2
  for port in 3000 3001 8080; do
    kill_port "$port"
  done
}

cleanup() {
  echo ""
  echo "=== Stopping SageMaker dev processes ==="
  stop_all
  echo "Done."
}

usage() {
  echo "Usage: $0 [command]"
  echo ""
  echo "Commands:"
  echo "  start   Build frontend & start all services (default)"
  echo "  stop    Stop all running services"
  echo "  restart Rebuild & restart all services"
  echo ""
  echo "Services started:"
  echo "  - Backend:  Spring Boot (workshop profile, H2) on :8080"
  echo "  - Frontend: Next.js on 127.0.0.1:3001"
  echo "  - Proxy:    SageMaker proxy :3000 → :3001"
}

do_start() {
  echo "=== Stopping previous session ==="
  stop_all

  echo ""
  echo "=== Building frontend ==="
  cd "$FRONTEND_DIR"
  if [ ! -d "node_modules" ]; then
    echo "node_modules not found. Running npm install..."
    npm install
  fi
  npx next build

  echo ""
  echo "=== Starting backend (workshop profile) ==="
  cd "$BACKEND_DIR"
  $SETSID ./gradlew bootRun --args='--spring.profiles.active=workshop' &
  BACKEND_PID=$!
  echo "$BACKEND_PID" > "$PIDFILE"
  echo "Backend PID: $BACKEND_PID"

  echo ""
  echo "=== Starting frontend (Next.js on 127.0.0.1:3001) ==="
  cd "$FRONTEND_DIR"
  # -H 127.0.0.1: IPv6 (::) バインドだと lsof で検出できず、外部露出も不要なため
  $SETSID npx next start -H 127.0.0.1 -p 3001 &
  FRONTEND_PID=$!
  echo "$FRONTEND_PID" >> "$PIDFILE"
  echo "Frontend PID: $FRONTEND_PID"

  echo ""
  echo "=== Starting SageMaker proxy (:3000 → :3001) ==="
  $SETSID node "$FRONTEND_DIR/scripts/sagemaker-proxy.mjs" &
  PROXY_PID=$!
  echo "$PROXY_PID" >> "$PIDFILE"
  echo "Proxy PID: $PROXY_PID"

  trap cleanup EXIT
  trap 'exit 130' INT TERM

  echo ""
  echo "=========================================="
  echo "  All services running!"
  echo "  Access: https://<studio-domain>${BASE_PATH}/"
  echo "  Press Ctrl+C to stop all services"
  echo "=========================================="
  echo ""

  wait
}

CMD="${1:-start}"

case "$CMD" in
  start)
    do_start
    ;;
  stop)
    stop_all
    echo "All services stopped."
    ;;
  restart)
    do_start
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown command: $CMD"
    usage
    exit 1
    ;;
esac
