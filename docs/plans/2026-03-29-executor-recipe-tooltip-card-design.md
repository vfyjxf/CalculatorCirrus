# Executor Recipe Tooltip Card Design

**Goal:** Replace the target tooltip preview with a recipe card built from workbench data, and render the machine slot in the status bar summary.

**Scope:** `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/execution/ExecutionOverlayWidget.java` only. No API changes.

## Data Mapping

- Inputs: `mergedWorkbenchInputs(recipeFocus)`; when `recipeFocus == null`, use `mergedWorkbenchInputs(focus)`.
- Outputs: `workbenchRecipeDisplayStack(recipeFocus)`; if `recipeFocus == null` or stack empty, outputs list is empty.
- Catalysts: empty list (no inference).
- Machine: if `viewModel.footerIcon` is present and not empty, map to `MaterialSlot.item(footerIcon, 1)`, otherwise `null`.

## Tooltip

- Replace the preview input/output slots in `buildWorkbenchTargetTooltip()` with:
  `builder.recipeCard(new Entry.RecipeCard(inputs, outputs, catalysts, machine))`.
- Keep existing progress bar and metrics below the recipe card (Need/Now/Left, Step).

## Status Bar Summary

- Render machine slot to the left of the target slot, same size, 2px gap.
- If no machine icon, keep the current single-slot layout.
- Adjust text X offsets to align with the new slot stack when machine is present.

## Testing

- No new unit tests. Optionally run `./gradlew.bat :Mod:compileJava --no-daemon` if available.

## Risks

- Empty recipe card lists rely on existing tooltip renderer to display gracefully.
