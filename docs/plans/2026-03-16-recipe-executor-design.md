# Recipe Executor Design

## Goal

Design a recipe executor that simulates player-side crafting behavior while staying compatible with Cirrus's routing pipeline. The executor must translate abstract routing output into interaction-oriented runtime steps, handle storage accessibility and player inventory capacity, expose machine interaction as a generic API with a default crafting-table-like implementation, and provide a tree-shaped progress model for visualization.

## Scope

- Build an execution layer on top of `RoutingPlan` rather than merging execution into routing.
- Support mostly client-driven execution, while modeling per-step side requirements instead of hard-coding the whole executor to a single side.
- Treat machine discovery and opening as API boundaries. Complex machines may only provide guidance and manual checkpoints in the first version.
- Keep execution plans in-memory only. If assumptions become invalid, re-translate or re-plan instead of restoring from serialized executor state.

## Non-Goals

- No automatic machine discovery, pathfinding, or generic "find and open every machine" implementation.
- No persisted executor sessions across reconnect or restart.
- No universal full-automation backend for every machine GUI in the first version.
- No attempt to make `RoutingPlan` itself runtime-aware.

## Architecture

### Routing vs Execution

`RoutingPlan` remains a planner output. It expresses what theoretical actions are needed, but not how the current player, inventory, screen, or accessible storage can satisfy them.

The new execution layer introduces `ExecutionTranslator`, which converts a `RoutingPlan` into an `InteractionExecutionPlan`. Translation inserts runtime protection and interaction steps that do not belong in the planner:

- accessibility checks for storage and interaction context
- player inventory capacity checks
- extraction and insertion routing
- manual checkpoints for machines that cannot be driven automatically
- output return and spill-prevention steps

### Runtime Components

- `ExecutionTranslator`
  Translates `PlanAction` values into interaction-facing steps and produces a visualization tree.
- `ExecutionRuntime`
  Sequentially evaluates translated steps against the current environment and drives the active session.
- `ExecutionSession`
  Holds transient runtime state such as current step index, pause reason, reserved catalysts, pending outputs, and current interaction target.
- `StorageApi`
  Becomes a runtime capability entry point for accessible inventories and transfer operations.
- `InteractionApi`
  Becomes a generic interaction capability entry point, with a default crafting-table-like implementation.

## Core Models

### InteractionExecutionPlan

The translated execution plan is in-memory only and should stay minimal. It needs:

- translated interaction steps
- required runtime capabilities
- translation notes for diagnostics and UX
- re-translation hints for invalidation and retry

It does not need plan versioning or serialization metadata.

### InteractionStep Hierarchy

Use sealed interfaces and records instead of enums with external payload maps. The first version should model steps close to actual runtime behavior:

- `EnsureAccessibleStep`
- `EnsureCapacityStep`
- `WithdrawResourceStep`
- `ReserveCatalystStep`
- `OpenInteractionStep`
- `PrepareInputsStep`
- `ExecuteRecipeStep`
- `CollectOutputsStep`
- `StoreOutputsStep`
- `ManualCheckpointStep`

Each step carries its own side requirement so runtime can decide whether it can execute immediately, needs player action, or must pause.

### ExecutionFlowViewModel

The executor also emits a tree-shaped progress model for UI. This model is separate from the linear runtime step list.

The recommended structure is recipe-centered:

- root node for the requested target
- one execution node per translated recipe action
- child nodes for input acquisition, catalyst reservation, interaction opening, recipe execution, and output handling

Each node stores:

- label
- status
- optional guidance
- associated runtime steps
- child nodes

This keeps step execution strictly ordered while exposing meaningful tree progress in the UI.

## Translation Rules

### ConsumeStockAction

Translate into:

- ensure source accessibility
- ensure player or target capacity if transfer staging is needed
- withdraw the resource into the required runtime context

### ReserveCatalystAction

Translate into:

- ensure source accessibility
- withdraw or confirm the catalyst is already held
- reserve it for later recipe execution without consuming it

### ExecuteRecipeAction

Translate into:

- ensure the required interaction context is available
- ensure the player inventory can absorb intermediate movement and outputs
- prepare inputs into the correct slots or context
- execute the recipe
- collect outputs
- store outputs back into accessible storage when possible

### Protection Step Injection

The translator is responsible for injecting runtime safety steps, especially:

- capacity checks before transfer or output collection
- accessibility checks before extraction and insertion
- manual checkpoints when interaction exists but no automatic performer exists

## Storage Design

The existing `StorageApi` behaves more like a registry facade. The executor needs a runtime-facing API.

### New Responsibilities

`StorageApi` should expose:

- the currently accessible storage sources
- whether a source is accessible right now
- which sources can provide a requested resource
- whether a source can receive a resource right now
- simulated extraction and insertion results
- real extraction and insertion operations
- structured failure and guidance information

### StorageSource Contract

`StorageSource` should move away from being only a passive content snapshot. It should support runtime queries or delegate them to associated handlers so translation and runtime can reason about:

- current accessibility
- extraction ability
- insertion ability
- partial transfer results
- failure reasons

### Backward Compatibility

If the existing storage registration behavior is still needed for UI and plugin wiring, keep that concern in implementation helpers or a dedicated registry abstraction rather than forcing the executor to depend on a registration-only API.

## Interaction Design

### InteractionApi Responsibilities

`InteractionApi` is the generic interaction boundary for execution. It should focus on three operations:

- `probe`
  Detect whether the current environment matches a known interaction context.
- `guide`
  Produce `ExecutionGuidance` when the required context is not active.
- `perform`
  Execute a generic action within a matched interaction context.

