# OmniCalc Calculator Cirrus Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move the `omnicalc` calculator experience into the calculator tab content in `TabContainer`, keep the omnicalc layout intact, and replace the remaining web-style calculator UI with Cirrus-native textures and missing atlas slices.

**Architecture:** Keep `dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator` as the single source of truth for the migrated calculator UI. Integrate that view into [`TabContainer`](D:\ProjectDir\CloudMods\CalculatorCirrus\Mod\src\main\java\dev\vfyjxf\cirrus\ui\widget\TabContainer.java), then patch each omnicalc subview in place so Tree keeps its current interaction logic while Matrix, Milestones, the control bar, and chrome are upgraded from placeholder styling to Cirrus-native rendering. Asset work stays inside the existing `assets/calculator_cirrus/textures/cirrus/gui/...` tree and is registered only through [`CirrusTextures.java`](D:\ProjectDir\CloudMods\CalculatorCirrus\Mod\src\main\java\dev\vfyjxf\cirrus\ui\CirrusTextures.java).

**Tech Stack:** Java, NeoForge, CloudLib UI canvas/widgets, Cirrus NineSlice/Image textures, Gradle, ModDevMCP request API

---

### Task 1: Add a regression test for the calculator tab host and swap the tab to omnicalc content

**Files:**
- Create: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/TabContainerCalculatorContractTest.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/TabContainer.java`
- Read: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/calc/CalculatorWidget.java`
- Read: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/OmniCalculatorView.java`

**Step 1: Write the failing test**

Create a contract test that asserts the calculator tab factory no longer builds the old calculator workflow and instead mounts the omnicalc calculator view type.

```java
class TabContainerCalculatorContractTest {
    @Test
    void calculatorTabBuildsOmniCalcView() {
        TabContainer container = new TabContainer();
        Widget calculatorContent = container.debugCreateCalculatorContent();
        assertThat(calculatorContent).isInstanceOf(OmniCalculatorView.class);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "*TabContainerCalculatorContractTest" --no-daemon`  
Expected: FAIL because `TabContainer` does not expose a calculator content factory and still wires `CalculatorWidget`.

**Step 3: Write minimal implementation**

- Add a small package-visible or public debug/helper method in `TabContainer` that builds the calculator tab content without simulating mouse input.
- Change the calculator tab supplier from `CalculatorWidget::new` to `OmniCalculatorView::new` or a thin host wrapper if sizing/padding needs adaptation.
- Keep other tabs untouched.

```java
Widget debugCreateCalculatorContent() {
    return new OmniCalculatorView();
}
```

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "*TabContainerCalculatorContractTest" --no-daemon`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/TabContainer.java Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/TabContainerCalculatorContractTest.java
git commit -m "test: mount omnicalc calculator in tab container"
```

### Task 2: Define calculator view state tests and patch the omnicalc control shell without changing layout

**Files:**
- Create: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/OmniCalculatorViewContractTest.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/OmniCalculatorView.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/ControlBarWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/ViewSwitcherWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/OmniCalcSidebar.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/OmniCalcColors.java`

**Step 1: Write the failing test**

Add a view contract test around state transitions that should remain stable while the shell styling changes:

```java
class OmniCalculatorViewContractTest {
    @Test
    void calculateSwitchesFromEmptyStateToTreeResultView() {
        OmniCalculatorView view = new OmniCalculatorView();
        assertThat(view.isCalculated()).isFalse();
        view.onCalculate();
        assertThat(view.isCalculated()).isTrue();
        assertThat(view.currentProcessView()).isEqualTo(OmniCalculatorView.ProcessView.TREE);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "*OmniCalculatorViewContractTest" --no-daemon`  
Expected: FAIL because the accessors and/or action visibility are not test-friendly enough.

**Step 3: Write minimal implementation**

- Keep the existing layout hierarchy in `OmniCalculatorView`.
- Make only the smallest visibility adjustments needed for the contract test and for later in-place patching.
- Replace control-bar, switcher, and sidebar rendering with Cirrus-native backgrounds/tabs/icons using `CirrusTextures`.
- Do not introduce a new layout container or move the resource list.

Focus changes:

- `ControlBarWidget`: replace rounded web pills with frame/inset/slot/tab textures and Cirrus icons.
- `ViewSwitcherWidget`: switch from text pills to calculator-specific texture-backed tabs.
- `OmniCalcSidebar`: keep current nav structure, but replace hand-drawn glyph presentation with Cirrus calculator/database/context/settings tab art where available.
- `OmniCalcColors`: keep only colors still needed as semantic tint values.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "*OmniCalculatorViewContractTest" --no-daemon`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/OmniCalculatorViewContractTest.java
git commit -m "feat: restyle omnicalc calculator shell for cirrus"
```

### Task 3: Register missing atlas slices from the root image sheets before using them in calculator views

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/CirrusTextures.java`
- Create: new PNG slices under `Mod/src/main/resources/assets/calculator_cirrus/textures/cirrus/gui/...`
- Read from source atlases:
  - `D:\ProjectDir\CloudMods\gui_output (1).png`
  - `D:\ProjectDir\CloudMods\Untitled 03-15-2026 12-45-49.png`

**Step 1: Write the failing test**

Add a lightweight resource contract test that asserts the new calculator texture constants resolve to non-null textures after registration.

```java
class CirrusCalculatorTextureContractTest {
    @Test
    void omnicalcSupplementalTexturesAreRegistered() {
        assertThat(CirrusTextures.Icon.Calc.Tree.tabSelected).isNotNull();
        assertThat(CirrusTextures.Icon.Calc.Task.trackSelected).isNotNull();
        assertThat(CirrusTextures.Toggle.Checked.Small.select).isNotNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "*CirrusCalculatorTextureContractTest" --no-daemon`  
Expected: FAIL once the test references new constants that are not registered yet.

**Step 3: Write minimal implementation**

- Cut only the slices needed by the calculator migration.
- Place them in the existing resource tree, following current naming rules.
- Register them in `CirrusTextures.java`.
- Prefer extending existing nested classes instead of introducing a parallel texture registry.

Likely additions:

- extra task/matrix/checklist icons
- missing tab edge states
- list row state markers
- small track/check/expand affordances
- thin separators / scrollbar or status pieces if the current textures do not cover the web placeholder states cleanly

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "*CirrusCalculatorTextureContractTest" --no-daemon`  
Expected: PASS

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/CirrusTextures.java Mod/src/main/resources/assets/calculator_cirrus/textures/cirrus/gui
git commit -m "feat: add cirrus calculator atlas slices"
```

### Task 4: Patch Tree view styling in place without touching its interaction model

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/TreeViewWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/TreeCanvasWidget.java`
- Optionally modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/flowchart/RecipeStepsWidget.java` only if shared rendering helpers are reused safely

**Step 1: Write the failing test**

Add a regression contract around Tree view state, not pixels:

```java
class TreeCanvasWidgetContractTest {
    @Test
    void togglingMaterialPanelDoesNotResetViewport() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setViewport(120, 240, 0.75f);
        widget.toggleMaterialPanel();
        assertThat(widget.getPanX()).isEqualTo(120);
        assertThat(widget.getPanY()).isEqualTo(240);
        assertThat(widget.getZoom()).isEqualTo(0.75f);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "*TreeCanvasWidgetContractTest" --no-daemon`  
Expected: FAIL if the current API is not exposed cleanly enough for regression coverage.

**Step 3: Write minimal implementation**

- Keep all node placement, zoom, pan, collapse, minimap, breadcrumb, and material list logic intact.
- Replace web-style surface drawing with Cirrus texture-backed panels where it is safe:
  - layer nav chrome
  - bottom breadcrumb bars
  - material list headers and rows
  - search field shell
  - scrollbars and state markers
- Do not move the material/resource list to another widget.
- Do not alter world-space hit boxes unless a texture swap forces exact padding fixes.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "*TreeCanvasWidgetContractTest" --no-daemon`  
Expected: PASS

**Step 4.5: Run in-game Tree verification through ModDevMCP**

- Resolve a live ModDevMCP base URL with `status.get`
- If needed, run `hotswap.reload` with compile enabled
- Open the calculator tab in-game
- Use `ui.inspect` to confirm the Tree view hierarchy exists
- Use `ui.capture` to export a framebuffer or auto capture for visual review

Expected:

- Tree screen mounts inside the calculator tab host
- layout is intact
- material/resource list still exists in place
- no obvious broken hit areas or missing chrome

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/TreeViewWidget.java Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/TreeCanvasWidget.java Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/TreeCanvasWidgetContractTest.java
git commit -m "feat: reskin omnicalc tree view with cirrus assets"
```

### Task 5: Replace Matrix and Milestones placeholder rendering with Cirrus-styled omnicalc-aligned layouts

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/MatrixViewWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/MilestonesViewWidget.java`
- Optionally create: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/MatrixMilestonesContractTest.java`

**Step 1: Write the failing test**

Add a simple contract test that verifies both views construct without throwing and expose their expected root layout semantics.

```java
class MatrixMilestonesContractTest {
    @Test
    void matrixAndMilestonesViewsConstruct() {
        assertThat(new MatrixViewWidget()).isNotNull();
        assertThat(new MilestonesViewWidget()).isNotNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "*MatrixMilestonesContractTest" --no-daemon`  
Expected: FAIL only if constructor changes require missing resources or invalid texture references.

**Step 3: Write minimal implementation**

- Keep the `omnicalc-pro` layout structure:
  - Matrix: title band, table header, expandable/interactive row look
  - Milestones: top progress summary, phase columns, task cards
- Replace rounded web drawing with Cirrus textures, slots, signs, and state icons.
- Use the current calculator-side data and placeholders where real data is still absent, but make the layout read as the final omnicalc page rather than a demo card.
- Reuse the newly registered atlas slices instead of adding ad-hoc canvas glyphs.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "*MatrixMilestonesContractTest" --no-daemon`  
Expected: PASS

**Step 4.5: Run in-game Matrix and Milestones verification through ModDevMCP**

- Use `hotswap.reload` after code changes
- Open calculator tab and switch views in-game
- Use `ui.inspect` to verify the active screen/widget tree after each switch
- Use `ui.capture` to export proof images for Matrix and Milestones

Expected:

- view switching works inside the fixed omnicalc layout
- Matrix and Milestones render with Cirrus-styled chrome instead of placeholder cards
- right-side material/resource area remains present and stable across view switches

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/MatrixViewWidget.java Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/MilestonesViewWidget.java Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/MatrixMilestonesContractTest.java
git commit -m "feat: finish omnicalc matrix and milestones views"
```

### Task 6: Verify the full calculator tab path and remove obvious dead-path drift

**Files:**
- Modify if needed: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/OmniCalcScreen.java`
- Optionally modify: stale comments or docs that still claim the old calculator tab hosts `CalculatorWidget`
- Modify: `docs/plans/2026-03-22-omnicalc-calculator-cirrus-design.md`
- Modify: `docs/plans/2026-03-22-omnicalc-calculator-cirrus.md`

**Step 1: Run focused tests**

Run: `.\gradlew.bat :Mod:test --tests "*TabContainerCalculatorContractTest" --tests "*OmniCalculatorViewContractTest" --tests "*TreeCanvasWidgetContractTest" --tests "*MatrixMilestonesContractTest" --tests "*CirrusCalculatorTextureContractTest" --no-daemon`  
Expected: PASS

**Step 2: Run compile verification**

Run: `.\gradlew.bat compileJava compileTestJava --no-daemon`  
Expected: BUILD SUCCESSFUL

**Step 2.5: Run final ModDevMCP UI verification**

- Resolve service with `status.get`
- Confirm `gameReady=true` and client side is connected
- Reload changed classes with `hotswap.reload`
- Inspect the live screen with `ui.inspect`
- Export final captures with `ui.capture`

Expected:

- calculator tab hosts omnicalc content
- Tree / Matrix / Milestones all render in the same fixed layout
- Cirrus textures are visible in the control shell and subviews
- no obvious missing atlas slices remain

**Step 3: Fix drift only if verification exposes it**

- Remove or update comments that still describe the calculator tab as the old `CalculatorWidget`.
- Update `OmniCalcScreen` comments if it remains as a debug-only entry.
- Do not do opportunistic cleanup outside the migrated calculator path.

**Step 4: Re-run verification**

Run: `.\gradlew.bat :Mod:test --tests "*TabContainerCalculatorContractTest" --tests "*OmniCalculatorViewContractTest" --tests "*TreeCanvasWidgetContractTest" --tests "*MatrixMilestonesContractTest" --tests "*CirrusCalculatorTextureContractTest" --no-daemon`  
Expected: PASS

Run: `.\gradlew.bat compileJava compileTestJava --no-daemon`  
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget docs/plans
git commit -m "feat: finish omnicalc calculator migration into tab container"
```
