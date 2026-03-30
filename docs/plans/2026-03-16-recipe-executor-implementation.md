# Recipe Executor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a routing-to-execution translation layer, runtime-facing storage and interaction APIs, and a tree-shaped execution progress model for Cirrus recipe execution.

**Architecture:** Keep routing pure and introduce `ExecutionTranslator` plus an in-memory `InteractionExecutionPlan` for runtime behavior. Upgrade storage and interaction boundaries into execution-time capability APIs, then expose a separate flow-tree view model so runtime order and UI structure stay decoupled.

**Tech Stack:** Java records and sealed types, NeoForge Minecraft client/runtime APIs, existing Cirrus routing and storage packages, CloudLib-style region blocks and typed keys.

---

### Task 1: Lock Down Current Planning and Execution Surface

**Files:**
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/`
- Read: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/routing/plan/PlanAction.java`
- Read: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageApi.java`
- Read: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/`

**Step 1: Write the failing translator contract tests**

Create focused tests that describe the expected translation of:
- consume stock into accessibility plus withdraw flow
- reserve catalyst into accessibility plus reserve flow
- execute recipe into interaction plus output handling flow
- manual checkpoint insertion when no performer exists

**Step 2: Run the targeted execution tests to verify failure**

Run: `.\gradlew.bat :Mod:test --tests "*recipe.execution*"`
Expected: FAIL because translator and flow types are incomplete or missing.

**Step 3: Add minimal test fixtures for mock storage, mock interaction, and sample plan actions**

Keep fixtures local to execution tests and avoid touching production code yet.

**Step 4: Re-run the targeted tests**

Run: `.\gradlew.bat :Mod:test --tests "*recipe.execution*"`
Expected: FAIL on missing production types rather than unclear fixture errors.

**Step 5: Commit**

```bash
git add Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "test: define recipe execution translator contracts"
```

### Task 2: Introduce the Execution Plan and Step Models

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/InteractionExecutionPlan.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/InteractionStep.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionSideRequirement.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionCapability.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/...`

**Step 1: Write the failing model tests**

Cover:
- step ordering and required side capture
- typed step payloads for accessibility, capacity, execution, and manual checkpoints
- no version fields or serialization assumptions in the execution plan

**Step 2: Run the targeted model tests**

Run: `.\gradlew.bat :Mod:test --tests "*InteractionExecutionPlan*" --tests "*InteractionStep*"`
Expected: FAIL on missing records and sealed hierarchies.

**Step 3: Implement the minimal sealed interfaces and records**

Use CloudLib-style naming and region blocks. Prefer records for immutable payloads.

**Step 4: Re-run the targeted model tests**

Run: `.\gradlew.bat :Mod:test --tests "*InteractionExecutionPlan*" --tests "*InteractionStep*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "feat: add interaction execution plan models"
```

### Task 3: Add the Execution Flow Tree Model for Visualization

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/view/ExecutionFlowNode.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/view/ExecutionFlowViewModel.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/view/ExecutionFlowStatus.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/...`

**Step 1: Write the failing flow-tree tests**

Describe a translated recipe execution tree with:
- root target node
- one recipe execution branch
- nested acquire, interact, execute, and output nodes
- status changes derived from runtime step state

**Step 2: Run the targeted flow tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionFlow*"`
Expected: FAIL because flow view types do not exist.

**Step 3: Implement the minimal flow-tree records and status type hierarchy**

Model the tree for UI consumption, not runtime mutation internals.

**Step 4: Re-run the targeted flow tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionFlow*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/view Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "feat: add execution flow tree model"
```

### Task 4: Upgrade StorageApi to a Runtime Capability Boundary

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageApi.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageSource.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageAccessibility.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageTransferRequest.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageTransferResult.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage/StorageGuidance.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/recipe/storage/StorageApiImpl.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/recipe/storage/source/PlayerInventorySource.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/...`

**Step 1: Write failing storage runtime tests**

Cover:
- source accessibility reporting
- provider and receiver selection by resource
- simulation of insert and extract
- execution fallback that stores outputs before pausing for capacity

**Step 2: Run the targeted storage tests**

Run: `.\gradlew.bat :Mod:test --tests "*Storage*Execution*" --tests "*StorageApi*"`
Expected: FAIL because runtime transfer APIs are absent.

**Step 3: Add request/result/accessibility types and extend the API**

Keep registration concerns separate from runtime transfer concerns where possible.

**Step 4: Update `StorageApiImpl` and `PlayerInventorySource` minimally**

Implement only what is required to satisfy the tests and first execution path.

**Step 5: Re-run the targeted storage tests**

Run: `.\gradlew.bat :Mod:test --tests "*Storage*Execution*" --tests "*StorageApi*"`
Expected: PASS.

**Step 6: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/storage Mod/src/main/java/dev/vfyjxf/cirrus/recipe/storage Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "feat: add runtime storage execution capabilities"
```

### Task 5: Add the Generic InteractionApi and Default Crafting-Table-Like Support

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction/InteractionApi.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction/InteractionContext.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction/InteractionProbeResult.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction/InteractionGuideResult.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction/InteractionPerformResult.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction/CraftingTableLikeInteraction.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/...`

**Step 1: Write failing interaction contract tests**

Cover:
- probe, guide, and perform contract behavior
- fallback to manual checkpoint when no performer is registered
- successful default crafting-table-like execution for a simple recipe layout fixture

**Step 2: Run the targeted interaction tests**

Run: `.\gradlew.bat :Mod:test --tests "*InteractionApi*" --tests "*CraftingTableLike*"`
Expected: FAIL because interaction abstractions are missing.

**Step 3: Implement the minimal interaction interfaces and default crafting-table-like adapter**

