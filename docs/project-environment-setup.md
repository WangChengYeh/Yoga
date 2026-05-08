# Project Environment Setup

This guide sets up the Yoga project workflow with:
- tmux session 1: `claude_rate_limit_watchdog.sh` running in background
- tmux session 2: Claude Code working session in project root

## Prerequisites

- `tmux` installed
- Claude CLI installed (`claude` command available)
- Repo checked out at:
  - `/Users/wangchengye/Documents/GitHub/Yoga`

## 1. Start watchdog tmux session

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga
tmux new -s yoga-watchdog
source sourceme
scripts/claude_rate_limit_watchdog.sh --daemon --interval 3600
```

Notes:
- Keep this tmux session running.
- Logs are written to `logs/claude_rate_limit_watchdog.log`.

## 2. Start Claude Code tmux session

Open a new terminal tab/window, then run:

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga
tmux new -s yoga-claude
source sourceme
claude
```

Inside Claude, you are now in the project workspace and can start issue triage/implementation.

## 3. Quick checks

From any shell:

```bash
tmux ls
tail -n 20 /Users/wangchengye/Documents/GitHub/Yoga/logs/claude_rate_limit_watchdog.log
```

Expected:
- `yoga-watchdog` session exists and remains active.
- `yoga-claude` session exists for interactive Claude work.

## 4. Reattach later

```bash
tmux attach -t yoga-watchdog
tmux attach -t yoga-claude
```

