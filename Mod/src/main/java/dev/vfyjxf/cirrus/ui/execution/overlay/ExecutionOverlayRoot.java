package dev.vfyjxf.cirrus.ui.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CoordinateSpace;
import dev.vfyjxf.cloudlib.api.ui.base.SceneLayer;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.inset;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class ExecutionOverlayRoot extends CompositeWidget<Widget> {
	//region geometry

	private static final int slotFrameInset = 1;
	private static final int slotFrameSize = 18;
	private static final int moveLineThickness = 1;
	private static final int moveAnchorInset = 9;
	private static final int traceInventoryFillAlpha = 0x18;
	private static final int traceInputFillAlpha = 0x12;
	private static final int traceOutputFillAlpha = 0x20;
	//endregion

	//region state

	private final TraceLayerWidget traceLayer;
	private final MainPanel panel;
	private final Runnable openStateListener = this::applyOpenState;
	private Rect2i panelBounds;
	//endregion

	//region setup

	public ExecutionOverlayRoot(ExecutionDriverHandle driverHandle, Rect2i panelBounds, int sceneWidth, int sceneHeight) {
		this.panelBounds = panelBounds;
		applyRootBounds();

		traceLayer = addWidget(new TraceLayerWidget());
		traceLayer.setSceneLayer(SceneLayer.overlay);
		traceLayer.setCoordinateSpace(CoordinateSpace.scene);
		traceLayer.useStyle(UIStyle.of(positionAbsolute(), inset(0), sizeOf(Math.max(0, sceneWidth), Math.max(0, sceneHeight))));
		traceLayer.setInteractive(false);
		traceLayer.setActive(false);

		panel = addWidget(new MainPanel(driverHandle));
		panel.useStyle(UIStyle.of(positionAbsolute(), inset(0), sizeOf(-1, -1)));

		ExecutionOverlayOpenState.addListener(openStateListener);
		onUnmount(() -> ExecutionOverlayOpenState.removeListener(openStateListener));
		applyOpenState();
	}
	//endregion

	//region access

	public MainPanel panel() {
		return panel;
	}

	public Rect2i panelBounds() {
		return panelBounds;
	}

	public Rect2i currentBounds() {
		if (!ExecutionOverlayOpenState.open()) {
			return new Rect2i(panelBounds.getX(), panelBounds.getY(), 0, 0);
		}
		return panelBounds;
	}

	public List<Rect2i> exclusionBounds() {
		return ExecutionOverlayOpenState.open() ? List.of(panelBounds) : List.of();
	}
	//endregion

	//region layout

	private void applyRootBounds() {
		Rect2i bounds = currentBounds();
		useStyle(UIStyle.of(
				positionAbsolute(),
				insetLeft(bounds.getX()),
				insetTop(bounds.getY()),
				sizeOf(bounds.getWidth(), bounds.getHeight())
		));
	}

	private void applyOpenState() {
		boolean open = ExecutionOverlayOpenState.open();
		applyRootBounds();
		panel.setVisible(open);
		panel.setActive(open);
		panel.setTickable(open);
		traceLayer.setVisible(open);
		traceLayer.setActive(false);
	}
	//endregion

	//region lifecycle

	@Override
	public void tick() {
		if (!ExecutionOverlayOpenState.open()) {
			return;
		}
		super.tick();
	}
	//endregion

	//region trace layer

	private final class TraceLayerWidget extends Widget {

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			super.renderInternal(canvas, mouseX, mouseY, partialTicks);
			if (!ExecutionOverlayOpenState.open() || !panel.traceEnabled()) {
				return;
			}
			ExecutionOverlayTraceSnapshot trace = panel.trace();
			if (trace == null || !trace.available()) {
				return;
			}
			var screen = context().currentScreen();
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			List<SlotRect> slotRects = resolveSlotRects(containerScreen, trace);
			if (slotRects.isEmpty()) {
				return;
			}

			for (ExecutionOverlayTraceSnapshot.SlotMove move : trace.moves()) {
				Rect2i from = findSlotRect(slotRects, move.from());
				Rect2i to = findSlotRect(slotRects, move.to());
				if (from == null || to == null) {
					continue;
				}
				int fromX = from.getX() + moveAnchorInset;
				int fromY = from.getY() + moveAnchorInset;
				int toX = to.getX() + moveAnchorInset;
				int toY = to.getY() + moveAnchorInset;
				canvas.line(fromX, fromY, toX, toY, moveLineThickness, move.color());
			}

			for (SlotRect slotRect : slotRects) {
				drawSlotRect(canvas, slotRect);
			}
		}

		private void drawSlotRect(SceneCanvas canvas, SlotRect slotRect) {
			Rect2i rect = slotRect.rect();
			canvas.fill(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), slotRect.fillColor());
			if (slotRect.role().equals("input-preview")) {
				canvas.strokeRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), withAlpha(slotRect.color(), 0x72), 1);
				if (slotRect.currentStack().isEmpty()) {
					ItemStack ghost = slotRect.stack();
					if (!ghost.isEmpty()) {
						canvas.color(0x72FFFFFF);
						canvas.renderItem(ghost, rect.getX() + 1, rect.getY() + 1);
						canvas.resetColor();
					}
				}
				return;
			}
			if (slotRect.role().equals("input-executing")) {
				canvas.strokeRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), withAlpha(slotRect.color(), 0x88), 1);
				canvas.fill(rect.getX() + 2, rect.getY() + 2, rect.getWidth() - 4, rect.getHeight() - 4, withAlpha(slotRect.color(), 0x10));
				return;
			}
			if (slotRect.role().equals("output")) {
				canvas.strokeRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), withAlpha(slotRect.color(), 0x88), 1);
				canvas.fill(rect.getX() + 2, rect.getY() + 2, rect.getWidth() - 4, rect.getHeight() - 4, withAlpha(slotRect.color(), 0x16));
				if (slotRect.currentStack().isEmpty()) {
					ItemStack ghost = slotRect.stack();
					if (!ghost.isEmpty()) {
						canvas.color(0x7AFFFFFF);
						canvas.renderItem(ghost, rect.getX() + 1, rect.getY() + 1);
						canvas.resetColor();
					}
				}
				return;
			}
			canvas.strokeRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), withAlpha(slotRect.color(), 0x88), 1);
			if (slotRect.role().equals("inventory") && slotRect.currentStack().isEmpty()) {
				ItemStack icon = slotRect.stack();
				if (!icon.isEmpty()) {
					canvas.color(0x70FFFFFF);
					canvas.renderItem(icon, rect.getX() + 1, rect.getY() + 1);
					canvas.resetColor();
				}
			}
		}

		private List<SlotRect> resolveSlotRects(AbstractContainerScreen<?> screen, ExecutionOverlayTraceSnapshot trace) {
			ArrayList<SlotRect> out = new ArrayList<>();
			int left = screen.getGuiLeft();
			int top = screen.getGuiTop();
			for (ExecutionOverlayTraceSnapshot.SlotHighlight highlight : trace.highlights()) {
				Integer slotIndex = slotIndex(highlight.slot());
				if (slotIndex == null || slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
					continue;
				}
				Slot slot = screen.getMenu().slots.get(slotIndex);
				Rect2i rect = new Rect2i(left + slot.x - slotFrameInset, top + slot.y - slotFrameInset, slotFrameSize, slotFrameSize);
				out.add(new SlotRect(
						highlight.slot(),
						highlight.role(),
						rect,
						highlight.color(),
						roleFillColor(highlight.role(), highlight.color()),
						normalizedStack(highlight.stack()),
						normalizedStack(slot.getItem())
				));
			}
			return out;
		}

		private int roleFillColor(String role, int color) {
			return switch (role) {
				case "input-preview" -> withAlpha(color, 0x08);
				case "input-executing" -> withAlpha(color, traceInputFillAlpha);
				case "output" -> withAlpha(color, traceOutputFillAlpha);
				case "inventory" -> withAlpha(color, traceInventoryFillAlpha);
				default -> withAlpha(color, 0x18);
			};
		}

		private @Nullable Rect2i findSlotRect(List<SlotRect> slots, SlotLayout.Reference reference) {
			for (SlotRect slot : slots) {
				if (slot.reference().equals(reference)) {
					return slot.rect();
				}
			}
			return null;
		}

		private @Nullable Integer slotIndex(SlotLayout.Reference reference) {
			if (reference instanceof SlotLayout.Reference.Index index) {
				return index.index();
			}
			if (reference instanceof SlotLayout.Reference.Key) {
				return null;
			}
			return null;
		}

		private int withAlpha(int color, int alpha) {
			return (alpha << 24) | (color & 0x00FFFFFF);
		}

		private ItemStack normalizedStack(ItemStack stack) {
			if (stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack copy = stack.copy();
			copy.setCount(1);
			return copy;
		}
	}
	//endregion

	private record SlotRect(
			SlotLayout.Reference reference,
			String role,
			Rect2i rect,
			int color,
			int fillColor,
			ItemStack stack,
			ItemStack currentStack
	) {
	}
}
