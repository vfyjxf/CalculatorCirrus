# Execution Driver API Design (2026-03-31)

## Summary

This design generalizes execution UI and logic into a reusable driver-based API under dev.vfyjxf.cirrus.api.recipe.execution. It introduces a registry-driven ExecutionDriver abstraction, a slot-level skeleton implementation for crafting-style interactions, and a UI snapshot/control surface. The existing UI becomes a pure consumer of API view models and driver controls. Server-side drivers, when enabled by config, execute recipes fully on the server; the client acts as a proxy and UI surface.

Key goals:
- Provide a general API that most integrations can reuse or extend.
- Offer a slot-level skeleton implementation as the standard base.
- Allow fully custom implementations when the skeleton is insufficient.
- Keep execution sessions in-memory only; no persistence across restarts.
- Ensure UI has sufficient status and control while staying decoupled.

## Scope and Boundaries

In scope:
- Generalize UI execution packages into API types.
- Provide driver registry and slot skeleton.
- Provide pure client driver and server driver implementations.
- Generalize the demo controller into a CraftingTableExecutionDriver.
- UI consumes view models and control interfaces only.
- Fix current compilation issues in the same change set.

Out of scope:
- Persistent storage of execution sessions across restarts.
- Automatic plan selection; plans are provided externally.

## Architecture

### Layers
1. Execution Core (existing)
   - ExecutionSession, InteractionExecutionPlan, InteractionStep, ExecutionRuntime, ExecutionTranslator.
   - Remains the canonical execution state/semantics.

2. Execution Drivers (new)
   - ExecutionDriver: unified execution and control interface.
   - ExecutionDriverRegistry: lookup by interactionId + side.
   - ExecutionDriverState: in-memory resume only.

3. Slot Skeleton (new)
   - SlotAccess: minimal slot operations (read/click/count/interaction open).
   - SlotLayout: slot region description.
   - SlotExecutionDriver: base class implementing common craft workflow.
   - CraftingTableExecutionDriver (client) and CraftingTableServerExecutionDriver (server).

4. UI View Models (moved to API)
   - ExecutionOverlayViewModel, ExecutionOverlayNode, ExecutionOverlayLaneSegment moved to api.
   - UI depends on these only.

## API Components

### Execution Driver
Proposed interface surface (exact names may differ):
- ExecutionSession session()
- ExecutionDriverState state()
- ExecutionDriverSnapshot snapshot()
- advanceSteps(int steps)
- pause(), resume()
- toggleAutoAdvance()
- setRatePerSecond(double rate)
- reset()
- jumpToStep(int index)

Optional capability flags:
- supportsPause, supportsResume, supportsJump

### Driver Registry
- Register by interactionId + side.
- Lookup rules:
  - Prefer exact interactionId + side.
  - Fallback to interactionId + environmentAgnostic.
  - Final fallback: ManualOnlyExecutionDriver.

### Snapshot for UI
ExecutionDriverSnapshot fields:
- ExecutionSession session
- ExecutionOverlayViewModel viewModel
- ExecutionTelemetrySnapshot telemetry
- ImmutableList<OverlayMaterialRequirement> materials
- OverlayTargetProgress targetProgress
- double ratePerSecond
- boolean autoAdvanceEnabled
- ReTranslationHint reTranslationHint

UI consumes snapshot; does not drive translation.

## Data Flow

1. External system provides RecipeEntry, InteractionExecutionPlan, and ExecutionFlowNode (or equivalent builder inputs).
2. Driver holds ExecutionSession and uses ExecutionRuntime to advance steps.
3. Interaction-specific behavior (slot clicking, etc.) lives in driver implementations.
4. UI pulls ExecutionDriverSnapshot and renders via view models.

## Server-First Execution

If a server driver exists and config enables it:
- Server performs the entire execution.
- Client sends only control commands and displays server snapshots.
- Client driver acts as proxy (no real crafting).

### Config
- execution.serverDriver.enabled (boolean)
- execution.serverDriver.interactionWhitelist (list of interactionId; empty = all)

## In-Session Resume

- ExecutionDriverState holds the active ExecutionSession plus driver-specific fields.
- State remains in-memory only; no persistence across restarts.
- If interaction is closed, driver returns to a paused/wait state on resume.

## Compatibility and Error Handling

- If no driver matches, fallback driver pauses with guidance.
- If server driver is enabled but unavailable, fallback to client driver with UI notice.
- Errors are represented as PauseReason + ExecutionGuidance.

## Testing

- Update tests to reference CraftingTableExecutionDriver.
- Add registry selection tests.
- Add slot driver input/output handling tests.
- Validate server driver fallback and proxy behavior.

## Migration Notes

- WorkbenchExecutionDemoController becomes CraftingTableExecutionDriver.
- UI classes are updated to depend on snapshot/control interfaces, not a demo controller.
- View models are moved from ui packages into api.

