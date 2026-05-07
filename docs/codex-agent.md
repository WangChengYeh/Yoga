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
