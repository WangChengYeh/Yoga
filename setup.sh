#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCHDOG_SCRIPT="$PROJECT_ROOT/scripts/claude_rate_limit_watchdog.sh"
SOURCE_ME="$PROJECT_ROOT/sourceme"

WATCHDOG_SESSION="${WATCHDOG_SESSION:-yoga-watchdog}"
CLAUDE_SESSION="${CLAUDE_SESSION:-yoga-claude}"
WATCHDOG_INTERVAL="${WATCHDOG_INTERVAL:-3600}"
CLAUDE_BOOT_WAIT_SECONDS="${CLAUDE_BOOT_WAIT_SECONDS:-2}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

require_cmd tmux

if [ ! -f "$SOURCE_ME" ]; then
  echo "Missing file: $SOURCE_ME" >&2
  exit 1
fi

if [ ! -x "$WATCHDOG_SCRIPT" ]; then
  echo "Missing executable watchdog script: $WATCHDOG_SCRIPT" >&2
  exit 1
fi

if ! tmux has-session -t "$WATCHDOG_SESSION" 2>/dev/null; then
  tmux new-session -d -s "$WATCHDOG_SESSION"
  tmux send-keys -t "$WATCHDOG_SESSION" "cd \"$PROJECT_ROOT\"" C-m
  tmux send-keys -t "$WATCHDOG_SESSION" "source \"$SOURCE_ME\"" C-m
  tmux send-keys -t "$WATCHDOG_SESSION" "\"$WATCHDOG_SCRIPT\" --daemon --interval \"$WATCHDOG_INTERVAL\"" C-m
  echo "Created tmux session: $WATCHDOG_SESSION (watchdog running)"
else
  echo "Session already exists: $WATCHDOG_SESSION (left unchanged)"
fi

if ! tmux has-session -t "$CLAUDE_SESSION" 2>/dev/null; then
  tmux new-session -d -s "$CLAUDE_SESSION"
  tmux send-keys -t "$CLAUDE_SESSION" "cd \"$PROJECT_ROOT\"" C-m
  tmux send-keys -t "$CLAUDE_SESSION" "source \"$SOURCE_ME\"" C-m
  tmux send-keys -t "$CLAUDE_SESSION" "claude" C-m
  sleep "$CLAUDE_BOOT_WAIT_SECONDS"
  tmux send-keys -t "$CLAUDE_SESSION" "go project" C-m
  echo "Created tmux session: $CLAUDE_SESSION (claude launched)"
else
  tmux send-keys -t "$CLAUDE_SESSION" "go project" C-m
  echo "Session already exists: $CLAUDE_SESSION (sent: go project)"
fi

cat <<EOF

Setup complete.

Attach sessions:
  tmux attach -t $WATCHDOG_SESSION
  tmux attach -t $CLAUDE_SESSION

List sessions:
  tmux ls
EOF
