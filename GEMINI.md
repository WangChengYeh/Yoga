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

## Key Conventions (updated 2026-05-08)

**GDScript dual-file sync:** Any change to `godot/scripts/*.gd` MUST also be applied to `app/src/main/assets/scripts/*.gd`. Both copies must stay identical. The `.gd.remap` files point to `.gd` source (not `.gdc`), so the assets copy is what runs on device.

**Godot avatar layout:** `virtualCoachView` (GodotFragment) is a `110dp × 196dp` corner PiP (`bottom|end`). Do NOT revert to `match_parent`. Avatar is centered in the PiP (`_side_x_offset = 0.0`).

**PoseOverlayView coordinate mapping:** Landmarks are mapped using FILL_CENTER transform (scale = max(scaleX, scaleY), centered offsets) based on `imageWidth`/`imageHeight` from `PoseDetectionResult`. Do not use raw `landmark.x() * viewWidth`.

**Build environment:** Local machine has JDK 8. Build requires JDK 17+. Flag this explicitly if `./gradlew assembleDebug` fails.

## Verification Requirement
Every task must end with:
1. `./gradlew assembleDebug` — confirm BUILD SUCCESSFUL
2. `adb install -r <apk-path> && adb shell am start ... && adb logcat`
3. Report actual output — not "it should work"
