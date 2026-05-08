# Scripts

## Agent Communication Logger

Log Claude/Codex/Gemini agent communications to a local SQLite database (`logs/agent_comms.db`).

```bash
python3 scripts/agent_comm_logger.py send   --from claude --to codex  --session sess-2026-05-08-01 --issue "#71" --message "Implement sqlite logger"
python3 scripts/agent_comm_logger.py recv   --from codex  --to claude --session sess-2026-05-08-01 --issue "#71" --message "Implemented + verified" --elapsed-ms 45000
python3 scripts/agent_comm_logger.py report --date 2026-05-08 --session sess-2026-05-08-01
python3 scripts/agent_comm_logger.py stats  --days 7
```

## Export SQLite Logs to CSV

Export the latest `X` rows from a SQLite table to CSV:

```bash
python3 scripts/export_sqlite_logs_csv.py --limit 100
```

Options:
- `--db` (default: `logs/agent_comms.db`)
- `--table` (default: `agent_comms`)
- `--out` (default: `logs/agent_comms_latest.csv`)
- `--newest-first` (default output is oldest→newest among selected latest rows)

## Hook Event Logging (Bash)

`scripts/claude-stop-hook.sh` now records hook decisions into the same SQLite DB (`logs/agent_comms.db`) under table `hook_events`.

Manual test:

```bash
scripts/hook_event_logger.sh test_event "manual verification"
python3 scripts/export_sqlite_logs_csv.py --table hook_events --limit 10 --out logs/hook_events_latest.csv
```

## Claude Rate-Limit Watchdog

Detect `/rate-limit-options` in tmux panes and send `ESC` automatically.

```bash
# One-time check
scripts/claude_rate_limit_watchdog.sh --once

# Hourly background watchdog (default interval = 3600s)
scripts/claude_rate_limit_watchdog.sh --daemon --interval 3600

# Target a specific pane
scripts/claude_rate_limit_watchdog.sh --daemon --pane %3
```

Notes:
- Requires `tmux`.
- Scans all tmux panes by default; use `--pane` to limit to one pane.
- Logs to `logs/claude_rate_limit_watchdog.log`.
- Pattern match is literal `/rate-limit-options`.
- `scripts/claude-stop-hook.sh` invokes `scripts/claude_rate_limit_watchdog.sh --once` automatically as a best-effort pre-check on each stop-hook run.
