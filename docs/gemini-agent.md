# Gemini Agent

## Role
- Secondary implementer and reviewer in YogaFlow 3D PM workflow
- Used when Codex is rate-limited, or for review/continuation

## CLI Invocation
- Standard:
```bash
python3 scripts/gemini-acp.py "<task>"
```
- Read-only audit:
```bash
python3 scripts/gemini-acp.py "<task>" --mode plan
```
- Safe edits (default):
```bash
python3 scripts/gemini-acp.py "<task>" --mode auto_edit
```
- Resume:
```bash
python3 scripts/gemini-acp.py "<task>" --resume <session-id>
```
- List sessions:
```bash
python3 scripts/gemini-acp.py --list-sessions
```
- Explicit model:
```bash
python3 scripts/gemini-acp.py "<task>" --model gemini-2.5-pro
```

## MCP Tool Invocation
- Use:
```text
gemini_run(task, mode, model, resume_session)
```
- Available in Claude Code after restart once `mcpServers.gemini-acp` is registered in `.claude/settings.json`

## Default Model
- CLI default: `gemini-2.5-pro`
- MCP server default: `gemini-3.0-pro`
- Never use flash/flash-lite models

## Session IDs
- Printed to `stderr` after each run
- Save session IDs to resume context later

## When To Use
- Codex is rate-limited
- Review or audit tasks (`--mode plan`)
- Continue Codex work with full context handoff

## Handoff Requirement
Every handoff prompt must include:
- Issue number
- What Codex did
- Files changed
- Commands run
- Current failure output
- Remaining acceptance criteria
