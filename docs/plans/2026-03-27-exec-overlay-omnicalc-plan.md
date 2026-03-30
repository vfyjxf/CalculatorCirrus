# Execution Overlay OmniCalc-Style Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver an OmniCalc-style execution overlay focused on status/progress/speed/error observation, with a standalone temporary screen opened by `/cirrus exec_overlay`.

**Architecture:** Keep execution core APIs intact and add a UI-agnostic telemetry snapshot contract as the boundary. Rebuild overlay component/render/controls layers on top of a projection model aligned with existing OmniCalc tree + milestones style. Keep workbench overlay compatible, but make standalone screen the main debugging/experience entry.

**Tech Stack:** Java (NeoForge), CloudLib UI, Cirrus textures/i18n, JUnit 5, ModDev usage capture workflow.

---

**Implementation rules for this plan**
- Follow @superpowers:test-driven-development and @superpowers:verification-before-completion.
- Keep UI style aligned with existing `TreeViewWidget` and `MilestonesViewWidget`.
- Use `/cirrus exec_overlay` and do not use hotswap tooling.

### Task 1: Add UI-Agnostic Execution Telemetry Contract

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionTelemetrySnapshot.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayController.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoController.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionTelemetrySnapshotContractTest.java`

**Step 1: Write the failing test**

```java
@Test
void snapshotCarriesStatusProgressContextAndErrorFields() {
    var snapshot = new ExecutionTelemetrySnapshot(
        "running", false, false,
        3, 10, 0.3f,
        "step-a", "Assemble Rotor", "LV Assembler", 120,
        "step-b", "Prepare Casing",
        "step-root", "Target Machine",
        Lists.immutable.of(new ExecutionTelemetrySnapshot.PathNode("step-root", "Target Machine")),
        Lists.immutable.of("Low stock"),
        null, null,
        5.0, true
    );
    assertEquals("running", snapshot.statusText());
    assertEquals(10, snapshot.totalSteps());
    assertEquals(5.0, snapshot.ratePerSecond());
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionTelemetrySnapshotContractTest\" --no-daemon"`  
Expected: FAIL (`ExecutionTelemetrySnapshot` not found)

**Step 3: Write minimal implementation**

```java
public record ExecutionTelemetrySnapshot(
    String statusText, boolean paused, boolean completed,
    int completedSteps, int totalSteps, float progressPercent,
    @Nullable String currentStepId, String currentName, @Nullable String currentMachine, @Nullable Integer currentDurationTicks,
    @Nullable String nextStepId, @Nullable String nextName,
    @Nullable String parentStepId, @Nullable String parentName,
    ImmutableList<PathNode> stageGoalPath,
    ImmutableList<String> warnings,
    @Nullable String errorSummary, @Nullable String errorDetail,
    double ratePerSecond, boolean autoAdvanceEnabled
) {
    public record PathNode(String id, String name) {}
}
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionTelemetrySnapshotContractTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionTelemetrySnapshot.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayController.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoController.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/api/recipe/execution/ExecutionTelemetrySnapshotContractTest.java
git commit -m "feat: add ui-agnostic execution telemetry snapshot"
```

### Task 2: Add Continuous Speed Control in Controller Layer

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayController.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoController.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoControllerTest.java`

**Step 1: Write the failing test**

```java
@Test
void setRatePerSecondZeroPausesAndPositiveResumesAutoAdvance() {
    var controller = new WorkbenchExecutionDemoController();
    controller.overlaySetRatePerSecond(0.0);
    assertTrue(controller.executionOverlaySession().paused());
    controller.overlaySetRatePerSecond(6.5);
    assertTrue(controller.overlayAutoAdvanceEnabled());
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*WorkbenchExecutionDemoControllerTest\" --no-daemon"`  
Expected: FAIL (`overlaySetRatePerSecond` missing)

**Step 3: Write minimal implementation**

```java
public interface ExecutionOverlayController {
    void overlaySetRatePerSecond(double value);
    double overlayRatePerSecond();
}
```

```java
@Override
public void overlaySetRatePerSecond(double value) {
    double clamped = Math.max(0.0, Math.min(10.0, value));
    this.ratePerSecond = clamped;
    if (clamped == 0.0) {
        session = session.pause(PauseReasons.userPaused, session.pauseReason());
        autoAdvance = false;
    } else {
        autoAdvance = true;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*WorkbenchExecutionDemoControllerTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayController.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoController.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionDemoControllerTest.java
git commit -m "feat: add continuous overlay speed control to controller"
```

### Task 3: Rework Overlay ViewModel Projection from Telemetry

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayViewModel.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayViewModelContractTest.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidgetContractTest.java`

**Step 1: Write the failing test**

```java
@Test
void projectionIncludesInfoPanelContextPathAndRateReadout() {
    var controller = new WorkbenchExecutionDemoController();
    controller.overlaySetRatePerSecond(5.0);
    var model = ExecutionOverlayViewModel.derive(controller.executionOverlaySession());
    assertNotNull(model.infoPanel());
    assertFalse(model.pathContext().isEmpty());
    assertEquals("5.0/s", model.rateText());
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlayViewModelContractTest\" --tests \"*ExecutionOverlayWidgetContractTest\" --no-daemon"`  
Expected: FAIL (missing fields in projection)

**Step 3: Write minimal implementation**

```java
public record ExecutionOverlayViewModel(
    ...,
    ExecutionInfoPanelModel infoPanel,
    ImmutableList<PathContextNode> pathContext,
    String rateText
) {}
```

```java
String rateText = String.format(Locale.ROOT, "%.1f/s", telemetry.ratePerSecond());
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlayViewModelContractTest\" --tests \"*ExecutionOverlayWidgetContractTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayViewModel.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlayViewModelContractTest.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidgetContractTest.java
git commit -m "feat: project telemetry into overlay info and path models"
```

### Task 4: Rebuild Controls Row with Slider + Existing Actions

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayControlsWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayComponentRenderer.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlaySandboxScreenContractTest.java`

**Step 1: Write the failing test**

```java
@Test
void controlsExposeRateReadoutAndPreserveStepBurstAutoReset() {
    var controller = new WorkbenchExecutionDemoController();
    var controls = new ExecutionOverlayControlsWidget(controller, () -> {}, 24);
    controls.debugSetRate(7.5);
    assertEquals("7.5/s", controls.debugRateText());
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlaySandboxScreenContractTest\" --no-daemon"`  
Expected: FAIL (`debugSetRate`/rate UI absent)

**Step 3: Write minimal implementation**

```java
// Keep existing buttons, append slider + readout:
rateSlider.onValueChanged(value -> controller.overlaySetRatePerSecond(value));
rateLabel.setText(Component.literal(String.format(Locale.ROOT, "%.1f/s", controller.overlayRatePerSecond())));
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlaySandboxScreenContractTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayControlsWidget.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayComponentRenderer.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlaySandboxScreenContractTest.java
git commit -m "feat: add continuous speed slider to execution overlay controls"
```

### Task 5: Align Overlay Layout & Visual Hierarchy with OmniCalc Tree/Milestones

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayComponentRenderer.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayLayout.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidgetContractTest.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayLayoutTest.java`

**Step 1: Write the failing test**

```java
@Test
void infoPanelUsesBoundedWidthRatioAndPathBarStaysBottomAnchored() {
    assertEquals(188, ExecutionOverlayWidget.infoPanelWidth(700));
    assertTrue(ExecutionOverlayWidget.pathBarYOffset(320) > 0);
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlayWidgetContractTest\" --tests \"*WorkbenchExecutionOverlayLayoutTest\" --no-daemon"`  
Expected: FAIL (helper/layout contracts missing)

**Step 3: Write minimal implementation**

```java
private static final float infoPanelWidthRatio = 0.31f;
private static final int infoPanelMinWidth = 96;
private static final int infoPanelMaxWidth = 188;
```

```java
// Draw order:
// 1) header
// 2) tree canvas
// 3) top-right info panel
// 4) bottom path bar
// 5) footer summary
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlayWidgetContractTest\" --tests \"*WorkbenchExecutionOverlayLayoutTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayComponentRenderer.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayLayout.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidgetContractTest.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayLayoutTest.java
git commit -m "feat: align execution overlay layout with omnicalc tree style"
```

### Task 6: Add `/cirrus exec_overlay` Entry and Non-Conflicting Dev Hotkey Fallback

**Files:**
- Create: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayCommandEntry.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/CirrusClient.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlaySandboxScreen.java`
- Modify: `Mod/src/main/resources/assets/calculator_cirrus/lang/en_us.json`
- Modify: `Mod/src/main/resources/assets/calculator_cirrus/lang/zh_cn.json`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlaySandboxScreenContractTest.java`

**Step 1: Write the failing test**

```java
@Test
void sandboxEntryDefinesCirrusExecOverlayCommandPath() {
    assertEquals("cirrus exec_overlay", ExecutionOverlayCommandEntry.debugCommandPath());
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlaySandboxScreenContractTest\" --no-daemon"`  
Expected: FAIL (`ExecutionOverlayCommandEntry` missing)

**Step 3: Write minimal implementation**

```java
public final class ExecutionOverlayCommandEntry {
    public static String debugCommandPath() { return "cirrus exec_overlay"; }
    public static void openSandbox() { Minecraft.getInstance().setScreen(new ExecutionOverlaySandboxScreen()); }
}
```

```java
// Register client command:
// /cirrus exec_overlay -> openSandbox()
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlaySandboxScreenContractTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayCommandEntry.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/CirrusClient.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlaySandboxScreen.java \
        Mod/src/main/resources/assets/calculator_cirrus/lang/en_us.json \
        Mod/src/main/resources/assets/calculator_cirrus/lang/zh_cn.json \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlaySandboxScreenContractTest.java
git commit -m "feat: add /cirrus exec_overlay sandbox entry"
```

### Task 7: Aggressively Remove Legacy UI-Only Overlay Branches

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayManager.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java`
- Delete/Modify: any obsolete execution overlay widget helpers no longer referenced
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidgetContractTest.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayLayoutTest.java`

**Step 1: Write the failing test**

```java
@Test
void workbenchManagerUsesSharedControlsWidgetInsteadOfInlineLegacyButtons() {
    assertTrue(WorkbenchExecutionOverlayManager.debugUsesSharedControlsWidget());
}
```

**Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlayWidgetContractTest\" --tests \"*WorkbenchExecutionOverlayLayoutTest\" --no-daemon"`  
Expected: FAIL (debug hook absent)

**Step 3: Write minimal implementation**

```java
// Remove duplicated inline button setup from manager state.
// Keep single path via ExecutionOverlayControlsWidget.
```

**Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlayWidgetContractTest\" --tests \"*WorkbenchExecutionOverlayLayoutTest\" --no-daemon"`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayManager.java \
        Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidgetContractTest.java \
        Mod/src/test/java/dev/vfyjxf/cirrus/ui/execution/workbench/WorkbenchExecutionOverlayLayoutTest.java
git commit -m "refactor: remove legacy overlay ui branches and unify controls"
```

### Task 8: Full Verification + ModDev Capture Checklist

**Files:**
- Modify: `docs/plans/2026-03-27-exec-overlay-omnicalc-design.md`
- Modify: `docs/plans/2026-03-27-exec-overlay-omnicalc-plan.md`
- Create: `docs/plans/screenshots/execution-overlay/2026-03-27/README.md`

**Step 1: Run focused automated tests**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --tests \"*ExecutionOverlay*\" --tests \"*WorkbenchExecution*\" --no-daemon"`  
Expected: PASS

**Step 2: Run full module tests**

Run: `pwsh -NoProfile -Command "$env:GRADLE_USER_HOME='.gradle-user'; .\gradlew.bat :Mod:test --no-daemon"`  
Expected: PASS

**Step 3: Execute moddev usage capture flow**

```text
1. Launch client in maximized windowed mode (not fullscreen).
2. Open temporary screen via /cirrus exec_overlay.
3. Capture: initial / running / paused / failed.
4. Save outputs under docs/plans/screenshots/execution-overlay/2026-03-27/.
5. Document visual diffs and final adjustments.
```

**Step 4: Update docs with verification evidence**

```markdown
- Commands executed
- Test pass summary
- Capture file list
- Remaining known risks
```

**Step 5: Commit**

```bash
git add docs/plans/2026-03-27-exec-overlay-omnicalc-design.md \
        docs/plans/2026-03-27-exec-overlay-omnicalc-plan.md \
        docs/plans/screenshots/execution-overlay/2026-03-27
git commit -m "docs: record execution overlay verification and capture evidence"
```

