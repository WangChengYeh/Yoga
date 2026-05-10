#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCHDOG_SCRIPT="$PROJECT_ROOT/scripts/claude_rate_limit_watchdog.sh"
SOURCE_ME="$PROJECT_ROOT/sourceme"

CLI_COWORK_BRIDGE_SESSION="${CLI_COWORK_BRIDGE_SESSION:-yoga-ccb}"
CLI_COWORK_BRIDGE_CLONE_DIR="${CLI_COWORK_BRIDGE_CLONE_DIR:-$HOME/cli_cowork_bridge}"
CLI_COWORK_BRIDGE_REPO="https://github.com/WangChengYeh/CLI_Cowork_Bridge.git"
CLI_COWORK_BRIDGE_TAG="main"  # using main for latest fixes

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
  # Use npm with --prefix to avoid global sudo issues if configured in $HOME/.local
  npm install -g --prefix="$HOME/.local" --quiet @anthropic-ai/claude-code @google/gemini-cli @openai/codex
  echo "AI CLIs updated."
}

# Run AI CLI updates
update_ai_clis

# --- CLI_Cowork_Bridge Sync & iMessage Setup ---
setup_ccb_sync() {
  echo "Setting up CCB Sync and iMessage..."
  
  local recipient="${IMESSAGE_RECIPIENT:-+886978813320}"
  echo "  Using iMessage recipient: $recipient"

  if ! ccb imessage doctor | grep -q "platform_supported=True"; then
    echo "  WARNING: iMessage integration is not supported on this platform or needs Full Disk Access."
    return 0
  fi

  echo "  Stopping existing daemon if running..."
  ccb daemon stop >/dev/null 2>&1 || true

  echo "  Starting CCB daemon with iMessage sync..."
  ccb daemon start --imessage --recipients "$recipient"
  
  if ccb daemon status | grep -q "state=running"; then
    echo "  CCB daemon is running and synced."
  else
    echo "  WARNING: CCB daemon failed to start. Check .ccb/runtime-daemon.log"
  fi
}

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
  ccb update || true

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

# Initialize CCB Sync/iMessage
setup_ccb_sync

echo "Launching CLI_Cowork_Bridge (ccb)..."
exec ccb

cat <<EOF

Setup complete.

Attach session:
  tmux attach -t $CLI_COWORK_BRIDGE_SESSION        # CLI_Cowork_Bridge: cmd(Watchdog) + pm(Claude) + writer(Codex) + reviewer(Gemini)

Daemon & Sync:
  ccb daemon status      # Check sync health and PID
  ccb daemon stop        # Stop the background sync worker
  ccb imessage doctor    # Verify iMessage connectivity

List sessions:
  tmux ls

Agent delegation (inside CLI_Cowork_Bridge session):
  /ask pm <task>         → Claude PM orchestrates
  /ask writer <task>     → Codex implements
  /ask reviewer <task>   → Gemini reviews
  /ask cmd <command>     → Execute shell command / watchdog status

CLI_Cowork_Bridge clone location: $CLI_COWORK_BRIDGE_CLONE_DIR
EOF
