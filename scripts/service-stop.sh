#!/usr/bin/env bash
# Stop a service started by service-start.sh.
#
#   service-stop.sh <name>
#
# Exits 0 when the service is already stopped: `make down` should be idempotent, not a way to
# discover that something was not running.
set -euo pipefail

NAME="${1:?service name required}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/logs/$NAME.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "$NAME not running"
  exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID" 2>/dev/null || true
  for _ in $(seq 1 20); do
    kill -0 "$PID" 2>/dev/null || break
    sleep 0.5
  done
  # Only escalate if it ignored the polite request.
  if kill -0 "$PID" 2>/dev/null; then
    echo "$NAME did not stop gracefully; forcing"
    kill -9 "$PID" 2>/dev/null || true
  fi
  echo "$NAME stopped (pid $PID)"
else
  echo "$NAME not running (stale pid file)"
fi

rm -f "$PID_FILE"
