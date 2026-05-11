# Detection Refactor Status

This document is an archived status note for the detection-layer refactor. Active product roadmap items now live in `docs/roadmap.md`; architecture responsibilities live in `docs/architecture.md`.

## Current State

- Flow JSON detect strings are parsed into `DetectKey`.
- `PoseDetectionRouter` routes supported pose IDs to pose-specific detection mappers.
- Unsupported pose IDs fail fast instead of silently falling back.
- `DetectionMapperSession` owns mapper instances and reset lifecycle.
- Mountain uses a strict `MountainDetectionMapper`; the old legacy fallback path has been removed for mountain.
- Expanded poses that do not yet have strict geometric mappers still use the intentionally routed fallback behavior in `PoseDetectionRouter`.

## Completed Refactor Phases

### Phase 1: Centralize Mapper Reset Ownership

`DetectionMapperSession` centralizes mapper lifecycle reset behavior.

### Phase 2: Instance-Scope Mapper State

Pose-specific mappers are class instances with smoothing and stability state scoped to the session owner.

### Phase 3: Router Owns Mapper Instances

`PoseDetectionRouter` is instance-based and receives mapper instances through construction.

### Phase 4: Strict Mountain Mapper

`MountainDetectionMapper` handles mountain detect keys explicitly instead of relying on the legacy `PoseStateMachine` path.

## Remaining Risks

- Some expanded poses still route through fallback behavior until strict pose-specific mappers are implemented for each one.
- Smoothing and stability-window behavior still need fixture-based regression tests for realistic frame sequences.
- Future multi-session support should keep mapper state scoped per live session.

## Validation Goals

- Flow transition resets mapper state.
- Restart resets mapper state.
- Unsupported pose IDs fail fast.
- Mapper fail reasons remain numeric when available.
- Packaged flow detect keys remain routable.
- Existing Flow JSON semantics remain stable.
