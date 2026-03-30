# Executor Recipe Tooltip Card Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the target tooltip’s preview with a JEI-style recipe card that supports inputs, outputs, catalysts, and machine (with quantities), and render the machine in the status bar.

**Architecture:** Extend `FlowChartTooltipComponent` with a `RecipeCard` entry rendered by the existing tooltip client. Map recipe data in `ExecutionOverlayWidget` into card inputs/outputs/catalysts/machine, and render machine slot in the status bar summary.

**Tech Stack:** Java (Minecraft UI), `FlowChartTooltipComponent`, Cirrus textures, JUnit 5.

---

### Task 1: Add a failing test for recipe-card layout

**Files:**
- Create: `Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/flowchart/FlowChartTooltipComponentTest.java`

**Step 1: Write the failing test**

```java
package dev.vfyjxf.cirrus.ui.widget.flowchart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FlowChartTooltipComponentTest {

    @Test
    void recipeCardLayoutWrapsInputs() {
        // Expect a layout helper to compute rows for inputs given width.
        var layout = FlowChartTooltipComponent.recipeCardLayout(140, 10, 2);
        assertTrue(layout.inputRows() >= 2, "Inputs should wrap to multiple rows");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponentTest" --no-daemon`  
Expected: FAIL with `recipeCardLayout` not found.

**Step 3: Write minimal implementation**

Add a small static layout helper and a record `RecipeCardLayout` in `FlowChartTooltipComponent`:

```java
public static RecipeCardLayout recipeCardLayout(int maxWidth, int inputCount, int outputCount) {
    int slotStep = 18;
    int minCols = Math.max(1, maxWidth / slotStep);
    int inputRows = (int) Math.ceil(inputCount / (double) minCols);
    int outputRows = (int) Math.ceil(outputCount / (double) minCols);
    return new RecipeCardLayout(minCols, inputRows, outputRows);
}

public record RecipeCardLayout(int columns, int inputRows, int outputRows) {}
```

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponentTest" --no-daemon`  
Expected: PASS.

**Step 5: Commit**

```
git add Mod/src/test/java/dev/vfyjxf/cirrus/ui/widget/flowchart/FlowChartTooltipComponentTest.java Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/flowchart/FlowChartTooltipComponent.java
git commit -m "test: add recipe card layout helper"
```

---

### Task 2: Add `RecipeCard` tooltip entry and renderer

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/flowchart/FlowChartTooltipComponent.java`

**Step 1: Write failing test for render sizing**

Extend the test to validate width/height are non-zero for a recipe card entry:

```java
@Test
void recipeCardEntryReportsSize() {
    var entry = FlowChartTooltipComponent.Entry.RecipeCard.empty();
    var component = FlowChartTooltipComponent.builder().recipeCard(entry).build();
    assertTrue(component.toClient(component).getWidth(null) > 0);
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :Mod:test --tests "dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponentTest" --no-daemon`  
Expected: FAIL (missing `RecipeCard` entry and builder).

**Step 3: Implement `RecipeCard` entry**

Add entry type:

```java
record RecipeCard(
    List<MaterialSlot> inputs,
    List<MaterialSlot> outputs,
    List<MaterialSlot> catalysts,
    @Nullable MaterialSlot machine
) implements Entry {
    static RecipeCard empty() { return new RecipeCard(List.of(), List.of(), List.of(), null); }
}
```

Add builder method:

```java
public Builder recipeCard(Entry.RecipeCard card) { entries.add(card); return this; }
```

Update `Client.getWidth/getHeight` and `renderImage` to layout:
- Inputs in left block, outputs in right block, arrow between.
- Inputs/outputs wrap by column count derived from `recipeCardLayout`.
- Catalysts row below, machine row below that.
- Omit catalysts/machine rows entirely when data is missing.
- Render slot backgrounds with existing SLOT_BG/SLOT_BD and counts.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :Mod:test --tests "dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponentTest" --no-daemon`  
Expected: PASS.

**Step 5: Commit**

```
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/flowchart/FlowChartTooltipComponent.java
git commit -m "feat: add recipe card tooltip entry"
```

---

### Task 3: Map recipe data and replace target tooltip preview

**Files:**
- Modify: `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java`

**Step 1: Write failing test (if feasible)**

If no UI tests exist, add a small unit test to verify data mapping methods return non-empty slots for known demo data. Otherwise, skip automated test and proceed with manual verification.

**Step 2: Implement data mapping**

Add helper methods:
- `workbenchTooltipRecipeInputs(...) -> List<MaterialSlot>`
- `workbenchTooltipRecipeOutputs(...) -> List<MaterialSlot>`
- `workbenchTooltipRecipeCatalysts(...) -> List<MaterialSlot>`
- `workbenchTooltipRecipeMachine(...) -> @Nullable MaterialSlot`

Catalysts:
- If `ExecutionFlowNode` exposes catalyst info, project it into `ExecutionOverlayNode`.
- Otherwise, treat `isCatalyst` slots from child nodes if available.

Machine:
- Prefer `viewModel.footerIcon` or flow-node machine icon.

**Step 3: Use recipe card in target tooltip**

Replace current preview with:

```java
builder.recipeCard(new Entry.RecipeCard(inputs, outputs, catalysts, machine));
```

Keep the minimal metrics (`Need/Now/Left`, `Step`) below.

**Step 4: Update status bar machine render**

Render a small machine slot in the top control panel summary, aligned near the target item slot.

**Step 5: Manual verification**

Run: `.\gradlew.bat :Mod:compileJava --no-daemon`  
In-game: `/cirrus exec_overlay` and verify:
- Inputs/outputs wrap correctly.
- Catalysts show as separate row.
- Machine shows in tooltip and status bar.

**Step 6: Commit**

```
git add Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java
git commit -m "feat: add recipe card tooltip preview"
```

---

Plan complete and saved to `docs/plans/2026-03-29-executor-recipe-tooltip-card-plan.md`. Two execution options:

1. Subagent-Driven (this session) - I dispatch a fresh subagent per task, review between tasks.
2. Parallel Session (separate) - Open new session with executing-plans, batch execution with checkpoints.

Which approach?
