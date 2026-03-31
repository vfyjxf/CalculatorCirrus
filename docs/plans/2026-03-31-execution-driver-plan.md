# Execution Driver Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Generalize execution UI/logic into a driver-based API with slot skeleton, provide client and server crafting drivers, update UI to consume snapshots/controls, and fix baseline compilation in the same change set.

**Architecture:** Introduce ExecutionDriver + registry + snapshot, move overlay view models into API, implement slot-based driver skeleton and CraftingTableExecutionDriver (client) plus CraftingTableServerExecutionDriver (server). UI binds to driver snapshot/control only. Server driver is enabled via config and takes precedence when available.

**Tech Stack:** Java, NeoForge/Minecraft mod architecture, Eclipse Collections.

---

### Task 1: Add driver API types (core)

**Files:**
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/ExecutionDriver.java
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/ExecutionDriverSnapshot.java
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/ExecutionDriverState.java

**Step 1: Write the failing test**

Create a new test to verify snapshot fields are non-null when session exists.

`java
@Test
public void snapshotContainsSessionAndTelemetry() {
    var driver = new TestDriver();
    var snapshot = driver.snapshot();
    assertNotNull(snapshot.session());
    assertNotNull(snapshot.telemetry());
}
`

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests  *ExecutionDriverSnapshotTest
Expected: FAIL (types not found).

**Step 3: Write minimal implementation**

`java
public interface ExecutionDriver {
    ExecutionSession session();
    ExecutionDriverState state();
    ExecutionDriverSnapshot snapshot();
    void advanceSteps(int steps);
    void pause();
    void resume();
    void toggleAutoAdvance();
    void setRatePerSecond(double rate);
    void reset();
    void jumpToStep(int index);
}
`

`java
public record ExecutionDriverSnapshot(
    ExecutionSession session,
    ExecutionOverlayViewModel viewModel,
    ExecutionTelemetrySnapshot telemetry,
    ImmutableList<ExecutionOverlayController.OverlayMaterialRequirement> materials,
    ExecutionOverlayController.OverlayTargetProgress targetProgress,
    double ratePerSecond,
    boolean autoAdvanceEnabled,
    ReTranslationHint reTranslationHint
) {}
`

`java
public record ExecutionDriverState(
    ExecutionSession session
) {}
`

**Step 4: Run test to verify it passes**

Run: ./gradlew.bat :Mod:test --tests *ExecutionDriverSnapshotTest
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/*.java Mod/src/test/java/**/ExecutionDriverSnapshotTest.java
git commit -m feat: add execution driver core types
`

### Task 2: Driver registry

**Files:**
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/ExecutionDriverRegistry.java
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/ExecutionDriverFactory.java
- Test: Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/ExecutionDriverRegistryTest.java

**Step 1: Write the failing test**

`java
@Test
public void registrySelectsByInteractionIdAndSide() {
    var registry = new ExecutionDriverRegistry();
    registry.register(minecraft:crafting_table, ExecutionSide.client, ctx -> new DummyDriver());
    assertNotNull(registry.find(minecraft:crafting_table, ExecutionSide.client));
}
`

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests *ExecutionDriverRegistryTest
Expected: FAIL.

**Step 3: Write minimal implementation**

Implement map keyed by interactionId + side with fallback to environmentAgnostic.

**Step 4: Run test to verify it passes**

Run: ./gradlew.bat :Mod:test --tests *ExecutionDriverRegistryTest
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/*Registry* Mod/src/test/java/**/ExecutionDriverRegistryTest.java
git commit -m feat: add execution driver registry
`

### Task 3: Slot skeleton API

**Files:**
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/SlotAccess.java
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/SlotLayout.java
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/SlotExecutionDriver.java
- Test: Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/SlotExecutionDriverTest.java

**Step 1: Write the failing test**

`java
@Test
public void slotDriverPausesWhenInteractionClosed() {
    var driver = new SlotExecutionDriver(new FakeSlotAccess(false));
    driver.advanceSteps(1);
    assertTrue(driver.session().paused());
}
`

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests *SlotExecutionDriverTest
Expected: FAIL.

**Step 3: Write minimal implementation**

Implement base class using ExecutionRuntime and SlotAccess to handle OpenInteraction/PrepareInputs/ExecuteRecipe/CollectOutputs.

**Step 4: Run test to verify it passes**

