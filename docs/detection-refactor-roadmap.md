# Detection Refactor Roadmap

This document tracks the next detection-layer refactors after strict routing was enabled.

## Current State

- Flow JSON detect strings are parsed into `DetectKey`.
- `PoseDetectionRouter` routes supported pose IDs to pose-specific detection mappers.
- Unsupported pose IDs now fail fast instead of silently falling back.
- Mountain still uses the legacy `PoseStateMachine` fallback path.

## Remaining Risks

### Stateful singleton mappers

Pose-specific mappers currently keep smoothing and stability-window state in singleton objects. This is acceptable for a single active session, but it makes state ownership implicit.

Risks:

- stale smoothing state after flow transitions
- cross-flow state contamination
- harder isolated tests
- future multi-session incompatibility

## Proposed Refactor Sequence

### Phase 1: Centralize mapper reset ownership

Create a small session owner for mapper lifecycle:

```kotlin
class DetectionMapperSession {
    fun resetAll() {
        ForwardFoldDetectionMapper.reset()
        TwistDetectionMapper.reset()
        SquatDetectionMapper.reset()
        BridgeDetectionMapper.reset()
    }
}
```

Then route all reset calls through this class.

Goal: make mapper session state explicit without changing detection behavior.

### Phase 2: Instance-scope mapper state

Convert mappers from singleton `object` to `class` instances owned by the detection session.

Goal: make smoothing and stability windows private to one live coaching session.

### Phase 3: Router owns mapper instances

Convert `PoseDetectionRouter` from singleton-style usage into an instance with injected mappers.

Goal: prepare for tests and future multi-session support.

### Phase 4: Remove mountain fallback

Add a strict mountain mapper and remove the remaining legacy fallback path.

Goal: every Flow DSL pose should use explicit strict detection behavior.

## Validation Goals

- Flow transition resets mapper state.
- Restart resets mapper state.
- Unsupported pose IDs fail fast.
- Mapper fail reasons remain numeric when available.
- No change to existing Flow JSON semantics.