### Default Implementation

Provide a default crafting-table-like interaction implementation for:

- player crafting grids
- crafting table style UIs
- similar input-grid plus result-slot interactions

### Complex Machines

Complex machine integrations may start with:

- context probe
- context guidance
- optional slot schema or machine-specific metadata

If no automatic performer exists, translation inserts a `ManualCheckpointStep` and runtime pauses with explicit guidance.

## Inventory and Capacity Policy

The default overflow strategy is:

1. Before a step that may move items, check whether required player capacity exists.
2. If capacity is insufficient, try to return intermediate or pending outputs to accessible storage.
3. If capacity is still insufficient, pause and guide the player to free space.

This applies both before recipe preparation and after output collection.

Runtime must distinguish at least these pause classes:

- inaccessible storage
- insufficient player capacity
- insufficient resources
- missing interaction context
- manual interaction required
- side capability not satisfied

## Side Requirements

Do not model the executor as globally client-only or server-only. Most first-version execution paths are client-facing, but side requirements belong to steps.

This allows runtime to distinguish:

- client-driven steps
- manually completed player steps
- environment-agnostic checks
- future steps that may be backed by non-client capabilities

## Pause, Resume, and Re-Translation

Pause is normal control flow, not an exceptional path.

`ExecutionSession` should track:

- current execution node and step index
- pause reason
- pending transfers or output returns
- reserved catalysts
- current interaction target and requirement

Resume revalidates the current assumptions and continues from the pause point.

When assumptions are invalidated, prefer:

1. re-translate the current `RoutingPlan`
2. if translation cannot produce a valid executable plan, escalate to re-planning

## Testing Strategy

Prioritize deterministic tests around translation and runtime control flow.

### Translation Tests

- `PlanAction` to interaction step translation
- correct insertion of capacity checks, accessibility checks, and manual checkpoints
- correct flow-tree generation

### Storage Tests

- accessibility checks
- extraction and insertion simulation
- extraction and insertion execution
- fallback to output return before capacity pause

### Interaction Tests

- default crafting-table-like interaction probe, guide, and perform behavior
- manual checkpoint fallback when no performer exists

### Session Tests

- pause and resume
- invalidation and re-translation
- progress node status changes

## Code Style Constraints

Implementation should follow CloudLib conventions rather than introducing a separate execution style.

- Prefer sealed interfaces, sealed classes, and records over enums where payload is required.
- Use lower camel case names for values that would traditionally be written as `static final` constants.
- Organize larger files with region blocks.
- Keep comments concise but explicit about purpose and invariants.
- Prefer typed models over stringly-typed status or reason channels.

## Execution Overlay UI

The execution UI should be independent from the existing calculator result tree. It must be designed as a reusable overlay widget that can be mounted on top of other screens, especially machine GUIs, without changing the execution runtime model.

### Widget Role

- `ExecutionOverlayWidget`
  A standalone left-anchored overlay panel that renders execution progress from session state.
- `ExecutionOverlayViewModel`
  A UI-only projection derived from `ExecutionSession` and `ExecutionFlowViewModel`.
- optional lightweight host helper
  A tiny mounting abstraction so any screen can supply the latest execution session snapshot to the overlay.

### Layout

Use a fixed left-side panel that consumes a bounded percentage of the current screen width, with minimum and maximum width guards so it remains readable inside narrow machine screens.

The panel should have three fixed sections:

- header
  target title, overall status, completed step count, and side requirement hint
- body
  vertically stacked execution tree, where each tree row expands horizontally into that node's linear step lane
- footer
  current step detail, pause guidance, and next-player-action hint

The body scrolls vertically. The header and footer stay fixed.

### Visual Language

The UI should stay close to Minecraft and existing Cirrus visuals rather than using modern rounded timeline dots.

- reuse `CirrusTextures.Background.Outlined.inset` for panel and node chrome
- reuse `CirrusTextures.Background.Sign.bar` and existing flowchart line language for tree trunks and horizontal lane connectors
- prefer small rectangular step segments instead of round progress dots
- prefer existing Cirrus icon and sign assets for state emphasis
- keep lines orthogonal and pixel-like, matching the current flowchart and calculator widgets

### Projection Rules

`ExecutionOverlayViewModel` should not expose raw runtime internals directly. It should derive:

- header summary
  target label, overall status, completed count, total count, current side requirement
- tree rows
  node label, depth, active-path flag, current status, collapsed summary
- linear lanes
  per-node ordered step segments, each with status, emphasis, and compact label if needed
- footer detail
  current step label, pause reason summary, guidance text

The current active branch may be expanded while non-active branches stay compact to preserve space in machine UIs.

### Interaction Scope

The first version is read-only.

- no pause/resume buttons
- no tree editing
- no drag-pan or zoom
- no modal interruption

The overlay should display state and guidance while leaving the host machine UI fully usable.

## Planned File Areas

The main work is expected around:

- `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/`
- `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/`
- `Mod/src/main/java/dev/vfyjxf/cirrus/recipe/storage/`
- `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/routing/plan/`
- `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/...` for the overlay widget and rendering
- `Mod/src/main/java/dev/vfyjxf/cirrus/ui/...` for optional overlay mounting helpers

## Summary

The executor should not be a thin interpreter over `RoutingPlan`. It should be a translation-and-runtime layer that turns planning results into environment-aware, visualizable, pause-resumable interaction steps. That separation keeps routing pure, gives storage and interaction APIs clear responsibilities, and creates a realistic path for later re-translation and re-planning without rewriting the whole system. The dedicated overlay widget then consumes a stable session projection without coupling execution visualization to the existing calculator result UI.
