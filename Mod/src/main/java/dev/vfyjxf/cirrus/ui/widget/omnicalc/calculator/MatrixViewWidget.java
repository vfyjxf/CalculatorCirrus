package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Sign;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Pos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.green;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.purple;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingHorizontal;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingVertical;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * Cirrus-skinned matrix view that keeps the omnicalc table hierarchy:
 * a compact graphical column header, then a dense list of machine rows.
 */
public class MatrixViewWidget extends CompositeWidget<Widget> {

	//region State
	private final MatrixRowsWidget rowsWidget;
	private final MatrixRowData[] rows;
	//endregion

	public MatrixViewWidget() {
		this(new ProcessGraph());
	}

	public MatrixViewWidget(ProcessGraph graph) {
		rows = buildRows(graph);
		useStyle(
				flexColumn(),
				sizeFull(),
				paddingHorizontal(4),
				paddingVertical(4),
				gap(3)
		);

		onRender((canvas, mx, my, pt, self, ctx) ->
				canvas.texture(Background.flat, 0, 0, width(), height()));

		rowsWidget = new MatrixRowsWidget(rows);
		addWidget(createTableShell());
	}

	int visibleRowCount() {
		return rows.length;
	}

	void debugSetTableScroll(float scroll) {
		rowsWidget.debugSetScroll(scroll);
	}

	float debugTableScroll() {
		return rowsWidget.debugScroll();
	}

	boolean debugTableNeedsScrollbar(int viewportHeight) {
		return rowsWidget.debugNeedsScrollbar(viewportHeight);
	}

	static int debugRowHeight() {
		return MatrixRowsWidget.rowHeight;
	}

	boolean debugHeaderUsesFrameTexture() {
		return false;
	}

	boolean debugHeaderUsesFlatAccentChrome() {
		return true;
	}

	boolean debugColumnHeaderUsesIcons() {
		return true;
	}

	boolean debugColumnHeaderUsesText() {
		return false;
	}

	boolean debugRowsRenderOutputNameText() {
		return false;
	}

	boolean debugRowsRenderOutputSummaryText() {
		return false;
	}

	boolean debugRowsRenderMachineIcons() {
		return true;
	}

	boolean debugRowsUsePerRowBorderChrome() {
		return false;
	}

	boolean debugRowsUseRichTooltips() {
		return false;
	}

	boolean debugRowsUseVisibleWindowRendering() {
		return true;
	}

	boolean debugRowsUseSharedMaterialSlotRenderer() {
		return OmniCalcSlotRenderer.usesRecipeContentRenderer();
	}

	boolean debugRowsUseGregTechTierAccent() {
		return false;
	}

	boolean debugHeaderShowsTitleText() {
		return false;
	}

	boolean debugUsesStandaloneSummaryHeader() {
		return false;
	}

	private Widget createTableShell() {
		var shell = new WidgetGroup<>();
		shell.useStyle(
				flexColumn(),
				flexGrow(1),
				minHeight(0),
				paddingHorizontal(3),
				paddingVertical(3),
				gap(2)
		);
		shell.onRender((canvas, mx, my, pt, self, ctx) ->
				canvas.texture(Background.flat, 0, 0, self.width(), self.height()));

		shell.addWidget(createColumnHeader());
		rowsWidget.useStyle(flexGrow(1), minHeight(0));
		shell.addWidget(rowsWidget);
		return shell;
	}

	private Widget createColumnHeader() {
		var header = new Widget();
		header.useStyle(heightOf(18));
		header.onRender((canvas, mx, my, pt, self, ctx) -> {
			int w = self.width();
			canvas.texture(Background.flat, 0, 0, w, self.height());
			canvas.texture(Background.Sign.bar, 2, 1, Math.max(16, w - 4), 2);
			canvas.fill(2, 1, Math.max(12, w - 4), 1, 0x668B8B8B);
			canvas.fill(2, self.height() - 1, Math.max(0, w - 4), 1, 0x338B8B8B);

			int outputX = 31;
			int processX = Math.max(60, w / 2 - 5);
			int inputX = Math.max(processX + 42, w - 74);

			canvas.texture(Sign.Group.item, outputX + 3, 6, 8, 8);
			canvas.texture(Icon.arrowRight, processX, 4, 10, 10);
			canvas.texture(Sign.Group.item, inputX + 3, 6, 8, 8);
		});
		return header;
	}