Favor typed result records over enums with optional fields.

**Step 4: Re-run the targeted interaction tests**

Run: `.\gradlew.bat :Mod:test --tests "*InteractionApi*" --tests "*CraftingTableLike*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/interaction Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "feat: add interaction api and crafting table support"
```

### Task 6: Implement the ExecutionTranslator

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionTranslator.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/TranslationNote.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ReTranslationHint.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/routing/plan/PlanAction.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/...`

**Step 1: Write failing translator integration tests**

Use representative plans to assert:
- consume actions become accessibility plus withdraw flows
- reserve actions become hold-plus-reserve flows
- execute actions inject capacity, interaction, collect, and store steps
- manual checkpoints appear when the interaction can be guided but not performed
- flow-tree nodes align with translated recipe structure

**Step 2: Run the targeted translator tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionTranslator*" --tests "*RoutingPlanActionsTest*"`
Expected: FAIL because translation logic is absent.

**Step 3: Implement the translator with small private helpers per action kind**

Keep routing types pure. Do not push runtime-specific checks back into planner models.

**Step 4: Re-run the targeted translator tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionTranslator*" --tests "*RoutingPlanActionsTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "feat: translate routing plans into interaction execution plans"
```

### Task 7: Implement Session, Pause, Resume, and Re-Translation Triggers

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionSession.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/PauseReason.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionRuntime.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/util/ExecutionGuidance.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/...`

**Step 1: Write failing runtime session tests**

Cover:
- normal step advancement
- pause on missing capacity
- pause on inaccessible storage
- pause on manual interaction requirement
- resume from pause
- invalidation leading to re-translation request

**Step 2: Run the targeted session tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionSession*" --tests "*ExecutionRuntime*"`
Expected: FAIL because runtime/session types do not exist.

**Step 3: Implement the minimal runtime loop and pause state records**

Keep pause as normal control flow and keep session state in-memory only.

**Step 4: Re-run the targeted session tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionSession*" --tests "*ExecutionRuntime*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution
git commit -m "feat: add recipe execution runtime and session control"
```

### Task 8: Add the Overlay Projection Model for Execution UI

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayViewModel.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayNode.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayLaneSegment.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/...`

**Step 1: Write failing overlay projection tests**

Describe:
- header summary derived from `ExecutionSession`
- tree rows with active-branch emphasis
- horizontal lane segment projection per execution node
- footer guidance derived from pause state and current step

**Step 2: Run the targeted UI tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionOverlayViewModel*"`
Expected: FAIL because the overlay projection types do not exist.

**Step 3: Implement the minimal overlay projection types**

Keep the projection read-only and separate from runtime mutation state.

**Step 4: Re-run the targeted UI tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionOverlayViewModel*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution Mod/src/test/java/dev/vfyjxf/cirrus/ui
git commit -m "feat: add execution overlay view model"
```

### Task 9: Implement the Standalone Execution Overlay Widget

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java`
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayHost.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/CirrusTextures.java`
- Modify: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/...`

**Step 1: Write failing overlay widget tests**

Describe:
- left-side bounded overlay sizing
- fixed header and footer layout contracts
- body row rendering driven by projected tree nodes
- read-only pause guidance rendering

**Step 2: Run the targeted widget tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionOverlayWidget*"`
Expected: FAIL because the overlay widget types do not exist.

**Step 3: Implement the minimal standalone overlay widget**

Reuse existing Cirrus textures and flowchart line language. Do not bind it to the calculator result tree.

**Step 4: Re-run the targeted widget tests**

Run: `.\gradlew.bat :Mod:test --tests "*ExecutionOverlayWidget*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution Mod/src/main/java/dev/vfyjxf/cirrus/ui/CirrusTextures.java Mod/src/test/java/dev/vfyjxf/cirrus/ui
git commit -m "feat: add standalone execution overlay widget"
```

### Task 9: Run Focused Verification and Clean Up the API Surface

**Files:**
- Modify: touched files only if failures require minimal cleanup
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/`

**Step 1: Run focused execution and UI suites**

Run: `.\gradlew.bat :Mod:test --tests "*recipe.execution*" --tests "*ExecutionFlow*" --tests "*StorageApi*" --tests "*InteractionApi*"`
Expected: PASS.

**Step 2: Run any routing tests that overlap with plan translation**

Run: `.\gradlew.bat :Mod:test --tests "*RoutingPlanActionsTest*" --tests "*RoutingContractsTest*"`
Expected: PASS.

**Step 3: Make only minimal cleanup edits required by failures**

Do not broaden scope beyond execution, translation, storage, interaction, and progress visualization.

**Step 4: Re-run the same focused verification**

Run: `.\gradlew.bat :Mod:test --tests "*recipe.execution*" --tests "*ExecutionFlow*" --tests "*StorageApi*" --tests "*InteractionApi*" --tests "*RoutingPlanActionsTest*" --tests "*RoutingContractsTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus Mod/src/test/java/dev/vfyjxf/cirrus
git commit -m "test: verify recipe executor integration"
```

### Task 10: Final Documentation Pass

**Files:**
- Modify: `docs/plans/2026-03-16-recipe-executor-design.md`
- Modify: `docs/plans/2026-03-16-recipe-executor-implementation.md`
- Optionally modify: relevant package docs under `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/.../package-info.java`

**Step 1: Update docs if implementation changed any planned names**

Keep the design and implementation plan aligned with the final code surface.

**Step 2: Run a final `git diff --stat` review**

Run: `git diff --stat`
Expected: only intended execution, storage, interaction, visualization, and doc changes.

**Step 3: Commit**

```bash
git add docs/plans Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/**/package-info.java
git commit -m "docs: finalize recipe executor design notes"
```
