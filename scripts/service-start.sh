#!/usr/bin/env bash
# Start a Spring Boot service in the background.
#
#   service-start.sh <name> <dir> <port>
#
# Runs the built jar rather than `mvnw spring-boot:run` on purpose: the jar gives one process
# whose PID we can actually record and kill. The Maven wrapper forks a child JVM, so its PID
# is not the thing you need to stop, and `make stop` would leave the port held.
set -euo pipefail

NAME="${1:?service name required}"
DIR="${2:?service directory required}"
PORT="${3:?port required}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/logs"
LOG="$LOG_DIR/$NAME.log"
PID_FILE="$LOG_DIR/$NAME.pid"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "$NAME already running (pid $(cat "$PID_FILE"))"
  exit 0
fi

JAR="$(ls -t "$ROOT/$DIR"/target/*.jar 2>/dev/null | grep -v -- '-sources\|-javadoc' | head -1 || true)"
if [ -z "$JAR" ]; then
  echo "No jar in $DIR/target - run 'make build' first" >&2
  exit 1
fi

echo "Starting $NAME on :$PORT  ($(basename "$JAR"))"
nohup "${JAVA_HOME:-}/bin/java" \
  -jar "$JAR" \
  --server.port="$PORT" \
  --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-local}" \
  > "$LOG" 2>&1 &

echo $! > "$PID_FILE"
echo "$NAME started (pid $(cat "$PID_FILE")) - logs: logs/$NAME.log"
