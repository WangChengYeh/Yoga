#!/usr/bin/env bash
# Test Claude→Gemini A2A (ACP) access. Runs three checks:
#   1. gemini CLI reachable
#   2. ACP handshake + model switch (plan mode, no edits)
#   3. Auto-fallback path (forces fallback model directly)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ACP="python3 $SCRIPT_DIR/gemini-acp.py"
PASS=0
FAIL=0

ok()   { echo "  [PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $*"; FAIL=$((FAIL + 1)); }

echo "=== Claude→Gemini A2A test suite ==="
echo

# ── 1. CLI reachable ────────────────────────────────────────────────────────
echo "1. Gemini CLI reachable"
if gemini --version &>/dev/null; then
  ok "gemini $(gemini --version)"
else
  fail "gemini CLI not found on PATH"
fi

# ── 2. ACP round-trip (auto model with fallback) ────────────────────────────
echo "2. ACP round-trip (plan mode, auto fallback)"
RESPONSE=$($ACP "Reply with exactly: PONG" --mode plan 2>/tmp/acp-stderr.txt) || true
STDERR=$(cat /tmp/acp-stderr.txt)

if echo "$RESPONSE" | grep -qi "pong"; then
  MODEL=$(echo "$STDERR" | grep '^\[model\]' | tail -1 | awk '{print $2}')
  SESSION=$(echo "$STDERR" | grep '^\[session\]' | tail -1 | awk '{print $2}')
  ok "Got PONG via $MODEL | session=$SESSION"
elif echo "$STDERR" | grep -qi "quota.*retrying"; then
  ok "Quota fallback triggered (auto-gemini-3 path exercised)"
  # Re-check response after fallback
  if echo "$RESPONSE" | grep -qi "pong"; then
    ok "Fallback response correct"
  else
    fail "Fallback response missing PONG: '$RESPONSE'"
  fi
else
  fail "No PONG in response. stderr: $STDERR"
fi

# ── 3. Direct fallback model ─────────────────────────────────────────────────
echo "3. Direct fallback model (auto-gemini-3)"
RESPONSE2=$($ACP "Reply with exactly: PONG" --mode plan --model auto-gemini-3 2>/tmp/acp-stderr2.txt) || true
STDERR2=$(cat /tmp/acp-stderr2.txt)

if echo "$RESPONSE2" | grep -qi "pong"; then
  SESSION2=$(echo "$STDERR2" | grep '^\[session\]' | tail -1 | awk '{print $2}')
  ok "auto-gemini-3 responded | session=$SESSION2"
else
  fail "auto-gemini-3 did not respond with PONG. stderr: $STDERR2"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo
echo "=== Results: $PASS passed, $FAIL failed ==="
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