Run: ./gradlew.bat :Mod:test --tests *SlotExecutionDriverTest
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/* Mod/src/test/java/**/SlotExecutionDriverTest.java
git commit -m feat: add slot driver skeleton
`

### Task 4: Crafting table drivers

**Files:**
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/CraftingTableExecutionDriver.java
- Create: Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/CraftingTableServerExecutionDriver.java
- Modify: Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoController.java (rename/migrate)

**Step 1: Write the failing test**

`java
@Test
public void craftingTableDriverExecutesRecipeSteps() {
    var driver = new CraftingTableExecutionDriver(fakeSlotAccess);
    driver.advanceSteps(2);
    assertTrue(driver.session().nextStepIndex() >= 2);
}
`

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests *CraftingTableExecutionDriverTest
Expected: FAIL.

**Step 3: Implement drivers**

- Port WorkbenchExecutionDemoController logic into CraftingTableExecutionDriver.
- Add server throttling behavior in CraftingTableServerExecutionDriver.
- Remove demo/workbench naming.

**Step 4: Run test to verify it passes**

Run: ./gradlew.bat :Mod:test --tests *CraftingTableExecutionDriverTest
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/driver/slot/CraftingTable* Mod/src/test/java/**/CraftingTableExecutionDriverTest.java
 git commit -m feat: add crafting table execution drivers
`

### Task 5: Move overlay view models into API

**Files:**
- Move: Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayViewModel.java -> Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/overlay/ExecutionOverlayViewModel.java
- Move: Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayNode.java -> Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/overlay/ExecutionOverlayNode.java
- Move: Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayLaneSegment.java -> Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/overlay/ExecutionOverlayLaneSegment.java
- Move: Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayNodeKind.java -> Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/overlay/ExecutionOverlayNodeKind.java
- Update imports in UI widget classes.

**Step 1: Write the failing test**

Update existing tests to new package and run them.

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests *ExecutionOverlay*
Expected: FAIL due to package moves.

**Step 3: Move classes and update imports**

**Step 4: Run tests to verify they pass**

Run: ./gradlew.bat :Mod:test --tests *ExecutionOverlay*
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/overlay/* Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/*
 git commit -m refactor: move execution overlay view models to api
`

### Task 6: UI bindings to driver snapshot/control

**Files:**
- Modify: Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java
- Modify: Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayControlsWidget.java
- Modify: Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayController.java (replace with driver interface or remove)

**Step 1: Write failing test**

Update UI tests to use driver snapshot interface and run.

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests *ExecutionOverlay*
Expected: FAIL.

**Step 3: Implement binding changes**

Replace controller dependency with ExecutionDriver + snapshot.

**Step 4: Run test to verify it passes**

Run: ./gradlew.bat :Mod:test --tests *ExecutionOverlay*
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/*
 git commit -m refactor: bind execution overlay to driver snapshot
`

### Task 7: Config for server driver

**Files:**
- Modify: Mod/src/main/java/dev/vfyjxf/cirrus/config/ModConfig.java
- Modify: Mod/src/main/java/dev/vfyjxf/cirrus/CirrusClient.java or overlay manager to use config

**Step 1: Write failing test**

Add config unit test if available; otherwise use a small runtime check in existing tests.

**Step 2: Run test to verify it fails**

Run: ./gradlew.bat :Mod:test --tests *Config*
Expected: FAIL.

**Step 3: Implement config keys**

Add execution.serverDriver.enabled and execution.serverDriver.interactionWhitelist.

**Step 4: Run tests**

Run: ./gradlew.bat :Mod:test --tests *Config*
Expected: PASS.

**Step 5: Commit**

`ash
git add Mod/src/main/java/dev/vfyjxf/cirrus/config/ModConfig.java
 git commit -m feat: add server driver config
`

### Task 8: Baseline compilation fixes

**Files:**
- Modify: any failing files discovered by build

**Step 1: Run full build**

Run: ./gradlew.bat :Mod:test
Expected: FAIL with baseline errors.

**Step 2: Fix errors incrementally**

Apply minimal fixes to compile.

**Step 3: Re-run build**

Run: ./gradlew.bat :Mod:test
Expected: PASS.

**Step 4: Commit**

`ash
git add -A
 git commit -m fix: baseline compile
`

---

Plan complete and saved to docs/plans/2026-03-31-execution-driver-plan.md. Two execution options:

1. Subagent-Driven (this session) - I dispatch fresh subagent per task, review between tasks, fast iteration
2. Parallel Session (separate) - Open new session with executing-plans, batch execution with checkpoints

Which approach?

