package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.ProcessGroup;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.recipe.storage.source.PlayerInventorySource;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Pos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToLongFunction;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.borderMain;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.borderSecondary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.green;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textSecondary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textWhite;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingBottom;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingHorizontal;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingVertical;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class MilestonesViewWidget extends CompositeWidget<Widget> {

	//region Data & Metrics
	private static final int summaryProgressInset = 0;
	private static final int phaseProgressInset = 4;
	private static final int stepProgressInset = 0;
	private static final int summaryProgressFill = textWhite;
	private static final int progressFill = green;
	//endregion

	private final MilestoneBoardWidget board;
	private final PhaseColumnData[] phases;
	private final Set<String> manualCompletedStepKeys;
	private final ToLongFunction<StorageTransferRequest> stockCounter;

	public MilestonesViewWidget() {
		this(new ProcessGraph());
	}

	public MilestonesViewWidget(ProcessGraph graph) {
		this(graph, new HashSet<>(), MilestonesViewWidget::countStorageAvailable);
	}

	MilestonesViewWidget(
			ProcessGraph graph,
			Set<String> manualCompletedStepKeys,
			ToLongFunction<StorageTransferRequest> stockCounter
	) {
		this.manualCompletedStepKeys = manualCompletedStepKeys;
		this.stockCounter = stockCounter;
		this.phases = buildPhases(graph);
		useStyle(
				flexColumn(),
				sizeFull(),
				paddingHorizontal(4),
				paddingVertical(4),
				gap(4)
		);

		onRender((canvas, mx, my, pt, self, ctx) ->
				canvas.texture(Background.flat, 0, 0, width(), height()));

		addWidget(createSummaryHeader());
		board = new MilestoneBoardWidget();
		addWidget(board);
	}

	int phaseColumnCount() {
		return phases.length;
	}

	void debugSetPhaseScroll(int phaseIndex, float scroll) {
		board.debugSetPhaseScroll(phaseIndex, scroll);
	}

	float debugPhaseScroll(int phaseIndex) {
		return board.debugPhaseScroll(phaseIndex);
	}

	boolean debugStepCardsShowStageText() {
		return false;
	}

	boolean debugStepCardsShowAmountBadge() {
		return false;
	}

	boolean debugStepCardsShowActionText() {
		return true;
	}

	String debugStepCardActionKind(int phaseIndex, int stepIndex) {
		StepCardData step = debugStepCard(phaseIndex, stepIndex);
		return step == null ? "" : step.kind().name().toLowerCase();
	}

	String debugStepCardMaterialName(int phaseIndex, int stepIndex) {
		StepCardData step = debugStepCard(phaseIndex, stepIndex);
		return step == null ? "" : step.materialName();
	}

	int debugStepCardQuantity(int phaseIndex, int stepIndex) {
		StepCardData step = debugStepCard(phaseIndex, stepIndex);
		return step == null ? 0 : step.quantity();
	}

	boolean debugPhaseHeadersUseExternalProgressBar() {
		return false;
	}

	int debugSummaryProgressInset() {
		return summaryProgressInset;
	}

	int debugPhaseProgressInset() {
		return phaseProgressInset;
	}

	int debugStepCardProgressInset() {
		return stepProgressInset;
	}

	boolean debugSummaryProgressTouchesHorizontalEdges() {
		return true;
	}

	boolean debugSummaryProgressTouchesBottomEdge() {
		return true;
	}

	boolean debugSummaryProgressMergedTopBorder() {
		return true;
	}

	boolean debugSummaryHeaderShowsSubtitle() {
		return false;
	}

	boolean debugPhaseHeadersShowSubtitle() {
		return false;
	}

	boolean debugProgressUsesUnifiedFillColor() {
		return true;
	}

	boolean debugManualToggleUsesSwitchTexture() {
		return false;
	}

	boolean debugManualToggleUsesCheckboxTexture() {
		return true;
	}

	boolean debugManualToggleIsProgressMarker() {
		return true;
	}

	boolean debugManualToggleUsesStatusMarker() {
		return false;
	}

	boolean debugManualTogglePlaysClickSound() {
		return true;
	}

	boolean debugManualToggleIsRightAligned() {
		return true;
	}

	boolean debugSummaryHeaderUsesMinimalChrome() {
		return true;
	}

	String debugSummaryHeaderTitle() {
		return CirrusLangs.Calc.Milestones.title.key();
	}

	String debugSummaryHeaderProgressText() {
		return summaryProgressText();
	}

	boolean debugUsesPlaceholderHeaderTexture() {
		return false;
	}

	boolean debugSupportsManualStepToggle() {
		return true;
	}

	boolean debugStepCardsUseSharedMaterialSlotRenderer() {
		return OmniCalcSlotRenderer.usesRecipeContentRenderer();
	}

	boolean debugStepCardsUseFallbackBarrierIcon() {
		return OmniCalcSlotRenderer.usesFallbackBarrierIcon();
	}

	boolean debugPhaseColumnsAutoHideScrollbar() {
		return true;
	}

	int debugPhaseScrollbarWidth() {
		return MilestoneBoardWidget.verticalScrollbarWidth;
	}

	boolean debugPhaseNeedsScrollbar(int phaseIndex, int viewportHeight) {
		return board.debugPhaseNeedsScrollbar(phaseIndex, viewportHeight);
	}

	boolean debugBoardNeedsHorizontalScrollbar(int viewportWidth) {
		return board.debugNeedsHorizontalScrollbar(viewportWidth);
	}

	static boolean shouldRouteToHorizontalScroll(boolean shiftDown, float scrollX, boolean overHorizontalBar, boolean overPhaseBody) {
		if (shiftDown || Math.abs(scrollX) > 0.01f) return true;
		if (overPhaseBody) return false;
		return overHorizontalBar;
	}

	void debugToggleManualStep(int phaseIndex, int stepIndex) {
		board.debugToggleManualStep(phaseIndex, stepIndex);
	}

	boolean debugManualStepChecked(int phaseIndex, int stepIndex) {
		return board.debugManualStepChecked(phaseIndex, stepIndex);
	}

	boolean debugStepCompleted(int phaseIndex, int stepIndex) {
		return board.debugStepCompleted(phaseIndex, stepIndex);
	}

	long debugStepAvailableStock(int phaseIndex, int stepIndex) {
		return board.debugStepAvailableStock(phaseIndex, stepIndex);
	}

	float debugStepProgressRatio(int phaseIndex, int stepIndex) {
		return board.debugStepProgressRatio(phaseIndex, stepIndex);
	}

	private Widget createSummaryHeader() {
		var header = new WidgetGroup<>();
		header.useStyle(
				flexColumn(),
				paddingTop(4),
				paddingBottom(0),
				gap(0)
		);
		var titlePanel = new WidgetGroup<>();
		titlePanel.useStyle(
				flexColumn(),
				paddingTop(4),
				paddingBottom(5)
		);
		titlePanel.onRender((canvas, mx, my, pt, self, ctx) -> {
			canvas.texture(Background.Outlined.flat, 0, 0, self.width(), self.height());
			canvas.texture(Background.flat, 2, 2, self.width() - 4, self.height() - 4);
		});

		var titleRow = new WidgetGroup<>();
		titleRow.useStyle(flexRow(), alignItemsCenter(), gap(4), paddingHorizontal(6), heightOf(20));

		var icon = new Widget();
		icon.useStyle(sizeOf(12, 12));
		icon.onRender((canvas, mx, my, pt, self, ctx) -> {
			canvas.texture(Icon.Calc.Task.tabSelected, 0, 0, 12, 12);
		});
		titleRow.addWidget(icon);

		var title = LabelWidget.of(CirrusLangs.Calc.Milestones.title)
							   .setColor(textPrimary)
							   .setShadow(false);
		titleRow.addWidget(title);

		titleRow.addWidget(SpacerWidget.create());

		var progress = new Widget();
		progress.useStyle(sizeOf(42, 12));
		progress.onRender((canvas, mx, my, pt, self, ctx) -> {
			String text = summaryProgressText();
			int textW = Minecraft.getInstance().font.width(text);
			canvas.drawString(text, Math.max(0, self.width() - textW), 2, textSecondary, false);
		});
		titleRow.addWidget(progress);

		titlePanel.addWidget(titleRow);
		header.addWidget(titlePanel);

		var summaryBarStrip = new Widget();
		summaryBarStrip.useStyle(heightOf(7));
		summaryBarStrip.onRender((canvas, mx, my, pt, self, ctx) -> {
			int w = self.width();
			int border = borderMain;
			int trackColor = borderSecondary;
			// Merge with title panel bottom border: no extra top border on this strip.
			canvas.fill(0, 0, 1, 7, border);
			canvas.fill(w - 1, 0, 1, 7, border);
			canvas.fill(0, 6, w, 1, border);
			canvas.fill(1, 0, w - 2, 6, trackColor);

			int trackW = Math.max(0, w - 2 - summaryProgressInset * 2);
			float ratio = summaryProgressRatio();
			int fillW = ratio <= 0f ? 0 : Math.max(2, Math.round((trackW - 2) * ratio));
			if (fillW > 0) {
				canvas.fill(2 + summaryProgressInset, 1, fillW, 3, summaryProgressFill);
			}
		});
		header.addWidget(summaryBarStrip);

		var summaryDivider = new Widget();
		summaryDivider.useStyle(heightOf(1));
		summaryDivider.onRender((canvas, mx, my, pt, self, ctx) ->
				canvas.fill(0, 0, self.width(), 1, borderSecondary));
		header.addWidget(summaryDivider);

		return header;
	}

	private static PhaseColumnData[] buildPhases(ProcessGraph graph) {
		List<PhaseColumnData> result = new ArrayList<>();
		if (!graph.groups().isEmpty()) {
			for (ProcessGroup group : graph.groups()) {
				if (group.parentGroupId() != null) {
					continue;
				}
				List<StepCardData> steps = new ArrayList<>();
				appendGroupSteps(graph, group, steps);
				if (!steps.isEmpty()) {
					result.add(phase(group.name(), steps));
				}
			}
		}
		if (result.isEmpty() && graph.nodeCount() > 0) {
			List<StepCardData> steps = new ArrayList<>();
			for (ProcessNode node : graph.nodes()) {
				steps.add(step(node));
			}
			result.add(phase(CirrusLangs.Calc.Milestones.defaultPhase.string(), steps));
		}
		return result.toArray(PhaseColumnData[]::new);
	}

	private static void appendGroupSteps(ProcessGraph graph, ProcessGroup group, List<StepCardData> steps) {
		for (String nodeId : group.childNodeIds()) {
			ProcessNode node = graph.node(nodeId);
			if (node != null) {
				steps.add(step(node));
			}
		}
		for (String childGroupId : group.childGroupIds()) {
			ProcessGroup child = graph.group(childGroupId);
			if (child != null) {
				appendGroupSteps(graph, child, steps);
			}
		}
	}

	private static PhaseColumnData phase(String label, List<StepCardData> steps) {
		int completed = 0;
		for (StepCardData step : steps) {
			if (step.complete()) completed++;
		}
		float progress = steps.isEmpty() ? 0f : (float) completed / steps.size();
		return new PhaseColumnData(label, completed + " / " + steps.size(), progress, steps.toArray(StepCardData[]::new));
	}

	private static StepCardData step(ProcessNode node) {
		StepKind kind = stepKind(node);
		MaterialSlot subject = subjectSlot(node);
		String materialName = subject == null ? nodeDisplayName(node) : subject.materialName();
		int quantity = subject == null ? 0 : subject.amount();
		Component action = switch (kind) {
			case COLLECT -> CirrusLangs.Calc.Milestones.collect.component(formatQuantity(quantity), materialName);
			case MAKE -> CirrusLangs.Calc.Milestones.make.component(formatQuantity(quantity), materialName);
			case STEP -> CirrusLangs.Calc.Milestones.step.component(nodeDisplayName(node));
		};
		Component detail = switch (kind) {
			case COLLECT -> CirrusLangs.Calc.Milestones.source.component();
			case MAKE, STEP -> Component.literal(node.machine().isBlank() ? nodeDisplayName(node) : node.machine());
		};
		return new StepCardData(
				stepKey(node, kind, subject),
				kind,
				materialName,
				quantity,
				action,
				detail,
				subject,
				machineIcon(node),
				storageRequest(subject),
				false,
				false
		);
	}

	private static StepKind stepKind(ProcessNode node) {
		if (node.inputs().isEmpty() && !node.outputs().isEmpty()) {
			return StepKind.COLLECT;
		}
		if (!node.outputs().isEmpty()) {
			return StepKind.MAKE;
		}
		if (!node.inputs().isEmpty()) {
			return StepKind.COLLECT;
		}
		return StepKind.STEP;
	}

	private static @Nullable MaterialSlot subjectSlot(ProcessNode node) {
		if (!node.outputs().isEmpty()) {
			return node.outputs().getFirst();
		}
		if (!node.inputs().isEmpty()) {
			return node.inputs().getFirst();
		}
		return null;
	}

	private static String nodeDisplayName(ProcessNode node) {
		if (!node.name().isBlank()) {
			return node.name();
		}
		if (!node.machine().isBlank()) {
			return node.machine();
		}
		return "";
	}

	private static String formatQuantity(int amount) {
		return Integer.toString(Math.max(0, amount));
	}

	private static String stepKey(ProcessNode node, StepKind kind, @Nullable MaterialSlot subject) {
		return node.id()
				+ "|"
				+ kind.name().toLowerCase(Locale.ROOT)
				+ "|"
				+ (subject == null ? "" : subject.materialName());
	}

	private static @Nullable StorageTransferRequest storageRequest(@Nullable MaterialSlot slot) {
		if (slot == null || slot.amount() <= 0) {
			return null;
		}
		RecipeContent content = slot.content();
		if (content instanceof InputContent input && !input.isEmpty()) {
			return StorageTransferRequest.forInput(input, slot.amount());
		}
		if (content instanceof OutputContent output && !output.isEmpty()) {
			ContentResource resource = output.asResource();
			if (resource.amount() > 0) {
				return StorageTransferRequest.forResource(resource, slot.amount());
			}
		}
		if (slot.itemStack() != null && !slot.itemStack().isEmpty()) {
			return StorageTransferRequest.forResource(ContentStack.fromStack(slot.itemStack(), slot.amount()), slot.amount());
		}
		if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
			return StorageTransferRequest.forResource(ContentStack.fromStack(slot.fluidStack(), slot.amount()), slot.amount());
		}
		ItemStack inferredItem = inferItemStack(slot.materialName(), slot.amount());
		if (!inferredItem.isEmpty()) {
			return StorageTransferRequest.forResource(ContentStack.fromStack(inferredItem, slot.amount()), slot.amount());
		}
		return null;
	}

	private static ItemStack inferItemStack(String materialName, int amount) {
		if (materialName == null || materialName.isBlank()) {
			return ItemStack.EMPTY;
		}
		int count = Math.max(1, amount);
		String query = materialName.trim().toLowerCase(Locale.ROOT);
		if (query.indexOf(':') > 0) {
			ResourceLocation id = ResourceLocation.tryParse(query);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				Item item = BuiltInRegistries.ITEM.get(id);
				if (item != null && !BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.ITEM.getDefaultKey())) {
					return new ItemStack(item, count);
				}
			}
		}
		String normalizedQuery = normalizeMaterialName(materialName);
		if (normalizedQuery.isBlank()) {
			return ItemStack.EMPTY;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null || id.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
				continue;
			}
			if (normalizeMaterialName(id.getPath()).equals(normalizedQuery)) {
				return new ItemStack(item, count);
			}
			ItemStack stack = new ItemStack(item, count);
			if (normalizeMaterialName(stack.getHoverName().getString()).equals(normalizedQuery)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static String normalizeMaterialName(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lower.length());
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				out.append(c);
			}
		}
		return out.toString();
	}

	static long countStorageAvailable(StorageTransferRequest request) {
		try {
			StorageApi api = StorageApi.instance();
			ensureCurrentPlayerStorage(api);
			return api.countAvailable(request);
		} catch (IllegalStateException ignored) {
			return 0L;
		}
	}

	private static void ensureCurrentPlayerStorage(StorageApi api) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.player == null) {
				return;
			}
			if (api.findStorage(minecraft.player) == null) {
				api.addStorage(new PlayerInventorySource(minecraft.player.getInventory()));
			}
		} catch (RuntimeException ignored) {
			// Headless tests and early client bootstrap can have no usable Minecraft player yet.
		}
	}

	private static ItemStack machineIcon(ProcessNode node) {
		if (!node.machineOptions().isEmpty()) {
			ItemStack machine = node.machineOptions().getFirst().icon();
			if (machine != null && !machine.isEmpty()) {
				return machine.copy();
			}
		}
		return ItemStack.EMPTY;
	}

	private String summaryProgressText() {
		return summaryCompletedSteps() + " / " + summaryTotalSteps();
	}

	private int summaryCompletedSteps() {
		if (board != null) {
			return board.completedSteps();
		}
		int completed = 0;
		for (PhaseColumnData phase : phases) {
			completed += parsePhaseCountPart(phase.count, 0);
		}
		return completed;
	}

	private int summaryTotalSteps() {
		int total = 0;
		for (PhaseColumnData phase : phases) {
			total += parsePhaseCountPart(phase.count, 1);
		}
		return total;
	}

	private float summaryProgressRatio() {
		int total = summaryTotalSteps();
		if (total <= 0) return 0f;
		return (float) summaryCompletedSteps() / total;
	}

	private static int parsePhaseCountPart(String count, int index) {
		String[] parts = count.split("/");
		if (index < 0 || index >= parts.length) return 0;
		return Integer.parseInt(parts[index].trim());
	}

	private record PhaseColumnData(
			String label,
			String count,
			float progress,
			StepCardData[] steps
	) {}

	private enum StepKind {
		COLLECT,
		MAKE,
		STEP
	}

	private record StepCardData(
			String key,
			StepKind kind,
			String materialName,
			int quantity,
			Component action,
			Component detail,
			@Nullable MaterialSlot subject,
			ItemStack machineIcon,
			@Nullable StorageTransferRequest storageRequest,
			boolean complete,
			boolean tracked
	) {}

	private final class MilestoneBoardWidget extends Widget {

		private static final int boardPadding = 4;
		private static final int columnWidth = 186;
		private static final int columnGap = 8;
		private static final int columnHeaderHeight = 28;
		private static final int cardHeight = 56;
		private static final int cardGap = 6;
		private static final int horizontalScrollbarHeight = 10;
		private static final int verticalScrollbarWidth = 10;
		private static final int cardAreaY = columnHeaderHeight + 6;
		private static final int maxPhaseBodyHeight = cardHeight * 4 + cardGap * 3 + 8;
		private static final int phaseScrollBottomPadding = 12;
		private static final int columnInnerX = 4;
		private static final float scrollSmooth = 0.35f;
		private static final int statusMarkerRightInset = 8;
		private static final int statusMarkerY = 8;
		private static final int manualToggleWidth = 14;
		private static final int manualToggleHeight = 14;
		private static final int statusIconSize = 14;

		private float scrollX;
		private float targetScrollX;
		private final float[] phaseScrollY = new float[phases.length];
		private final float[] targetPhaseScrollY = new float[phases.length];
		private DragMode dragMode = DragMode.NONE;
		private int dragPhase = -1;
		private float dragOffset;
		private final ScrollState horizontalWheelState = CirrusScroll.longVerticalState(26);
		private final ScrollState verticalWheelState = CirrusScroll.longVerticalState(26);

		private enum DragMode {NONE, HORIZONTAL, PHASE_VERTICAL}

		private MilestoneBoardWidget() {
			useStyle(flexGrow(1), minHeight(0));

			onMouseClicked((input, ctx) -> {
				FloatPos local = input.mouseRelative(this);
				double mx = local.x();
				double my = local.y();
				if (!input.isLeftClick()) {
					stopDrag();
					return EventDispatch.pass;
				}
				ManualToggleHit toggleHit = manualToggleAt(mx, my);
				if (toggleHit != null) {
					toggleManualStep(toggleHit.phaseIndex(), toggleHit.stepIndex());
					CirrusUiSound.playClickSound();
					ctx.cancel();
					return EventDispatch.consumed;
				}
				if (isOnHorizontalThumb(mx, my)) {
					dragMode = DragMode.HORIZONTAL;
					dragOffset = (float) (mx - horizontalThumbX());
					return EventDispatch.consumed;
				}
				int phase = phaseAt(mx, my);
				if (phase >= 0 && isOnPhaseVerticalThumb(phase, mx, my)) {
					dragMode = DragMode.PHASE_VERTICAL;
					dragPhase = phase;
					dragOffset = (float) (my - phaseThumbY(phase, boardViewH()));
					return EventDispatch.consumed;
				}
				return EventDispatch.pass;
			});

			onMouseReleased((input, ctx) -> {
				if (dragMode == DragMode.NONE) {
					return EventDispatch.pass;
				}
				stopDrag();
				return EventDispatch.consumed;
			});

			onMouseDragged((input, deltaX, deltaY, ctx) -> {
				if (dragMode == DragMode.NONE) return EventDispatch.pass;
				if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
					stopDrag();
					return EventDispatch.pass;
				}
				FloatPos local = input.mouseRelative(this);
				if (dragMode == DragMode.HORIZONTAL) {
					scrollX = dragHorizontal(local.x());
					targetScrollX = scrollX;
				} else if (dragPhase >= 0) {
					phaseScrollY[dragPhase] = dragPhaseVertical(dragPhase, local.y());
					targetPhaseScrollY[dragPhase] = phaseScrollY[dragPhase];
				}
				ctx.cancel();
				return EventDispatch.consumed;
			});

			onMouseLeave((mouseX, mouseY, ctx) -> stopDrag());

			onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
				FloatPos local = sceneToLocal(mouseX, mouseY);
				int phase = phaseAt(local.x(), local.y());
				boolean overPhaseBody = phase >= 0 && local.y() >= boardPadding + cardAreaY && local.y() <= boardPadding + cardAreaY + phaseBodyH(boardViewH());
				int hBarY = boardPadding + boardViewH() + 1;
				boolean overHorizontalBar = horizontalScrollbarVisible()
						&& local.y() >= hBarY && local.y() <= hBarY + horizontalScrollbarHeight;
				boolean horizontalIntent = shouldRouteToHorizontalScroll(
						Screen.hasShiftDown(),
						(float) sx,
						overHorizontalBar,
						overPhaseBody
				);
				if (horizontalIntent) {
					if (!horizontalScrollbarVisible()) {
						return EventDispatch.pass;
					}
					float delta = Math.abs(sx) > 0.01f
							? -horizontalWheelState.wheelDelta(sx, 0).x()
							: verticalWheelState.wheelDelta(0, sy).y();
					targetScrollX = clamp(targetScrollX + delta, 0, maxScrollX());
				} else {
					if (phase >= 0 && phaseVerticalScrollbarVisible(phase, boardViewH())) {
						targetPhaseScrollY[phase] = clamp(targetPhaseScrollY[phase] + verticalWheelState.wheelDelta(0, sy).y(),
								0, maxPhaseScroll(phase, boardViewH()));
					} else if (phase >= 0) {
						return EventDispatch.pass;
					} else if (horizontalScrollbarVisible()) {
						targetScrollX = clamp(targetScrollX + verticalWheelState.wheelDelta(0, sy).y(), 0, maxScrollX());
					} else {
						return EventDispatch.pass;
					}
				}
				ctx.cancel();
				return EventDispatch.consumed;
			});
		}

		private void stopDrag() {
			dragMode = DragMode.NONE;
			dragPhase = -1;
			dragOffset = 0;
		}

		@Override
		public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
			StepHit hit = stepAt(mouseX, mouseY);
			if (hit == null) {
				return super.hoverTooltip(mouseX, mouseY);
			}
			StepCardData step = phases[hit.phaseIndex()].steps[hit.stepIndex()];
			Tooltip tooltip = Tooltip.create();
			tooltip.add(step.action());
			if (!step.detail().getString().isBlank()) {
				tooltip.add(step.detail());
			}
			return tooltip;
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			Font font = Minecraft.getInstance().font;
			int w = width();
			int h = height();
			int viewW = boardViewW();
			int viewH = boardViewH();

			scrollX += (targetScrollX - scrollX) * scrollSmooth;
			if (Math.abs(scrollX - targetScrollX) < 0.5f) scrollX = targetScrollX;
			for (int i = 0; i < phaseScrollY.length; i++) {
				phaseScrollY[i] += (targetPhaseScrollY[i] - phaseScrollY[i]) * scrollSmooth;
				if (Math.abs(phaseScrollY[i] - targetPhaseScrollY[i]) < 0.5f) {
					phaseScrollY[i] = targetPhaseScrollY[i];
				}
			}

			drawPanel(canvas, 0, 0, w, h);

			Pos abs = absolutePos();
			canvas.pushClip(abs.x() + boardPadding, abs.y() + boardPadding, viewW, viewH);
			canvas.pushTransform();
			canvas.translate(boardPadding - scrollX, boardPadding);

			int stride = columnWidth + columnGap;
			int firstColumn = Math.max(0, (int) Math.floor(scrollX / stride) - 1);
			int lastColumn = Math.min(phases.length - 1, (int) Math.floor((scrollX + viewW) / stride) + 1);
			for (int i = firstColumn; i <= lastColumn; i++) {
				renderColumn(canvas, font, phases[i], i, i * stride, viewH, abs, partialTicks);
			}

			canvas.popTransform();
			canvas.popClip();

			if (horizontalScrollbarVisible()) {
				renderHorizontalScrollbar(canvas, viewW, viewH);
			}
		}

		private void renderColumn(
				SceneCanvas canvas, Font font, PhaseColumnData phase, int phaseIndex,
				int x, int viewH, Pos boardAbs, float partialTicks) {
			drawPanel(canvas, x, 0, columnWidth, viewH);
			canvas.drawString(phase.label, x + 10, 9, textPrimary, false);

			String count = phaseCountText(phaseIndex);
			int countW = font.width(count);
			canvas.drawString(count, x + columnWidth - countW - 10, 9, textSecondary, false);

			int progressTrackX = x + phaseProgressInset;
			int progressTrackY = columnHeaderHeight - 4;
			int progressTrackW = columnWidth - phaseProgressInset * 2;
			float progress = phaseProgress(phaseIndex);
			int progressFillW = progress <= 0f ? 0 : Math.max(2, (int) ((progressTrackW - 2) * progress));
			canvas.texture(Background.inset, progressTrackX, progressTrackY, progressTrackW, 5);
			if (progressFillW > 0) {
				canvas.fill(progressTrackX + 1, progressTrackY + 1, progressFillW, 3, progressFill);
			}

			int bodyH = phaseBodyH(viewH);
			boolean hasScrollbar = phaseVerticalScrollbarVisible(phaseIndex, viewH);
			int innerW = phaseInnerWidth(phaseIndex, viewH);
			int clipX = boardAbs.x() + boardPadding + x - Math.round(scrollX) + columnInnerX;
			int clipY = boardAbs.y() + boardPadding + cardAreaY;

			canvas.pushClip(clipX, clipY, innerW, bodyH);
			canvas.pushTransform();
			canvas.translate(0, cardAreaY - phaseScrollY[phaseIndex]);

			int cardStride = cardHeight + cardGap;
			int firstStep = Math.max(0, (int) Math.floor(phaseScrollY[phaseIndex] / cardStride) - 1);
			int lastStep = Math.min(phase.steps.length - 1, (int) Math.floor((phaseScrollY[phaseIndex] + bodyH) / cardStride) + 1);
			for (int stepIndex = firstStep; stepIndex <= lastStep; stepIndex++) {
				StepCardData step = phase.steps[stepIndex];
				renderCard(canvas, font, step, phaseIndex, stepIndex, x + columnInnerX, stepIndex * cardStride, innerW, partialTicks);
			}

			canvas.popTransform();
			canvas.popClip();

			if (hasScrollbar) {
				int trackX = x + columnWidth - verticalScrollbarWidth - 4;
				canvas.texture(Background.scroll, trackX, cardAreaY, verticalScrollbarWidth, bodyH);
				canvas.texture(Icon.scrollBarVertical, trackX + 1, phaseThumbY(phaseIndex, viewH) - boardPadding,
						verticalScrollbarWidth - 2, phaseThumbHeight(phaseIndex, viewH));
			}
		}

		private void renderCard(SceneCanvas canvas, Font font, StepCardData step, int phaseIndex, int stepIndex, int x, int y, int w, float partialTicks) {
			canvas.texture(Background.flat, x, y, w, cardHeight);
			if (step.tracked) {
				canvas.texture(Background.Sign.bar, x + 2, y + 2, Math.max(16, w - 4), 2);
			}
			long available = availableStock(step);

			if (step.subject() != null) {
				OmniCalcSlotRenderer.renderMaterialSlot(canvas, font, step.subject(), x + 8, y + 8, 22, partialTicks, true);
			} else {
				OmniCalcSlotRenderer.renderMachineSlot(canvas,
						step.machineIcon().isEmpty() ? null : new ProcessNode.MachineInfo(step.detail().getString(), step.machineIcon()),
						x + 8, y + 8, 22, textSecondary);
			}

			boolean completed = step.complete || stepCompleteByManual(step) || stepCompleteByStorage(step, available);
			int markerX = x + statusMarkerX(w);
			int markerY = y + statusMarkerY;
			CirrusControlChrome.renderButtonBackground(canvas, markerX, markerY,
					manualToggleWidth, manualToggleHeight, false, false, true);
			SizedTexture checkboxIcon = completed
					? CirrusTextures.Toggle.Checked.select
					: CirrusTextures.Toggle.Checked.unselect;
			int iconX = markerX + CirrusControlChrome.centeredIconX(manualToggleWidth, checkboxIcon.width());
			int iconY = markerY + CirrusControlChrome.centeredCheckboxIconY(manualToggleHeight, checkboxIcon.height());
			canvas.texture(checkboxIcon, iconX, iconY, checkboxIcon.width(), checkboxIcon.height());

			int textX = x + 38;
			int textW = Math.max(0, markerX - textX - 5);
			canvas.drawString(fit(font, step.action.getString(), textW), textX, y + 8, textPrimary, false);
			String detail = step.detail.getString();
			if (!detail.isBlank()) {
				canvas.drawString(fit(font, detail, textW), textX, y + 22, textSecondary, false);
			}

			int barX = x + stepProgressInset;
			int barY = y + cardHeight - 6;
			int barW = w - stepProgressInset * 2;
			float progress = stepProgressRatio(step, available);
			int barFillW = progress <= 0f ? 0 : Math.max(6, Math.round((barW - 2) * progress));
			canvas.texture(Background.inset, barX, barY, barW, 6);
			if (barFillW > 0) {
				canvas.fill(barX + 1, barY + 1, barFillW, 4, progressFill);
			}
		}

		private float stepProgressRatio(StepCardData step) {
			return stepProgressRatio(step, availableStock(step));
		}

		private float stepProgressRatio(StepCardData step, long available) {
			if (stepCompleteByManual(step) || step.complete) return 1f;
			if (step.storageRequest() != null) {
				return Math.min(1f, available / (float) step.storageRequest().amount());
			}
			if (step.tracked) return 0.6f;
			return 0f;
		}

		private boolean stepCompleted(StepCardData step) {
			return step.complete || stepCompleteByManual(step) || stepCompleteByStorage(step);
		}

		private boolean stepCompleteByManual(StepCardData step) {
			return manualCompletedStepKeys.contains(step.key());
		}

		private boolean stepCompleteByStorage(StepCardData step) {
			return stepCompleteByStorage(step, availableStock(step));
		}

		private boolean stepCompleteByStorage(StepCardData step, long available) {
			return step.storageRequest() != null && available >= step.storageRequest().amount();
		}

		private long availableStock(StepCardData step) {
			StorageTransferRequest request = step.storageRequest();
			if (request == null) {
				return 0L;
			}
			return Math.max(0L, Math.min(request.amount(), stockCounter.applyAsLong(request)));
		}

		private void renderHorizontalScrollbar(SceneCanvas canvas, int viewW, int viewH) {
			int hBarY = boardPadding + viewH + 1;
			int hThumbW = horizontalThumbWidth(viewW);
			int hThumbX = horizontalThumbX();
			canvas.texture(Background.scroll, boardPadding, hBarY, viewW, horizontalScrollbarHeight);
			canvas.texture(Icon.scrollBarVertical, hThumbX + 1, hBarY + 1, hThumbW - 2, 8);
		}

		private boolean isOnHorizontalThumb(double mx, double my) {
			if (!horizontalScrollbarVisible()) {
				return false;
			}
			int viewW = boardViewW();
			int y = boardPadding + boardViewH() + 1;
			return mx >= horizontalThumbX() && mx <= horizontalThumbX() + horizontalThumbWidth(viewW)
					&& my >= y && my <= y + horizontalScrollbarHeight;
		}

		private int phaseAt(double mx, double my) {
			int viewH = boardViewH();
			if (my < boardPadding || my > boardPadding + viewH) return -1;
			for (int i = 0; i < phases.length; i++) {
				int x = phaseScreenX(i);
				if (mx >= x && mx <= x + columnWidth) {
					return i;
				}
			}
			return -1;
		}

		private boolean isOnPhaseVerticalThumb(int phase, double mx, double my) {
			int viewH = boardViewH();
			if (!phaseVerticalScrollbarVisible(phase, viewH)) {
				return false;
			}
			int x = phaseScreenX(phase) + columnWidth - verticalScrollbarWidth - 4;
			return mx >= x && mx <= x + verticalScrollbarWidth
					&& my >= phaseThumbY(phase, viewH) && my <= phaseThumbY(phase, viewH) + phaseThumbHeight(phase, viewH);
		}

		private int phaseScreenX(int phase) {
			return boardPadding + phase * (columnWidth + columnGap) - Math.round(scrollX);
		}

		private int horizontalThumbX() {
			int viewW = boardViewW();
			int track = Math.max(1, viewW - horizontalThumbWidth(viewW));
			float progress = maxScrollX() <= 0 ? 0 : scrollX / maxScrollX();
			return boardPadding + Math.round(track * progress);
		}

		private int horizontalThumbWidth(int viewW) {
			return Math.max(18, Math.min(viewW, viewW * viewW / Math.max(contentWidth(), 1)));
		}

		private int phaseThumbY(int phase, int viewH) {
			int bodyH = phaseBodyH(viewH);
			int track = Math.max(1, bodyH - phaseThumbHeight(phase, viewH));
			float progress = maxPhaseScroll(phase, viewH) <= 0 ? 0 : phaseScrollY[phase] / maxPhaseScroll(phase, viewH);
			return boardPadding + cardAreaY + Math.round(track * progress);
		}

		private int phaseThumbHeight(int phase, int viewH) {
			int bodyH = phaseBodyH(viewH);
			return Math.max(18, Math.min(bodyH, bodyH * bodyH / Math.max(phaseContentHeight(phases[phase]), 1)));
		}

		private float dragHorizontal(double mouseX) {
			int viewW = boardViewW();
			int thumbW = horizontalThumbWidth(viewW);
			int track = Math.max(1, viewW - thumbW);
			float left = clamp((float) mouseX - dragOffset - boardPadding, 0, track);
			return maxScrollX() <= 0 ? 0 : left / track * maxScrollX();
		}

		private float dragPhaseVertical(int phase, double mouseY) {
			int viewH = boardViewH();
			int thumbH = phaseThumbHeight(phase, viewH);
			int track = Math.max(1, phaseBodyH(viewH) - thumbH);
			float top = clamp((float) mouseY - dragOffset - boardPadding - cardAreaY, 0, track);
			return maxPhaseScroll(phase, viewH) <= 0 ? 0 : top / track * maxPhaseScroll(phase, viewH);
		}

		private int phaseBodyH(int viewH) {
			int dynamicBodyH = Math.max(32, viewH - cardAreaY - 2);
			return Math.min(dynamicBodyH, maxPhaseBodyHeight);
		}

		private int phaseContentHeight(PhaseColumnData phase) {
			return phase.steps.length * cardHeight + Math.max(0, phase.steps.length - 1) * cardGap;
		}

		private int phaseInnerWidth(int phase, int viewH) {
			return columnWidth - 12 - (phaseVerticalScrollbarVisible(phase, viewH) ? verticalScrollbarWidth : 0);
		}

		private int contentWidth() {
			return phases.length * columnWidth + Math.max(0, phases.length - 1) * columnGap;
		}

		private int boardViewW() {
			return Math.max(0, width() - boardPadding * 2);
		}

		private int boardViewH() {
			int horizontalInset = horizontalScrollbarVisible() ? horizontalScrollbarHeight + 2 : 0;
			return Math.max(0, height() - boardPadding * 2 - horizontalInset);
		}

		private int maxScrollX() {
			return Math.max(0, contentWidth() - boardViewW());
		}

		private int maxPhaseScroll(int phase, int viewH) {
			return Math.max(0, phaseContentHeight(phases[phase]) + phaseScrollBottomPadding - phaseBodyH(viewH));
		}

		private boolean horizontalScrollbarVisible() {
			return maxScrollX() > 0;
		}

		private boolean phaseVerticalScrollbarVisible(int phase, int viewH) {
			return phase >= 0 && phase < phases.length && maxPhaseScroll(phase, viewH) > 0;
		}

		private void toggleManualStep(int phaseIndex, int stepIndex) {
			if (!isValidStep(phaseIndex, stepIndex)) return;
			StepCardData step = phases[phaseIndex].steps[stepIndex];
			if (manualCompletedStepKeys.contains(step.key())) {
				manualCompletedStepKeys.remove(step.key());
			} else {
				manualCompletedStepKeys.add(step.key());
			}
		}

		private int completedSteps() {
			int completed = 0;
			for (int phaseIndex = 0; phaseIndex < phases.length; phaseIndex++) {
				completed += completedSteps(phaseIndex);
			}
			return completed;
		}

		private int completedSteps(int phaseIndex) {
			if (phaseIndex < 0 || phaseIndex >= phases.length) return 0;
			int completed = 0;
			for (int stepIndex = 0; stepIndex < phases[phaseIndex].steps.length; stepIndex++) {
				if (stepCompleted(phases[phaseIndex].steps[stepIndex])) {
					completed++;
				}
			}
			return completed;
		}

		private String phaseCountText(int phaseIndex) {
			if (phaseIndex < 0 || phaseIndex >= phases.length) return "0 / 0";
			return completedSteps(phaseIndex) + " / " + phases[phaseIndex].steps.length;
		}

		private float phaseProgress(int phaseIndex) {
			if (phaseIndex < 0 || phaseIndex >= phases.length || phases[phaseIndex].steps.length == 0) return 0f;
			return (float) completedSteps(phaseIndex) / phases[phaseIndex].steps.length;
		}

		private boolean isValidStep(int phaseIndex, int stepIndex) {
			return phaseIndex >= 0 && phaseIndex < phases.length
					&& stepIndex >= 0 && stepIndex < phases[phaseIndex].steps.length;
		}

		private @Nullable ManualToggleHit manualToggleAt(double mx, double my) {
			int phaseIndex = phaseAt(mx, my);
			if (phaseIndex < 0) return null;
			int phaseX = phaseScreenX(phaseIndex);
			int viewH = boardViewH();
			int innerW = phaseInnerWidth(phaseIndex, viewH);
			if (mx < phaseX + columnInnerX || mx > phaseX + columnInnerX + innerW) return null;
			if (my < boardPadding + cardAreaY || my > boardPadding + cardAreaY + phaseBodyH(viewH)) return null;
			double contentY = my - boardPadding - cardAreaY + phaseScrollY[phaseIndex];
			for (int stepIndex = 0; stepIndex < phases[phaseIndex].steps.length; stepIndex++) {
				int cardY = stepIndex * (cardHeight + cardGap);
				int toggleX = phaseX + columnInnerX + statusMarkerX(innerW);
				int toggleY = boardPadding + cardAreaY - Math.round(phaseScrollY[phaseIndex]) + cardY + statusMarkerY;
				if (mx >= toggleX && mx <= toggleX + manualToggleWidth
						&& my >= toggleY && my <= toggleY + manualToggleHeight
						&& contentY >= cardY && contentY <= cardY + cardHeight) {
					return new ManualToggleHit(phaseIndex, stepIndex);
				}
			}
			return null;
		}

		private @Nullable StepHit stepAt(double mx, double my) {
			int phaseIndex = phaseAt(mx, my);
			if (phaseIndex < 0) return null;
			int phaseX = phaseScreenX(phaseIndex);
			int viewH = boardViewH();
			int innerW = phaseInnerWidth(phaseIndex, viewH);
			if (mx < phaseX + columnInnerX || mx > phaseX + columnInnerX + innerW) return null;
			if (my < boardPadding + cardAreaY || my > boardPadding + cardAreaY + phaseBodyH(viewH)) return null;
			double contentY = my - boardPadding - cardAreaY + phaseScrollY[phaseIndex];
			int stride = cardHeight + cardGap;
			int stepIndex = (int) (contentY / stride);
			if (stepIndex < 0 || stepIndex >= phases[phaseIndex].steps.length) return null;
			int yInCard = (int) (contentY - (double) stepIndex * stride);
			return yInCard >= 0 && yInCard < cardHeight ? new StepHit(phaseIndex, stepIndex) : null;
		}

		private void debugSetPhaseScroll(int phaseIndex, float scroll) {
			if (phaseIndex < 0 || phaseIndex >= phaseScrollY.length) return;
			phaseScrollY[phaseIndex] = Math.max(0, scroll);
			targetPhaseScrollY[phaseIndex] = phaseScrollY[phaseIndex];
		}

		private float debugPhaseScroll(int phaseIndex) {
			if (phaseIndex < 0 || phaseIndex >= phaseScrollY.length) return 0;
			return phaseScrollY[phaseIndex];
		}

		private void debugToggleManualStep(int phaseIndex, int stepIndex) {
			toggleManualStep(phaseIndex, stepIndex);
		}

		private boolean debugManualStepChecked(int phaseIndex, int stepIndex) {
			return isValidStep(phaseIndex, stepIndex)
					&& manualCompletedStepKeys.contains(phases[phaseIndex].steps[stepIndex].key());
		}

		private boolean debugStepCompleted(int phaseIndex, int stepIndex) {
			return isValidStep(phaseIndex, stepIndex) && stepCompleted(phases[phaseIndex].steps[stepIndex]);
		}

		private long debugStepAvailableStock(int phaseIndex, int stepIndex) {
			return isValidStep(phaseIndex, stepIndex) ? availableStock(phases[phaseIndex].steps[stepIndex]) : 0L;
		}

		private float debugStepProgressRatio(int phaseIndex, int stepIndex) {
			return isValidStep(phaseIndex, stepIndex) ? stepProgressRatio(phases[phaseIndex].steps[stepIndex]) : 0f;
		}

		private boolean debugPhaseNeedsScrollbar(int phaseIndex, int viewportHeight) {
			return phaseIndex >= 0
					&& phaseIndex < phases.length
					&& maxPhaseScroll(phaseIndex, Math.max(0, viewportHeight)) > 0;
		}

		private boolean debugNeedsHorizontalScrollbar(int viewportWidth) {
			return contentWidth() > Math.max(0, viewportWidth);
		}

		private static int statusMarkerX(int cardWidth) {
			return Math.max(30, cardWidth - statusMarkerRightInset - statusIconSize);
		}

		private record ManualToggleHit(int phaseIndex, int stepIndex) {}
		private record StepHit(int phaseIndex, int stepIndex) {}
	}

	private @Nullable StepCardData debugStepCard(int phaseIndex, int stepIndex) {
		if (phaseIndex < 0 || phaseIndex >= phases.length) return null;
		StepCardData[] steps = phases[phaseIndex].steps;
		if (stepIndex < 0 || stepIndex >= steps.length) return null;
		return steps[stepIndex];
	}

	private static void drawPanel(SceneCanvas canvas, int x, int y, int w, int h) {
		canvas.texture(Background.Outlined.flat, x, y, w, h);
		canvas.texture(Background.flat, x + 2, y + 2, w - 4, h - 4);
	}

	private static String fit(Font font, String text, int maxWidth) {
		if (maxWidth <= 0 || text.isBlank()) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int ellipsisWidth = font.width("...");
		if (maxWidth <= ellipsisWidth) {
			return "";
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth)).stripTrailing() + "...";
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
