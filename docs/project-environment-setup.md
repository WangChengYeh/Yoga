# Project Environment Setup

This guide standardizes a single tmux workspace for all agents:
- Pane 1: Claude
- Pane 2: Codex
- Pane 3: Gemini
- Pane 4: communication monitor (live data exchange view via script)

The target layout is a 2x2 grid built with tmux vertical + horizontal splits.

## Prerequisites

- `tmux` installed
- `claude`, `codex`, and `gemini` CLIs available on `PATH`
- `python3` available (for `scripts/agent_comm_logger.py`)
- Optional but recommended: `sqlite3` (for raw event stream in monitor pane)
- Repo checked out at:
  - `/Users/wangchengye/Documents/GitHub/Yoga`

## 1. Create the 4-pane tmux session

Run from repo root:

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga

SESSION=yoga-agents
WINDOW=agents
ROOT=/Users/wangchengye/Documents/GitHub/Yoga

# Fresh session with one pane (pane 0)
tmux new-session -d -s "$SESSION" -n "$WINDOW" -c "$ROOT"

# Split into 4 panes: vertical + horizontal
# Result:
#   0 | 1
#  ---+---
#   2 | 3
tmux split-window -h -t "$SESSION:$WINDOW.0" -c "$ROOT"
tmux split-window -v -t "$SESSION:$WINDOW.0" -c "$ROOT"
tmux split-window -v -t "$SESSION:$WINDOW.1" -c "$ROOT"

# Optional: even sizing
tmux select-layout -t "$SESSION:$WINDOW" tiled

# Pane 0 = Claude
tmux send-keys -t "$SESSION:$WINDOW.0" 'source sourceme && claude' C-m

# Pane 1 = Codex
tmux send-keys -t "$SESSION:$WINDOW.1" 'source sourceme && codex' C-m

# Pane 2 = Gemini
tmux send-keys -t "$SESSION:$WINDOW.2" 'source sourceme && gemini' C-m

# Pane 3 = Communication monitor
# Shows rolling stats and latest agent_comms rows from SQLite.
tmux send-keys -t "$SESSION:$WINDOW.3" 'source sourceme && while true; do clear; date; echo "=== Agent Comms Stats (24h) ==="; python3 scripts/agent_comm_logger.py stats --days 1; echo; echo "=== Latest Exchanges ==="; sqlite3 -header -column logs/agent_comms.db "SELECT created_at, direction, from_agent, to_agent, session_id, issue_id, substr(message,1,100) AS message FROM agent_comms ORDER BY id DESC LIMIT 12;" 2>/dev/null || echo "No DB rows yet (or sqlite3 not installed)."; sleep 3; done' C-m

# Attach
tmux attach -t "$SESSION"
```

## 2. How to use the communication pane

Use `scripts/agent_comm_logger.py` to write send/receive events. Example:

```bash
python3 scripts/agent_comm_logger.py send --from claude --to codex --session sess-20260509-01 --issue "#72" --message "Implement tmux 4-pane setup"
python3 scripts/agent_comm_logger.py recv --from codex --to claude --session sess-20260509-01 --issue "#72" --message "Implemented and verified" --elapsed-ms 32000
```

Pane 4 updates automatically and displays:
- 24h communication stats
- Latest rows from `logs/agent_comms.db`

## 3. Verification checklist

From any shell:

```bash
tmux ls
tmux list-panes -t yoga-agents:agents -F '#{pane_index} #{pane_current_command}'
tmux capture-pane -pt yoga-agents:agents.3 -S -80 | tail -n 60
```

Expected:
- Session `yoga-agents` exists.
- Four panes exist in window `agents`.
- Pane 0/1/2 run `claude`, `codex`, `gemini`.
- Pane 3 shows rolling communication output (stats and latest exchanges).

## 4. Reattach later

```bash
tmux attach -t yoga-agents
```

## 5. Reset session (if layout is broken)

```bash
tmux kill-session -t yoga-agents
```

Then rerun section 1.
