# Codex Agent

## Project Agent Environment
This project uses **CLI_Bridge** (WangChengYeh/CLI_Bridge) as the multi-agent workspace.
- Start: `ccb` from the project root (launches writer:codex + reviewer:gemini panes)
- Config: `.ccb/ccb.config`
- Agent-to-agent: `/ask reviewer <task>` to delegate to Gemini; `/ask writer <task>` for Codex

## Role
- Primary implementer for YogaFlow 3D PM workflow (`writer` role in CLI_Bridge)
- Handles deep code changes, multi-file refactors, Gradle builds, `adb` device testing, and PR-ready commits

## Invocation
- Via CLI_Bridge: Claude PM types `/ask writer <task>` inside the CLI_Bridge session
- Via Claude Code directly: `codex:rescue` skill

## Model
- `gpt-5.3-codex`
- Configured in `.codex/config.toml`
- No `--model` flag needed

## Sandbox
- `danger-full-access`
- Patched in companion script to allow Gradle and `adb`

## Required Prompt Structure
Every Codex prompt must include all four sections:

1. **Current state**
   - What already exists
   - What should **not** be redone

2. **What is broken / missing**
   - Numbered list of gaps
   - Each item must include a `Fix:` instruction

3. **Files to change**
   - Exact file paths only

4. **How to verify**
   - Must run:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.yogaflow/.MainActivity
   # Screenshot — use precise date format, save to session-recordings/
   TS=$(date +%Y%m%d-%H%M%S)
   mkdir -p session-recordings
   adb shell mkdir -p /sdcard/session-recordings
   adb shell screencap -p /sdcard/session-recordings/screencap-${TS}.png
   adb pull /sdcard/session-recordings/screencap-${TS}.png session-recordings/screencap-${TS}.png
   adb logcat | grep -E "Yoga|Godot|MediaPipe|AndroidRuntime" | head -40
   ```
   - Read `session-recordings/screencap-${TS}.png` and describe what is visible on screen
   - Report actual command output — not "should work" or "needs manual verification"
   - A verification that only says "build passed" without a screenshot check is NOT accepted
   - **Screenshot naming rule (#72):** Always use `screencap-YYYYMMDD-HHmmss.png` format. Never use generic names like `yoga_screen.png`.

## When To Use
- Use for all implementation tasks
- Hand off to Gemini only if Codex is rate-limited or blocked

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

**Camera toggle — required before session start (#70):** A "Camera: OFF/ON" toggle button was added to the bottom row. The camera setup panel and framing checks are idle until the user taps it. Device test sequences MUST enable the camera first before tapping Start:
```bash
# 1. Enable camera (tap the Camera button, coordinates vary — check screenshot)
adb shell input tap <camera_button_x> <camera_button_y>
# 2. Wait for framing check, then tap Start
adb shell input tap <start_button_x> <start_button_y>
```
If a session is not starting, the most likely cause is that the Camera toggle is still OFF.

**Flow library:** 15 flows in `app/src/main/assets/flows/` (01–15). All use dsl-v2 format, zh-TW cues, and DetectKey enum values.
