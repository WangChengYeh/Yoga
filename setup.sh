#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCHDOG_SCRIPT="$PROJECT_ROOT/scripts/claude_rate_limit_watchdog.sh"
SOURCE_ME="$PROJECT_ROOT/sourceme"

WATCHDOG_SESSION="${WATCHDOG_SESSION:-yoga-watchdog}"
CLAUDE_SESSION="${CLAUDE_SESSION:-yoga-claude}"
CCB_SESSION="${CCB_SESSION:-yoga-ccb}"
WATCHDOG_INTERVAL="${WATCHDOG_INTERVAL:-3600}"
CLAUDE_BOOT_WAIT_SECONDS="${CLAUDE_BOOT_WAIT_SECONDS:-2}"
CCB_CLONE_DIR="${CCB_CLONE_DIR:-$HOME/claude_codex_bridge}"
CCB_REPO="https://github.com/bfly123/claude_codex_bridge.git"
CCB_TAG="${CCB_TAG:-v6.0.29}"  # pin to stable release; override with CCB_TAG=vX.Y.Z

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

require_cmd tmux
require_cmd git

if [ ! -f "$SOURCE_ME" ]; then
  echo "Missing file: $SOURCE_ME" >&2
  exit 1
fi

if [ ! -x "$WATCHDOG_SCRIPT" ]; then
  echo "Missing executable watchdog script: $WATCHDOG_SCRIPT" >&2
  exit 1
fi

# --- CCB install (auto-clone if missing) ---
install_ccb() {
  echo "CCB not found — cloning and installing..."

  if [ -d "$CCB_CLONE_DIR/.git" ]; then
    echo "  Repo already exists at $CCB_CLONE_DIR — checking out stable tag $CCB_TAG..."
    git -C "$CCB_CLONE_DIR" fetch --tags
    git -C "$CCB_CLONE_DIR" checkout "$CCB_TAG"
  else
    echo "  Cloning $CCB_REPO at tag $CCB_TAG → $CCB_CLONE_DIR"
    git clone --branch "$CCB_TAG" --depth 1 "$CCB_REPO" "$CCB_CLONE_DIR"
  fi

  echo "  Running install.sh..."
  bash "$CCB_CLONE_DIR/install.sh" install

  echo "  Running ccb update..."
  ccb update

  echo "CCB installed successfully."
}

if ! command -v ccb >/dev/null 2>&1; then
  if command -v node >/dev/null 2>&1; then
    install_ccb
  else
    echo "WARNING: node not found — skipping CCB install." >&2
    echo "  Install Node.js, then re-run setup.sh." >&2
  fi
fi

# --- Watchdog ---
if ! tmux has-session -t "$WATCHDOG_SESSION" 2>/dev/null; then
  tmux new-session -d -s "$WATCHDOG_SESSION"
  tmux send-keys -t "$WATCHDOG_SESSION" "cd \"$PROJECT_ROOT\"" C-m
  tmux send-keys -t "$WATCHDOG_SESSION" "source \"$SOURCE_ME\"" C-m
  tmux send-keys -t "$WATCHDOG_SESSION" "\"$WATCHDOG_SCRIPT\" --daemon --interval \"$WATCHDOG_INTERVAL\"" C-m
  echo "Created tmux session: $WATCHDOG_SESSION (watchdog running)"
else
  echo "Session already exists: $WATCHDOG_SESSION (left unchanged)"
fi

# --- CCB agent workspace (writer:codex + reviewer:gemini) ---
if command -v ccb >/dev/null 2>&1; then
  if ! tmux has-session -t "$CCB_SESSION" 2>/dev/null; then
    tmux new-session -d -s "$CCB_SESSION"
    tmux send-keys -t "$CCB_SESSION" "cd \"$PROJECT_ROOT\"" C-m
    tmux send-keys -t "$CCB_SESSION" "source \"$SOURCE_ME\"" C-m
    tmux send-keys -t "$CCB_SESSION" "ccb" C-m
    echo "Created tmux session: $CCB_SESSION (CCB launched — writer:codex + reviewer:gemini)"
  else
    echo "Session already exists: $CCB_SESSION (left unchanged)"
  fi
else
  echo "CCB unavailable — skipping $CCB_SESSION session."
fi

# --- Claude Code PM session ---
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
  tmux attach -t $WATCHDOG_SESSION   # rate-limit watchdog
  tmux attach -t $CCB_SESSION        # CCB: writer(Codex) + reviewer(Gemini)
  tmux attach -t $CLAUDE_SESSION     # Claude Code PM

List sessions:
  tmux ls

Agent delegation (inside CCB session):
  /ask writer <task>     → Codex implements
  /ask reviewer <task>   → Gemini reviews

CCB clone location: $CCB_CLONE_DIR
EOF
