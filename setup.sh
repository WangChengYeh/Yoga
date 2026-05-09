#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCHDOG_SCRIPT="$PROJECT_ROOT/scripts/claude_rate_limit_watchdog.sh"
SOURCE_ME="$PROJECT_ROOT/sourceme"

WATCHDOG_SESSION="${WATCHDOG_SESSION:-yoga-watchdog}"
CLI_COWORK_BRIDGE_SESSION="${CLI_COWORK_BRIDGE_SESSION:-yoga-ccb}"
WATCHDOG_INTERVAL="${WATCHDOG_INTERVAL:-3600}"
CLI_COWORK_BRIDGE_CLONE_DIR="${CLI_COWORK_BRIDGE_CLONE_DIR:-$HOME/cli_cowork_bridge}"
CLI_COWORK_BRIDGE_REPO="https://github.com/WangChengYeh/CLI_Cowork_Bridge.git"
CLI_COWORK_BRIDGE_TAG="v7.1.0"  # pin to stable release; override with CLI_COWORK_BRIDGE_TAG=vX.Y.Z

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

# --- CLI_Cowork_Bridge install (auto-clone if missing) ---
install_cli_cowork_bridge() {
  local target_tag="$CLI_COWORK_BRIDGE_TAG"
  
  if [ ! -d "$CLI_COWORK_BRIDGE_CLONE_DIR/.git" ]; then
    echo "CLI_Cowork_Bridge not found — cloning..."
    echo "  Cloning $CLI_COWORK_BRIDGE_REPO at tag $target_tag → $CLI_COWORK_BRIDGE_CLONE_DIR"
    git clone --branch "$target_tag" --depth 1 "$CLI_COWORK_BRIDGE_REPO" "$CLI_COWORK_BRIDGE_CLONE_DIR"
  else
    local current_tag
    current_tag=$(git -C "$CLI_COWORK_BRIDGE_CLONE_DIR" describe --tags 2>/dev/null || echo "unknown")
    
    if [ "$current_tag" != "$target_tag" ]; then
      echo "CLI_Cowork_Bridge version mismatch (current: $current_tag, target: $target_tag) — upgrading..."
      git -C "$CLI_COWORK_BRIDGE_CLONE_DIR" fetch --tags
      git -C "$CLI_COWORK_BRIDGE_CLONE_DIR" checkout "$target_tag"
    else
      echo "CLI_Cowork_Bridge is already at target version $target_tag."
      return 0
    fi
  fi

  echo "  Running install.sh..."
  bash "$CLI_COWORK_BRIDGE_CLONE_DIR/install.sh" install

  echo "  Running ccb update..."
  #ccb update

  echo "CLI_Cowork_Bridge setup complete."
}

if ! command -v ccb >/dev/null 2>&1 || [ "$(git -C "$CLI_COWORK_BRIDGE_CLONE_DIR" describe --tags 2>/dev/null)" != "$CLI_COWORK_BRIDGE_TAG" ]; then
  if command -v node >/dev/null 2>&1; then
    install_cli_cowork_bridge
  else
    if ! command -v ccb >/dev/null 2>&1; then
      echo "ERROR: node not found — cannot install CLI_Cowork_Bridge." >&2
      echo "  Install Node.js, then re-run setup.sh." >&2
      exit 1
    fi
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

# --- CLI_Cowork_Bridge workspace: pm:claude + writer:codex + reviewer:gemini ---
if ! tmux has-session -t "$CLI_COWORK_BRIDGE_SESSION" 2>/dev/null; then
  tmux new-session -d -s "$CLI_COWORK_BRIDGE_SESSION"
  tmux send-keys -t "$CLI_COWORK_BRIDGE_SESSION" "cd \"$PROJECT_ROOT\"" C-m
  tmux send-keys -t "$CLI_COWORK_BRIDGE_SESSION" "source \"$SOURCE_ME\"" C-m
  tmux send-keys -t "$CLI_COWORK_BRIDGE_SESSION" "ccb" C-m
  echo "Created tmux session: $CLI_COWORK_BRIDGE_SESSION (CLI_Cowork_Bridge launched — pm:claude + writer:codex + reviewer:gemini)"
else
  echo "Session already exists: $CLI_COWORK_BRIDGE_SESSION (left unchanged)"
fi

cat <<EOF

Setup complete.

Attach sessions:
  tmux attach -t $WATCHDOG_SESSION   # rate-limit watchdog
  tmux attach -t $CLI_COWORK_BRIDGE_SESSION        # CLI_Cowork_Bridge: pm(Claude) + writer(Codex) + reviewer(Gemini)

List sessions:
  tmux ls

Agent delegation (inside CLI_Cowork_Bridge session):
  /ask pm <task>         → Claude PM orchestrates
  /ask writer <task>     → Codex implements
  /ask reviewer <task>   → Gemini reviews

CLI_Cowork_Bridge clone location: $CLI_COWORK_BRIDGE_CLONE_DIR
EOF
