#!/usr/bin/env bash
set -euo pipefail

# Detect Claude "/rate-limit-options" prompt and send ESC to dismiss it.
# Scans all tmux panes by default.
#
# Examples:
#   scripts/claude_rate_limit_watchdog.sh --once
#   scripts/claude_rate_limit_watchdog.sh --daemon --interval 3600
#   scripts/claude_rate_limit_watchdog.sh --once --pane %3

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
LOG_DIR="$PROJECT_ROOT/logs"
LOG_FILE="$LOG_DIR/claude_rate_limit_watchdog.log"

CHECK_INTERVAL=3600
RUN_ONCE=false
TARGET_PANE=""
PATTERN="/rate-limit-options"

mkdir -p "$LOG_DIR"

usage() {
  cat <<'EOF'
Usage:
  claude_rate_limit_watchdog.sh [--once] [--daemon] [--interval SECONDS] [--pane PANE_ID]

Options:
  --once               Run one check and exit.
  --daemon             Keep checking forever (default if --once not supplied).
  --interval SECONDS   Check frequency in seconds (default: 3600).
  --pane PANE_ID       Explicit tmux pane id (for example: %3).
  -h, --help           Show help.
EOF
}

log() {
  local msg="$1"
  local ts
  ts="$(date +"%Y-%m-%d %H:%M:%S")"
  echo "[$ts] $msg" | tee -a "$LOG_FILE"
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --once)
        RUN_ONCE=true
        ;;
      --daemon)
        RUN_ONCE=false
        ;;
      --interval)
        shift
        CHECK_INTERVAL="${1:-}"
        ;;
      --pane)
        shift
        TARGET_PANE="${1:-}"
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        echo "Unknown argument: $1" >&2
        usage
        exit 1
        ;;
    esac
    shift
  done
}

require_tmux() {
  if ! command -v tmux >/dev/null 2>&1; then
    log "tmux not found; cannot monitor terminal output."
    exit 1
  fi
}

find_candidate_panes() {
  if [ -n "$TARGET_PANE" ]; then
    echo "$TARGET_PANE"
    return
  fi

  tmux list-panes -a -F '#{pane_id}'
}

check_pane_and_dismiss() {
  local pane="$1"
  local content

  if ! content="$(tmux capture-pane -p -t "$pane" -S -200 2>/dev/null)"; then
    log "pane $pane unavailable; skipping."
    return 1
  fi

  if echo "$content" | rg -Fq "$PATTERN"; then
    tmux send-keys -t "$pane" Escape
    log "detected '$PATTERN' in $pane; sent ESC."
    return 0
  fi

  log "no rate-limit prompt in $pane."
  return 1
}

run_check() {
  local panes
  local hit=false

  panes="$(find_candidate_panes || true)"
  if [ -z "$panes" ]; then
    log "no candidate tmux panes found."
    return 1
  fi

  while IFS= read -r pane; do
    [ -n "$pane" ] || continue
    if check_pane_and_dismiss "$pane"; then
      hit=true
    fi
  done <<EOF
$panes
EOF

  if [ "$hit" = true ]; then
    return 0
  fi
  return 1
}

main() {
  parse_args "$@"
  require_tmux

  if [ "$RUN_ONCE" = true ]; then
    run_check || true
    exit 0
  fi

  log "watchdog started; interval=${CHECK_INTERVAL}s pattern='$PATTERN'."
  while true; do
    run_check || true
    sleep "$CHECK_INTERVAL"
  done
}

main "$@"
