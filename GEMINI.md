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
- `auto-gemini-3` (Gemini 3 Auto)
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

**Build environment:** Local machine has JDK 8 as default. Build requires JDK 17+. Use the explicit Homebrew path — `java_home -v 17` resolves to JDK 8 on this machine and will NOT work:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

**Camera toggle — required before session start (#70):** A "Camera: OFF/ON" toggle button is in the bottom row. Camera setup and framing checks are idle until it is tapped ON. Any device test that tries to start a session must tap the Camera button first, then tap Start.

**Flow library:** 15 flows in `app/src/main/assets/flows/` (01–15). All use dsl-v2 format, zh-TW cues.

## Verification Requirement
Every task must end with:
1. `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug` — confirm BUILD SUCCESSFUL
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell am start -n com.yogaflow/.MainActivity`
4. Screenshot — use precise date format, save to `session-recordings/`:
   ```bash
   TS=$(date +%Y%m%d-%H%M%S)
   mkdir -p session-recordings
   adb shell mkdir -p /sdcard/session-recordings
   adb shell screencap -p /sdcard/session-recordings/screencap-${TS}.png
   adb pull /sdcard/session-recordings/screencap-${TS}.png session-recordings/screencap-${TS}.png
   ```
5. Read `session-recordings/screencap-${TS}.png` and describe what is visible — confirm the feature works visually
6. `adb logcat | grep -E "Yoga|Godot|MediaPipe|AndroidRuntime" | head -40`
7. Report actual output — not "it should work" or "needs manual verification"
8. **Screenshot naming rule (#72):** Always `screencap-YYYYMMDD-HHmmss.png`. Never use generic names like `yoga_screen.png`.
