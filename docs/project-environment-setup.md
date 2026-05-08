# Project Environment Setup

This guide sets up the Yoga project workflow with:
- tmux session 1: `claude_rate_limit_watchdog.sh` running in background
- tmux session 2: Claude Code in project root
- automatic `go project` sent to Claude when ready

## Prerequisites

- `tmux` installed
- Claude CLI installed (`claude` command available)
- Repo checked out at:
  - `/Users/wangchengye/Documents/GitHub/Yoga`

## 1. Recommended: one-command setup

Run from repo root:

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga
./setup.sh
```

What `setup.sh` does:
- Creates tmux session `yoga-watchdog` and runs:
  - `source sourceme`
  - `scripts/claude_rate_limit_watchdog.sh --daemon --interval 3600`
- Creates tmux session `yoga-claude` and runs:
  - `source sourceme`
  - `claude`
  - `go project` (auto-sent after Claude boots)
- If sessions already exist, it keeps them and sends `go project` to `yoga-claude`.

Optional env overrides:

```bash
WATCHDOG_SESSION=my-watchdog CLAUDE_SESSION=my-claude WATCHDOG_INTERVAL=1800 CLAUDE_BOOT_WAIT_SECONDS=3 ./setup.sh
```

## 2. Manual tmux setup (fallback)

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga
tmux new -s yoga-watchdog
source sourceme
scripts/claude_rate_limit_watchdog.sh --daemon --interval 3600
```

Open a new terminal tab/window, then run:

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga
tmux new -s yoga-claude
source sourceme
claude
go project
```

## 3. Verify sessions and logs

From any shell, run:

```bash
tmux ls
tail -n 20 /Users/wangchengye/Documents/GitHub/Yoga/logs/claude_rate_limit_watchdog.log
tmux capture-pane -pt yoga-claude -S -40 | tail -n 40
```

Expected:
- `yoga-watchdog` session exists and remains active.
- `yoga-claude` session exists for interactive Claude work.
- `go project` appears in the recent `yoga-claude` pane history.

## 4. Reattach later

```bash
tmux attach -t yoga-watchdog
tmux attach -t yoga-claude
```
