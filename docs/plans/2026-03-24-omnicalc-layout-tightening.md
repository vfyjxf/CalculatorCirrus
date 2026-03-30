# OmniCalc Layout Tightening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Tighten OmniCalc calculator layout toward the fixed-pixel, dense industrial layout rules without replacing the existing Cirrus shell or large-scale view structure.

**Architecture:** Keep the current TabContainer and calculator shell intact, then shrink and harden layout constraints inside the calculator subtree. Apply test-first changes to ControlBar, sidebar, post-calculate splitter, and dense table/tree containers using existing texture-backed widgets rather than new layout systems.

**Tech Stack:** Java, NeoForge client UI widgets, CloudLib/Taffy layout styles, JUnit 5, ModDevMCP for in-game UI validation.

---

### Task 1: Lock Compact Calculator Shell Metrics

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/ControlBarWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/OmniCalculatorView.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/OmniCalculatorViewContractTest.java`

**Step 1: Write the failing test**

Add assertions for compact fixed metrics:
- control bar fixed height
- target slot fixed size
- amount input compact width
- calculate button fixed width

**Step 2: Run test to verify it fails**

Run:
```bash
.\gradlew.bat :Mod:test --tests "*OmniCalculatorViewContractTest" --no-daemon --no-configuration-cache
```

**Step 3: Write minimal implementation**

Tighten ControlBar and root calculator spacing:
- reduce gaps/padding
- expose debug metrics
- keep existing shell structure

**Step 4: Run test to verify it passes**

Run the same focused test command.

### Task 2: Tighten Sidebar and View Tabs

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/OmniCalcSidebar.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/ViewSwitcherWidget.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/OmniCalculatorViewContractTest.java`

**Step 1: Write the failing test**

Add assertions for:
- sidebar expanded/collapsed width staying fixed
- collapsed sidebar hiding nav text from layout
- tree header actions staying compact

**Step 2: Run test to verify it fails**

Run:
```bash
.\gradlew.bat :Mod:test --tests "*OmniCalculatorViewContractTest" --no-daemon --no-configuration-cache
```

**Step 3: Write minimal implementation**

Make sidebar/button metrics denser and keep footer pinned low. Tighten ViewSwitcher padding/action button sizes without changing the overall top strip structure.

**Step 4: Run test to verify it passes**

Run the same focused test command.

### Task 3: Tighten Tree and Matrix Containers

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/TreeViewWidget.java`
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/MatrixViewWidget.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/TreeViewWidgetContractTest.java`
- Test: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/omnicalc/calculator/MatrixMilestonesContractTest.java`

**Step 1: Write the failing test**

Add assertions for:
- tree layer nav width and bottom strip height staying compact
- matrix row height staying in dense fixed-pixel range
- matrix chip width cap and truncation behavior remaining stable

**Step 2: Run test to verify it fails**

Run:
```bash
.\gradlew.bat :Mod:test --tests "*TreeViewWidgetContractTest" --tests "*MatrixMilestonesContractTest" --no-daemon --no-configuration-cache
```

**Step 3: Write minimal implementation**

Reduce paddings/gaps, keep fixed pixel dimensions, and preserve current view behavior.

**Step 4: Run test to verify it passes**

Run the same focused test command.

### Task 4: Verify In Game and Archive Captures

**Files:**
- Modify: `docs/plans/2026-03-23-omnicalc-component-audit.md`

**Step 1: Compile and run focused tests**

Run:
```bash
.\gradlew.bat :Mod:test --tests "*OmniCalculatorViewContractTest" --tests "*TreeViewWidgetContractTest" --tests "*MatrixMilestonesContractTest" --no-daemon --no-configuration-cache
```

**Step 2: Relaunch client and validate**

Use ModDevMCP to:
- restart the client if needed
- join `Codex UITest (1)`
- maximize the window
- open OmniCalc
- capture Tree / Matrix / Milestones

**Step 3: Record evidence**

Append the new capture paths and conclusions to the audit document.
