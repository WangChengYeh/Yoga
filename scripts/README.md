# Scripts

## Testing

### Full regression (Gradle + APK + on-device)

Runs unit tests, builds the debug APK, installs it, checks the Godot bridge port,
sweeps the avatar through positions with automated position verification, and checks
for crashes. Run from the repo root.

```bash
scripts/test-regression.sh
```

Requires an ADB-connected device. `JAVA_HOME` is auto-detected via Homebrew if not set.
Override ADB path with `ADB=/path/to/adb scripts/test-regression.sh`.

### Avatar position sweep + verification

Sweeps the avatar LEFT → RIGHT → CENTER → clear-override, then runs
`test-avatar-position.py --verify` on the captured screenshots and `--logcat`
for ground-truth ordering. Exits non-zero if verification fails.

```bash
scripts/test-avatar-movement.sh           # default 2-second wait per position
WAIT=4 scripts/test-avatar-movement.sh   # slower on laggy devices
```

Artifacts: `test-artifacts/avatar-left.png`, `avatar-right.png`, `avatar-center.png`, `avatar-auto.png`.

### Avatar position detection (CV + logcat)

Extracts and verifies avatar position from screen captures.
`uv run` auto-installs `opencv-python` and `numpy` on first use — no manual `pip install` needed.

```bash
# Verify regression artifacts (screenshot diff + logcat ordering)
uv run scripts/test-avatar-position.py --verify test-artifacts/

# Read avatar position commands from live ADB logcat (most reliable)
uv run scripts/test-avatar-position.py --logcat

# Analyse a screen recording for avatar motion (background subtraction)
uv run scripts/test-avatar-position.py --video test-artifacts/avatar-demo.mp4

# Analyse a single screenshot (blob detection, less reliable on home screen)
uv run scripts/test-avatar-position.py --image test-artifacts/avatar-left.png

# Diff-based detection between two screenshots
uv run scripts/test-avatar-position.py --image avatar-left.png --ref avatar-center.png

# Save annotated output alongside any mode
uv run scripts/test-avatar-position.py --image avatar-left.png --annotate
```

**Known limitation**: `adb shell screencap` does not composite the Godot SurfaceView layer
on most devices, so pixel-diff checks are automatically skipped when images are identical.
The logcat corroboration (ground truth) and video analysis are not affected.

### Avatar self-test (demo video + screenshot + motion analysis)

Installs APK, launches in demo mode, records a 12-second video, runs CV motion analysis
on it, captures a screenshot, and dumps the UI hierarchy. Artifacts land in `test-artifacts/`.

```bash
scripts/test-avatar-self.sh
```

Automatically calls `test-avatar-position.py --video` after pulling the recording.
The video checklist still requires human review for visual quality.

### ADB integration test suite

Eleven sequential UI tests: launch, class start, Godot lifecycle, bridge connect, pause/resume, avatar position sweep, restart.

```bash
uv run scripts/test-integration.py                # build + install + run
uv run scripts/test-integration.py --skip-install # skip build (APK already installed)
uv run scripts/test-integration.py --screenshots  # save a PNG per test step to test-artifacts/
```

## Agent Communication Logger

Log Claude/Codex/Gemini agent communications to a local SQLite database (`logs/agent_comms.db`).

```bash
uv run scripts/agent_comm_logger.py send   --from claude --to codex  --session sess-2026-05-08-01 --issue "#71" --message "Implement sqlite logger"
uv run scripts/agent_comm_logger.py recv   --from codex  --to claude --session sess-2026-05-08-01 --issue "#71" --message "Implemented + verified" --elapsed-ms 45000
uv run scripts/agent_comm_logger.py report --date 2026-05-08 --session sess-2026-05-08-01
uv run scripts/agent_comm_logger.py stats  --days 7
```

## Export SQLite Logs to CSV

Export the latest `X` rows from a SQLite table to CSV:

```bash
uv run scripts/export_sqlite_logs_csv.py --limit 100
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
uv run scripts/export_sqlite_logs_csv.py --table hook_events --limit 10 --out logs/hook_events_latest.csv
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
