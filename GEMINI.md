# Gemini Agent

## Role
- Secondary implementer and reviewer in YogaFlow 3D PM workflow
- Used when Codex is rate-limited, or for review/continuation

## Invocation
- Invoked via the `mcp__gemini__gemini_run` MCP tool from Claude Code
- Do NOT use `python3 scripts/gemini-acp.py`

| approval_mode | Use when |
|---|---|
| `plan` | Read-only audit, reviewing code, no edits |
| `auto_edit` | Fixing code, file edits allowed (default) |

## Default Model
- `gemini-2.5-pro`
- Never use flash/flash-lite models

## When To Use
- Codex is rate-limited
- Review or audit tasks (`approval_mode: plan`)
- Continue Codex work with full context handoff

## Handoff Requirement
Every handoff prompt must include:
- Issue number
- What Codex did
- Files changed
- Commands run
- Current failure output
- Remaining acceptance criteria

## Project Context
- Android on-device AI yoga coaching app
- Kotlin (main logic) + Godot (avatar rendering) + MediaPipe (pose detection)
- Architecture: `docs/architecture.md`
- Roadmap: `docs/roadmap.md`

## Verification Requirement
Every task must end with:
1. `./gradlew assembleDebug` — confirm BUILD SUCCESSFUL
2. `adb install -r <apk-path> && adb shell am start ... && adb logcat`
3. Report actual output — not "it should work"
