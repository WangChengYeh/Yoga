#!/usr/bin/env bash
# Claude Code Stop hook — auto-continue project work when Claude goes idle.
#
# Behaviour (checked in order):
#   1. Staged uncommitted changes  → inject "continue current task"
#   2. Open GitHub issues          → inject "run triage loop"
#   3. No issues, < 1 h elapsed    → exit silently (Claude stays idle)
#   4. No issues, ≥ 1 h elapsed    → inject "re-check for new issues"
#
# Install: referenced from .claude/settings.json Stop hook.

REPO="WangChengYeh/Yoga"
IDLE_STAMP="/tmp/claude-yogaflow-idle-since"
ONE_HOUR=3600

# ── 1. In-progress work (staged but uncommitted) ─────────────────────────────
if ! git diff --cached --quiet 2>/dev/null; then
  echo "Staged uncommitted changes exist in the repo. Continue the current task to completion, then commit."
  exit 0
fi

# ── 2. Open GitHub issues ─────────────────────────────────────────────────────
ISSUE_COUNT=$(GITHUB_TOKEN="" gh issue list \
  --repo "$REPO" --state open --limit 1 --json number 2>/dev/null \
  | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null \
  || echo 0)

if [ "$ISSUE_COUNT" -gt 0 ]; then
  rm -f "$IDLE_STAMP"
  echo "Open GitHub issues found in $REPO. Run the project triage loop per CLAUDE.md: fetch open issues, pick the highest-priority one, delegate to Codex or Gemini, commit, and comment on the issue."
  exit 0
fi

# ── 3/4. No open issues — enforce 1-hour cooldown ────────────────────────────
NOW=$(date +%s)

if [ -f "$IDLE_STAMP" ]; then
  ELAPSED=$(( NOW - $(cat "$IDLE_STAMP") ))
  if [ "$ELAPSED" -lt "$ONE_HOUR" ]; then
    # Still within cooldown — stay silent, let Claude idle
    exit 0
  fi
fi

# First run or cooldown expired — update stamp and wake Claude
echo "$NOW" > "$IDLE_STAMP"
echo "All issues in $REPO are closed. 1-hour cooldown elapsed — re-checking for newly filed issues. If still none, go idle and wait for the user."
exit 0
