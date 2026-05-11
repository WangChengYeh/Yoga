# Scripts

## Testing

### Full regression (Gradle + APK + on-device)

Runs unit tests, builds the debug APK, installs it, checks the Godot bridge port,
sweeps the avatar through positions, and verifies no crashes. Run from the repo root.

```bash
scripts/run-regression.sh
```

Requires an ADB-connected device. `JAVA_HOME` is auto-detected via Homebrew if not set.
Override ADB path with `ADB=/path/to/adb scripts/run-regression.sh`.

### ADB integration test suite

Ten sequential UI tests: launch, class start, Godot lifecycle, bridge connect, pause/resume, restart.

```bash
python3 scripts/integration_test.py                # build + install + run
python3 scripts/integration_test.py --skip-install # skip build (APK already installed)
python3 scripts/integration_test.py --screenshots  # save a PNG per test step to test-artifacts/
```

### Avatar self-test (demo video + screenshot)

Installs APK, launches in demo mode, records a 12-second video, captures a screenshot,
and dumps the UI hierarchy. Artifacts land in `test-artifacts/`.

```bash
scripts/avatar-self-test.sh
```

**Human review required** — open `test-artifacts/avatar-self-test.png` and
`test-artifacts/avatar-demo.mp4` and check the printed checklist.

### Avatar position sweep

Sweeps the avatar LEFT → RIGHT → CENTER → clear-override, capturing a screenshot at each stop.

```bash
scripts/test-avatar-movement.sh           # default 2-second wait per position
WAIT=4 scripts/test-avatar-movement.sh   # slower on laggy devices
```

Artifacts: `test-artifacts/avatar-left.png`, `avatar-right.png`, `avatar-center.png`, `avatar-auto.png`.

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

# Typically managed via ccb (CLI_Cowork_Bridge)
# The 'cmd' component in ccb is now used instead of a standalone tmux session.
```

Notes:
- Requires `tmux`.
- Scans all tmux panes by default; use `--pane` to limit to one pane.
- Logs to `logs/claude_rate_limit_watchdog.log`.
- Pattern match is literal `/rate-limit-options`.
- `scripts/claude-stop-hook.sh` invokes `scripts/claude_rate_limit_watchdog.sh --once` automatically as a best-effort pre-check on each stop-hook run.
