#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCHDOG_SCRIPT="$PROJECT_ROOT/scripts/claude_rate_limit_watchdog.sh"
SOURCE_ME="$PROJECT_ROOT/sourceme"

WATCHDOG_SESSION="${WATCHDOG_SESSION:-yoga-watchdog}"
CLI_BRIDGE_SESSION="${CLI_BRIDGE_SESSION:-yoga-ccb}"
WATCHDOG_INTERVAL="${WATCHDOG_INTERVAL:-3600}"
CLI_BRIDGE_CLONE_DIR="${CLI_BRIDGE_CLONE_DIR:-$HOME/cli_bridge}"
CLI_BRIDGE_REPO="https://github.com/WangChengYeh/CLI_Bridge.git"
CLI_BRIDGE_TAG="v7.1.0"  # pin to stable release; override with CLI_BRIDGE_TAG=vX.Y.Z

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

require_cmd tmux
require_cmd git
require_cmd node
require_cmd npm

if [ ! -f "$SOURCE_ME" ]; then
  echo "Missing file: $SOURCE_ME" >&2
  exit 1
fi

if [ ! -x "$WATCHDOG_SCRIPT" ]; then
  echo "Missing executable watchdog script: $WATCHDOG_SCRIPT" >&2
  exit 1
fi

# --- AI CLI updates ---
update_ai_clis() {
  echo "Checking and updating AI CLIs (claude, gemini, codex) in $HOME/.local..."
  mkdir -p "$HOME/.local"
  npm install -g --prefix="$HOME/.local" --quiet @anthropic-ai/claude-code @google/gemini-cli @openai/codex
  echo "AI CLIs updated."
}

# Run AI CLI updates
update_ai_clis

# --- CLI_Bridge install (auto-clone if missing) ---
install_cli_bridge() {
  echo "CLI_Bridge not found — cloning and installing..."

  if [ -d "$CLI_BRIDGE_CLONE_DIR/.git" ]; then
    echo "  Repo already exists at $CLI_BRIDGE_CLONE_DIR — checking out stable tag $CLI_BRIDGE_TAG..."
    git -C "$CLI_BRIDGE_CLONE_DIR" fetch --tags
    git -C "$CLI_BRIDGE_CLONE_DIR" checkout "$CLI_BRIDGE_TAG"
  else
    echo "  Cloning $CLI_BRIDGE_REPO at tag $CLI_BRIDGE_TAG → $CLI_BRIDGE_CLONE_DIR"
    git clone --branch "$CLI_BRIDGE_TAG" --depth 1 "$CLI_BRIDGE_REPO" "$CLI_BRIDGE_CLONE_DIR"
  fi

  echo "  Running install.sh..."
  bash "$CLI_BRIDGE_CLONE_DIR/install.sh" install

  echo "  Running ccb update..."
  ccb update

  echo "CLI_Bridge installed successfully."
}

if ! command -v ccb >/dev/null 2>&1; then
  if command -v node >/dev/null 2>&1; then
    install_cli_bridge
  else
    echo "ERROR: node not found — cannot install CLI_Bridge." >&2
    echo "  Install Node.js, then re-run setup.sh." >&2
    exit 1
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

# --- CLI_Bridge workspace: pm:claude + writer:codex + reviewer:gemini ---
if ! tmux has-session -t "$CLI_BRIDGE_SESSION" 2>/dev/null; then
  tmux new-session -d -s "$CLI_BRIDGE_SESSION"
  tmux send-keys -t "$CLI_BRIDGE_SESSION" "cd \"$PROJECT_ROOT\"" C-m
  tmux send-keys -t "$CLI_BRIDGE_SESSION" "source \"$SOURCE_ME\"" C-m
  tmux send-keys -t "$CLI_BRIDGE_SESSION" "ccb" C-m
  echo "Created tmux session: $CLI_BRIDGE_SESSION (CLI_Bridge launched — pm:claude + writer:codex + reviewer:gemini)"
else
  echo "Session already exists: $CLI_BRIDGE_SESSION (left unchanged)"
fi

cat <<EOF

Setup complete.

Attach sessions:
  tmux attach -t $WATCHDOG_SESSION   # rate-limit watchdog
  tmux attach -t $CLI_BRIDGE_SESSION        # CLI_Bridge: pm(Claude) + writer(Codex) + reviewer(Gemini)

List sessions:
  tmux ls

Agent delegation (inside CLI_Bridge session):
  /ask pm <task>         → Claude PM orchestrates
  /ask writer <task>     → Codex implements
  /ask reviewer <task>   → Gemini reviews

CLI_Bridge clone location: $CLI_BRIDGE_CLONE_DIR
EOF