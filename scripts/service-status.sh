#!/usr/bin/env bash
# Report whether a service is running and whether it is actually answering.
#
#   service-status.sh <name> <port>
#
# A live PID and a healthy endpoint are different claims. A JVM that is up but failing its
# health check is the interesting case, so report both rather than collapsing them.
set -uo pipefail

NAME="${1:?service name required}"
PORT="${2:?port required}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/logs/$NAME.pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  PROC="up (pid $(cat "$PID_FILE"))"
else
  PROC="down"
fi

if curl -sf -m 2 "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
  HEALTH="healthy"
else
  HEALTH="not answering"
fi

printf '%-12s %-22s %s\n' "$NAME" "$PROC" "$HEALTH"