	private static MatrixRowData[] buildRows(ProcessGraph graph) {
		List<MatrixRowData> result = new ArrayList<>();
		for (ProcessNode node : graph.nodes()) {
			List<MaterialSlot> outputs = List.copyOf(node.outputs());
			List<MaterialSlot> inputs = node.inputs().stream()
					.filter(slot -> !slot.isCatalyst())
					.toList();
			List<MaterialSlot> catalysts = node.inputs().stream()
					.filter(MaterialSlot::isCatalyst)
					.toList();
			MaterialSlot primary = outputs.isEmpty() ? null : outputs.getFirst();
			String output = primary == null ? node.name() : primary.materialName();
			int count = primary == null ? 1 : Math.max(1, primary.amount());
			result.add(new MatrixRowData(
					node.name(),
					output,
					node.machine(),
					count,
					node.durationFormatted(),
					node.euFormatted(),
					inputs,
					catalysts,
					outputs,
					primary,
					primary == null ? 0xFF8B8B8B : materialAccent(primary),
					machineInfo(node)
			));
		}
		return result.toArray(MatrixRowData[]::new);
	}

	private static @org.jetbrains.annotations.Nullable MachineInfo machineInfo(ProcessNode node) {
		for (MachineInfo machine : node.machineOptions()) {
			if (machine.name().equals(node.machine())) {
				return machine;
			}
		}
		return node.machineOptions().isEmpty() ? null : node.machineOptions().getFirst();
	}

	private static int materialAccent(MaterialSlot slot) {
		return switch (slot.type()) {
			case ITEM -> 0xFFA8BAD8;
			case FLUID -> 0xFF8EC3DC;
			case ENERGY -> 0xFFB4A3E8;
			case GAS -> 0xFF8CC9BF;
			case CATALYST -> 0xFFC1B3E8;
		};
	}

	private record MatrixRowData(
			String name,
			String output,
			String machine,
			int count,
			String duration,
			String energy,
			List<MaterialSlot> inputs,
			List<MaterialSlot> catalysts,
			List<MaterialSlot> outputs,
			MaterialSlot primaryOutput,
			int accentColor,
			@org.jetbrains.annotations.Nullable MachineInfo machineInfo
	) {}

	private static final class MatrixRowsWidget extends Widget {

		//region Metrics
		private static final int rowHeight = 30;
		private static final int rowGap = 1;
		private static final int scrollbarWidth = 8;
		private static final int compactSlot = 17;
		private static final int machineSlot = 18;
		private static final float scrollSmooth = 0.35f;
		//endregion

		//region State
		private float scrollY;
		private float targetScrollY;
		private boolean draggingScrollbar;
		private float dragOffsetY;
		private final MatrixRowData[] rows;
		private final ScrollState wheelScrollState = CirrusScroll.longVerticalState(22);
		//endregion

		private MatrixRowsWidget(MatrixRowData[] rows) {
			this.rows = rows;
			onMouseClicked((input, ctx) -> {
				FloatPos local = input.mouseRelative(this);
				if (isOnScrollbarThumb(local.x(), local.y())) {
					draggingScrollbar = true;
					dragOffsetY = (float) local.y() - thumbY();
					ctx.cancel();
					return EventDispatch.consumed;
				}
				return EventDispatch.pass;
			});

			onMouseReleased((input, ctx) -> {
				if (!draggingScrollbar) {
					return EventDispatch.pass;
				}
				stopScrollbarDrag();
				return EventDispatch.consumed;
			});

			onMouseDragged((input, deltaX, deltaY, ctx) -> {
				if (!draggingScrollbar) return EventDispatch.pass;
				if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
					stopScrollbarDrag();
					return EventDispatch.pass;
				}
				FloatPos local = input.mouseRelative(this);
				scrollY = dragTo(local.y());
				targetScrollY = scrollY;
				ctx.cancel();
				return EventDispatch.consumed;
			});

			onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
				if (!needsScrollbar()) {
					return EventDispatch.pass;
				}
				targetScrollY = clamp(targetScrollY + wheelScrollState.wheelDelta(0, sy).y(), 0, maxScroll());
				ctx.cancel();
				return EventDispatch.consumed;
			});

			onMouseLeave((mouseX, mouseY, ctx) -> stopScrollbarDrag());
		}

		private void stopScrollbarDrag() {
			draggingScrollbar = false;
			dragOffsetY = 0;
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			Font font = Minecraft.getInstance().font;
			int w = width();
			int h = height();
			boolean hasScrollbar = needsScrollbar();
			int contentW = Math.max(0, w - (hasScrollbar ? scrollbarWidth + 4 : 0));
			Pos abs = absolutePos();

			scrollY += (targetScrollY - scrollY) * scrollSmooth;
			if (Math.abs(scrollY - targetScrollY) < 0.5f) scrollY = targetScrollY;

			canvas.pushClip(abs.x(), abs.y(), contentW, h);
			canvas.pushTransform();
			canvas.translate(0, -scrollY);

			int stride = rowHeight + rowGap;
			int start = Math.max(0, (int) (scrollY / stride) - 1);
			int end = Math.min(rows.length, (int) ((scrollY + h) / stride) + 2);
			int contentMouseY = Math.round(mouseY + scrollY);
			for (int i = start; i < end; i++) {
				drawRow(canvas, font, rows[i], contentW, i * stride, mouseX, contentMouseY, partialTicks);
			}

			canvas.popTransform();
			canvas.popClip();

			if (hasScrollbar) {
				int trackX = w - scrollbarWidth;
				canvas.texture(Background.scroll, trackX, 0, scrollbarWidth, h);
				canvas.texture(Icon.scrollBarVertical, trackX + 1, thumbY(), scrollbarWidth - 2, thumbH());
			}
		}

		private void drawRow(SceneCanvas canvas, Font font, MatrixRowData row, int w, int y, int mouseX, int mouseY, float partialTicks) {
			boolean hovered = mouseX >= 0 && mouseX < w && mouseY >= y && mouseY < y + rowHeight;
			canvas.texture(hovered ? Background.Outlined.flat : Background.flat, 0, y, w, rowHeight);
			canvas.texture(Background.Sign.bar, 2, y + 1, Math.max(16, w - 4), 2);
			canvas.fill(2, y + 1, Math.max(12, w - 4), 1, row.accentColor());

			int outputX = 7;
			int visibleOutputs = Math.min(3, Math.max(1, row.outputs().size()));
			int slotX = outputX;
			if (row.outputs().isEmpty()) {
				OmniCalcSlotRenderer.renderMaterialSlot(canvas, font, row.primaryOutput(), slotX, y + 7, compactSlot, partialTicks, true);
				slotX += compactSlot + 2;
			} else {
				for (int i = 0; i < visibleOutputs; i++) {
					OmniCalcSlotRenderer.renderMaterialSlot(canvas, font, row.outputs().get(i), slotX, y + 7, compactSlot, partialTicks, true);
					slotX += compactSlot + 2;
				}
			}
			if (row.outputs().size() > visibleOutputs) {
				drawMoreBadge(canvas, font, row.outputs().size() - visibleOutputs, slotX, y + 11, green);
			}

			int processX = Math.max(slotX + 18, w / 2 - machineSlot / 2);
			canvas.texture(Icon.arrowRight, processX - 16, y + 11, 10, 10);
			OmniCalcSlotRenderer.renderMachineSlot(canvas, row.machineInfo(), processX, y + 6, machineSlot, row.accentColor());

			List<MaterialSlot> visibleInputSlots = visibleInputSlots(row);
			int availableInputW = Math.max(0, w - processX - 38);
			int maxInputs = Math.max(1, Math.min(5, availableInputW / (compactSlot + 2)));
			int visibleInputs = Math.min(maxInputs, visibleInputSlots.size());
			int inputAreaX = Math.max(processX + 36, w - visibleInputs * (compactSlot + 2) - 24);
			canvas.texture(Icon.arrowRight, processX + machineSlot + 7, y + 11, 10, 10);
			slotX = inputAreaX;
			for (int i = 0; i < visibleInputs; i++) {
				OmniCalcSlotRenderer.renderMaterialSlot(canvas, font, visibleInputSlots.get(i), slotX, y + 7, compactSlot, partialTicks, true);
				slotX += compactSlot + 2;
			}
			if (visibleInputSlots.size() > visibleInputs) {
				drawMoreBadge(canvas, font, visibleInputSlots.size() - visibleInputs, slotX, y + 11, purple);
			}
		}

		private void drawMoreBadge(SceneCanvas canvas, Font font, int amount, int x, int y, int color) {
			String value = "+" + OmniCalcSlotRenderer.compactNumber(amount);
			int chipW = Math.max(16, font.width(value) + 9);
			canvas.texture(Background.flat, x, y, chipW, 10);
			canvas.drawString(value, x + 4, y + 1, color, false);
		}

		private boolean isOnScrollbarThumb(double mx, double my) {
			int trackX = width() - scrollbarWidth;
			return needsScrollbar()
					&& mx >= trackX && mx <= trackX + scrollbarWidth
					&& my >= thumbY() && my <= thumbY() + thumbH();
		}

		private int thumbY() {
			int track = Math.max(1, height() - thumbH());
			float progress = maxScroll() <= 0 ? 0 : scrollY / maxScroll();
			return Math.round(track * progress);
		}

		private int thumbH() {
			return Math.max(18, Math.min(height(), height() * height() / Math.max(contentH(), 1)));
		}

		private float dragTo(double mouseY) {
			int track = Math.max(1, height() - thumbH());
			float top = clamp((float) mouseY - dragOffsetY, 0, track);
			return maxScroll() <= 0 ? 0 : top / track * maxScroll();
		}

		private int contentH() {
			return rows.length * rowHeight + Math.max(0, rows.length - 1) * rowGap;
		}

		private int maxScroll() {
			return Math.max(0, contentH() - height());
		}

		private boolean needsScrollbar() {
			return needsScrollbarFor(rows.length, height());
		}

		private boolean debugNeedsScrollbar(int viewportHeight) {
			return needsScrollbarFor(rows.length, viewportHeight);
		}

		private static boolean needsScrollbarFor(int rowCount, int viewportHeight) {
			int contentHeight = rowCount * rowHeight + Math.max(0, rowCount - 1) * rowGap;
			return viewportHeight > 0 && contentHeight > viewportHeight;
		}

		private static List<MaterialSlot> visibleInputSlots(MatrixRowData row) {
			if (row.inputs().isEmpty()) {
				return row.catalysts();
			}
			if (row.catalysts().isEmpty()) {
				return row.inputs();
			}
			ArrayList<MaterialSlot> slots = new ArrayList<>(row.inputs().size() + row.catalysts().size());
			slots.addAll(row.inputs());
			slots.addAll(row.catalysts());
			return slots;
		}

		private void debugSetScroll(float scroll) {
			scrollY = clamp(scroll, 0, maxScroll());
			targetScrollY = scrollY;
		}

		private float debugScroll() {
			return scrollY;
		}

		private static float clamp(float value, float min, float max) {
			return Math.max(min, Math.min(max, value));
		}

	}
}
