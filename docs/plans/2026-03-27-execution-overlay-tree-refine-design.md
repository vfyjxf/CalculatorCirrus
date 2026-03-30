---
title: Execution Overlay Tree Refinement
date: 2026-03-27
status: approved
---

# Execution Overlay Tree Refinement (Cirrus)

## Goal
Close the remaining layout gap between the Cirrus execution overlay and the OmniCalc Pro web executor by tightening the tree presentation, improving minimap legibility, and refining card content without increasing visual noise.

## Scope
- Applies to the in-game execution overlay and sandbox screen.
- Focuses on tree layout, minimap, and two card types (node cards and execution info card).
- Keeps the overlay anchored to the JEI bookmark region.
- Avoids style changes outside the execution overlay.

## Constraints
- Default to pixel-art UI conventions and existing textures.
- Window must be maximized (not fullscreen) during UI validation.
- Use ModDevMCP operations for UI capture and verification.

## Layout Strategy (Plan B: Focus + Weak Global)
1. **Primary subgraph**
   - Render the current subgraph rooted at the nearest split node on the execution path.
   - Keep the focused branch visually dominant.
2. **Weak global context**
   - Represent non-focus siblings as collapsed groups (compact marker) instead of full cards.
   - Preserve a faint skeleton line so users understand direction without full detail.
3. **Transitions**
   - Auto-follow execution changes the subgraph root.
   - Manual exploration freezes the subgraph root until user unlocks.
4. **Spacing**
   - Tighten row/column gaps to reduce unused canvas space.
   - Maintain clear separation between rows to preserve legibility.

## Minimap
- Show only the current subgraph plus weak global skeleton.
- Enforce minimum node pixel sizes so the map remains readable.
- Emphasize the focused branch with thicker lines and higher contrast.
- Viewport indicator must be clearly framed and stable.

## Card Designs
### Node Card (Tree)
Must include:
- Item icon + item name.
- Minimal progress bar (single-track, single-color).
- Status indicator (icon-based, minimal text).
Error state:
- Status icon and subtle frame accent.
- One short error label (icon + short word).

### Execution Info Card (Top-right)
Must include:
- Current execution status (icon + short text).
- Current recipe and machine (icon-based rows).
- Current step (icon-based row).
- Context: previous/next steps (icon-based rows).
- Error information (visible only on error).
- Speed control slider (continuous).

## Speed Control (Continuous)
- Continuous slider with range 0–10 cycles/s.
- Default value: 5 cycles/s.
- Show a compact numeric readout (e.g., `5/s`).
- Value `0` maps to paused.

## Testing & Validation
- Use ModDevMCP `ui.capture` to record each iteration.
- Validate both sandbox and workbench overlay placements.
- Keep a screenshot per iteration under `docs/plans/screenhots/execution-overlay`.

## Non-Goals
- Full visual reskin of unrelated UI panels.
- Major feature additions outside the execution overlay.
