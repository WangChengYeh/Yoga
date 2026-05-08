#!/usr/bin/env bash
set -euo pipefail

# Hourly hook: greet other agents and append to logs/agent_greetings.log.
# Safe to call frequently; it only emits once per hour.

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
LOG_DIR="$PROJECT_ROOT/logs"
LOG_FILE="$LOG_DIR/agent_greetings.log"
STAMP_FILE="$LOG_DIR/agent_greetings.last"
ONE_HOUR=3600
SELF_AGENT="${SELF_AGENT:-codex}"

mkdir -p "$LOG_DIR"

NOW="$(date +%s)"
if [ -f "$STAMP_FILE" ]; then
  LAST_RUN="$(cat "$STAMP_FILE" 2>/dev/null || echo 0)"
  ELAPSED=$((NOW - LAST_RUN))
  if [ "$ELAPSED" -lt "$ONE_HOUR" ]; then
    exit 0
  fi
fi

TS="$(date +"%Y-%m-%d %H:%M:%S")"

for agent_file in "$PROJECT_ROOT/AGENTS.md" "$PROJECT_ROOT/GEMINI.md"; do
  [ -f "$agent_file" ] || continue
  agent_name="$(sed -n '1s/^# \(.*\) Agent$/\1/p' "$agent_file" | tr '[:upper:]' '[:lower:]')"
  [ -n "$agent_name" ] || continue
  [ "$agent_name" = "$SELF_AGENT" ] && continue
  echo "[$TS] Hello, ${agent_name}!" >> "$LOG_FILE"
done

echo "$NOW" > "$STAMP_FILE"
