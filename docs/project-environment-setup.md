# Project Environment Setup

This project uses **CLI_Cowork_Bridge (CCB)** as the multi-agent workspace. CCB launches the
configured agents in a single command, owns the inter-agent control plane, and replaces the
older manual tmux + `agent_comm_logger.py` + SQLite setup.

Upstream: <https://github.com/WangChengYeh/CLI_Cowork_Bridge>

---

## Prerequisites

- macOS / Linux shell
- `claude`, `codex`, and `gemini` CLIs available on `PATH`
- Git checkout at `/Users/wangchengye/Documents/GitHub/Yoga`
- Optional: `gh` for GitHub issue triage (used by Claude PM)

---

## One-time install

```bash
git clone https://github.com/WangChengYeh/CLI_Cowork_Bridge.git
cd CLI_Cowork_Bridge
./install.sh install
ccb update    # pull latest control-plane code
```

`ccb` lands in `~/.local/bin/ccb`. Confirm with `which ccb`.

---

## Project config: `.ccb/ccb.config`

The config is committed at the repo root. Current shape:

```text
it:gemini, pm:claude; rd:codex, ae:gemini

[agents.it]
model = "gemini-3.1-flash-lite-preview"
[agents.pm]
model = "claude-sonnet-4-6"
[agents.rd]
model = "gpt-5.3-codex"
[agents.ae]
model = "auto-gemini-3"
```

Role mapping for YogaFlow 3D:

| Role | Agent  | Responsibility                                                           |
| ---- | ------ | ------------------------------------------------------------------------ |
| `pm` | Claude | Triage GitHub issues, write prompts, review output, commit, manage releases |
| `rd` | Codex  | Primary implementer — deep code changes, Gradle builds, adb device testing  |
| `ae` | Gemini | Application-to-user integration, review, secondary implementer              |
| `it` | Gemini | Integration testing / lightweight automation                                 |

`pm` (Claude) is the PM and does not implement directly — it delegates to `rd` and `ae`.

To change a role assignment, edit `.ccb/ccb.config`, then run `ccb -n` to rebuild the runtime.

---

## Start / stop

```bash
ccb           # launch all agents defined in .ccb/ccb.config
ccb -s        # safe start — disable CLI auto-permission override
ccb -n        # rebuild runtime (keeps ccb.config), then start fresh
ccb kill      # stop the project's background runtime
ccb kill -f   # force cleanup of project-owned runtime residue
```

Runtime state lives under `.ccb/` (`ccbd/`, `room/`, `runtime-daemon.json`). These directories
are gitignored — do not commit them.

---

## Delegation commands

From inside Claude Code (PM):

```text
/ask <role> <message>     # send a task to another agent via skill
/ping <role|ccbd>         # inspect control-plane health
/pend <role|job_id>       # inspect mailbox / job replies
```

Equivalent shell form:

```bash
ccb ask <role> [from <sender>] <message>
ccb ping <role|ccbd>
ccb pend <role|job_id> [N]
ccb watch <role|job_id>
```

Async guardrail: when an `ask` returns `[CCB_ASYNC_SUBMITTED`, reply with one line
(`<Provider> processing...`) and end the turn — do not poll. CCB delivers the result back as a
hook in a later turn.

Typical PM delegations in this project:

```text
/ask rd <implementation task>       # → Codex implements
/ask ae <review or continuation>    # → Gemini reviews / continues
```

Handoff example: when Codex hits rate limit, Claude PM sends
`/ask ae continue: <what Codex did> / <what remains>`.

---

## Diagnostics

```bash
ccb ps                    # list active agent processes
ccb logs <role>           # tail an agent's log stream
ccb doctor                # environment self-check (paths, providers, daemon)
ccb daemon status         # control-plane daemon status
ccb daemon metrics        # daemon metrics
ccb version               # show installed version
```

Inspect mailbox content interactively:

```bash
ccb pend <role>           # peek latest replies
ccb watch <role>          # live-tail replies
```

---

## iMessage channel (optional)

The user receives mobile pings through CCB's iMessage bridge:

```bash
ccb imessage send <message>
ccb imessage doctor
ccb imessage watch
```

Access policy for incoming iMessages is managed by the `/imessage:access` skill — the user
runs it locally. Never approve a pairing on someone else's behalf.

---

## Fallback when CCB is unavailable

If `ccb` is not running, fall back to direct tool invocation:

| Target | Direct fallback |
| ------ | ---------------- |
| Codex  | `codex:rescue` skill (Agent tool, `subagent_type: codex:codex-rescue`) |
| Gemini | `mcp__gemini__gemini_run` MCP tool |

These bypass CCB's control plane (no shared mailbox, no `/pend`), so use them only when CCB is
down. Restore CCB with `ccb` (or `ccb -n` if the runtime is stuck).

Codex companion script (patched for `danger-full-access` sandbox so it can run Gradle/adb):

```text
~/.claude/plugins/cache/openai-codex/codex/1.0.4/scripts/codex-companion.mjs
```

If the plugin updates and resets the patch, re-apply:

```bash
sed -i '' 's/"workspace-write"/"danger-full-access"/' \
  ~/.claude/plugins/cache/openai-codex/codex/1.0.4/scripts/codex-companion.mjs
```

---

## Verification checklist

After `ccb` boots:

```bash
ccb ps               # all four roles (pm, rd, ae, it) running
ccb ping ccbd        # daemon healthy
ccb ping rd          # codex reachable
ccb ping ae          # gemini reachable
```

Expected:

- All four agent processes listed in `ccb ps`.
- `ccb ping ccbd` returns OK.
- `ccb ping rd` / `ccb ping ae` return OK and report the configured model.

---

## Legacy setup (removed)

The previous tmux 4-pane + `scripts/agent_comm_logger.py` + `logs/agent_comms.db` setup is no
longer used. CCB owns the multi-agent runtime, control plane, and inter-agent messaging end to
end. If you find references to `yoga-agents` tmux sessions or the SQLite comms log in old
commits, they predate the CCB migration.
