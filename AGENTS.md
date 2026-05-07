# Codex Agent

## Role
- Primary implementer for YogaFlow 3D PM workflow
- Handles deep code changes, multi-file refactors, Gradle builds, `adb` device testing, and PR-ready commits

## Invocation
- Invoke via `codex:rescue` skill in Claude Code

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
   adb install <apk-path>
   adb logcat
   ```
   - Must report actual command output (not just "should work")

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

**Build environment:** Local machine has JDK 8. Build requires JDK 17+. Run `export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew assembleDebug` or flag the JDK issue explicitly in the report.
