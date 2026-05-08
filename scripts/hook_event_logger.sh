#!/usr/bin/env bash
set -euo pipefail

DB_PATH="${HOOK_LOG_DB_PATH:-logs/agent_comms.db}"
EVENT="${1:-}"
DETAIL="${2:-}"
HOOK_NAME="${3:-claude-stop-hook}"

if [ -z "$EVENT" ]; then
  echo "usage: $0 <event> [detail] [hook_name]" >&2
  exit 2
fi

mkdir -p "$(dirname "$DB_PATH")"

python3 - "$DB_PATH" "$HOOK_NAME" "$EVENT" "$DETAIL" <<'PY'
import sqlite3
import sys
import time

db_path, hook_name, event, detail = sys.argv[1:5]
ts_ms = int(time.time() * 1000)

with sqlite3.connect(db_path) as conn:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS hook_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts_ms INTEGER NOT NULL,
            hook_name TEXT NOT NULL,
            event TEXT NOT NULL,
            detail TEXT NOT NULL
        )
        """
    )
    conn.execute(
        "INSERT INTO hook_events (ts_ms, hook_name, event, detail) VALUES (?, ?, ?, ?)",
        (ts_ms, hook_name, event, detail),
    )
    conn.commit()
PY
