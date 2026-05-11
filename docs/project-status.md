# YogaFlow 3D Project Status

Last updated: 2026-05-12 (docs re-org: removed `project-plan.md`, `flow-format.md`, `detection-refactor-roadmap.md`; their content lives in `Proposal.md`, `flow-dsl.md`, `roadmap.md`, and `architecture.md`)

## Purpose

This file is the short status hub for the project. Detailed information is partitioned into these documents:

- `docs/architecture.md`: runtime architecture, data flow, design rules, and component responsibilities.
- `docs/roadmap.md`: finished work, unfinished work, and future product direction.
- `docs/test-plan.md`: build, unit, integration, device, screenshot, and regression verification.
- `docs/flow-dsl.md`: Flow DSL v2 schema and runtime validation rules.
- `docs/avatar-overlay-architecture.md`: Android/Godot avatar overlay layout and positioning contract.
- `docs/godot-avatar-coach-overlay.md`: avatar coach product design direction.
- `docs/avatar-rig-skeleton.md`: avatar rig, bone mapping, and Blender-to-Godot pipeline.
- `docs/avatar-notes.md`: avatar source asset, materials, and licensing notes.
- `docs/project-environment-setup.md`: local multi-agent and tmux workspace setup.
- `docs/Proposal.md`: consolidated product, pitch, story, and business-facing material.

## Current Snapshot

YogaFlow 3D is an Android on-device AI yoga coaching app.

```text
CameraX -> MediaPipe Pose -> Camera setup gate -> Detection mapping -> Flow runtime -> Coaching feedback
                                                        |                         |
                                                        v                         v
                                                Pose overlay / framing      TTS / LLM fallback / Godot avatar
```

Current implementation facts:

- Kotlin app logic lives under `app/src/main/java/com/yogaflow/`.
- Godot runtime assets live under `app/src/main/assets/`.
- Godot source scripts must stay mirrored between `godot/scripts/*.gd` and `app/src/main/assets/scripts/*.gd`.
- Packaged flow library currently contains 20 `.flow.json` files in `app/src/main/assets/flows/`.
- App starts with camera off; camera setup and framing checks are idle until the user enables the camera toggle.
- Build requires JDK 17 via `JAVA_HOME=/opt/homebrew/opt/openjdk@17`.

## Current Verification Baseline

Last known local verification from this workspace:

- `assembleDebug`: passed
- `test`: passed
- `connectedDebugAndroidTest`: passed
- APK install: passed
- App launch: passed
- Screenshot smoke check: home screen rendered with Beginner Flow and START button
- Clean logcat sample: no current `AndroidRuntime` crash observed

Canonical verification commands and screenshot rules live in `docs/test-plan.md`.

## Documentation Partition Rules

- Put system design and component responsibilities in `architecture.md`.
- Put shipped, active, unfinished, and future product work in `roadmap.md`.
- Put exact verification commands, device procedures, and test gaps in `test-plan.md`.
- Put current one-page project state and cross-document navigation in this file.
- Do not duplicate long command blocks across multiple docs; link to `test-plan.md` instead.
- Do not duplicate roadmap lists across multiple docs; update `roadmap.md` instead.
