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

## Hourly Agent Hello Hook

`scripts/codex_hourly_agent_hello.sh` writes hourly greetings to `logs/agent_greetings.log`.

- It is safe to run repeatedly; it only emits once per hour (`logs/agent_greetings.last` cooldown file).
- `scripts/claude-stop-hook.sh` invokes it automatically with `SELF_AGENT=claude`, so Claude greets peer agents (for example: Codex/Gemini).
