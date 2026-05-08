#!/usr/bin/env bash
# Claude Code Stop hook — auto-continue project work when Claude goes idle.
#
# Behaviour (checked in order):
#   1. stop_hook_active == true  → exit silently (prevent re-injection loop)
#   2. Staged uncommitted changes  → inject "continue current task"
#   3. Open GitHub issues          → inject "run triage loop"
#   4. No issues, < 1 h elapsed    → exit silently (Claude stays idle)
#   5. No issues, ≥ 1 h elapsed    → inject "re-check for new issues"
#
# Install: referenced from .claude/settings.json Stop hook.
# Output format: {"decision":"block","reason":"..."} — blocks Claude from stopping and
# injects the reason as context, causing Claude to continue working.

REPO="WangChengYeh/Yoga"
IDLE_STAMP="/tmp/claude-yogaflow-idle-since"
ONE_HOUR=3600
ROLE="You are the project manager for YogaFlow 3D. You do not implement directly — you orchestrate Codex and Gemini CLI. Delegate all implementation to agents, review their output, and keep the project moving."
HOOK_LOGGER="$(cd "$(dirname "$0")" && pwd)/hook_event_logger.sh"

# ── 0. Read hook input ───────────────────────────────────────────────────────
INPUT=$(cat)
HOOK_ACTIVE=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(str(d.get('stop_hook_active',False)).lower())" 2>/dev/null || echo "false")

emit() {
  local msg="$1"
  python3 -c "
import json, sys
print(json.dumps({'decision': 'block', 'reason': sys.argv[1]}))" "$msg"
}

log_event() {
  local event="$1"
  local detail="${2:-}"
  if [ -x "$HOOK_LOGGER" ]; then
    "$HOOK_LOGGER" "$event" "$detail" "claude-stop-hook" >/dev/null 2>&1 || true
  fi
}

# ── 1. In-progress work (staged but uncommitted) ─────────────────────────────
if ! git diff --cached --quiet 2>/dev/null; then
  log_event "staged_changes" "Continue current task due to staged changes"
  emit "$ROLE Staged uncommitted changes exist in the repo. Continue the current task to completion, then commit."
  exit 0
fi

# ── 2. Open GitHub issues — always block, even if stop_hook_active ───────────
ISSUE_COUNT=$(GITHUB_TOKEN="" gh issue list \
  --repo "$REPO" --state open --limit 1 --json number 2>/dev/null \
  | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null \
  || echo 0)

if [ "$ISSUE_COUNT" -gt 0 ]; then
  rm -f "$IDLE_STAMP"
  log_event "open_issues" "Open issues found in $REPO"
  emit "$ROLE Open GitHub issues found in $REPO. Run the project triage loop per CLAUDE.md: fetch open issues, pick the highest-priority one, delegate to Codex or Gemini, commit, and comment on the issue."
  exit 0
fi

# ── 3. Nothing to do — only now respect stop_hook_active to prevent re-injection loop
if [ "$HOOK_ACTIVE" = "true" ]; then
  log_event "hook_active_skip" "stop_hook_active=true"
  exit 0
fi

# ── 4. No open issues — enforce 1-hour cooldown ──────────────────────────────
NOW=$(date +%s)

if [ -f "$IDLE_STAMP" ]; then
  ELAPSED=$(( NOW - $(cat "$IDLE_STAMP") ))
  if [ "$ELAPSED" -lt "$ONE_HOUR" ]; then
    log_event "cooldown_skip" "elapsed=${ELAPSED}s"
    exit 0
  fi
fi

echo "$NOW" > "$IDLE_STAMP"
log_event "cooldown_elapsed_recheck" "elapsed>=${ONE_HOUR}s"
emit "$ROLE All issues in $REPO are closed. 1-hour cooldown elapsed — re-checking for newly filed issues. If still none, go idle and wait for the user."
exit 0
