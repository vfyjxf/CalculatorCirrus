package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.bgPanel;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.borderMain;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.green;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textSecondary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignSelfStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginBottom;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.maxHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.maxWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingHorizontal;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingVertical;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Tree diagram view with overlay controls
 * matching the omnicalc-pro TreeDiagram overlays.
 */
public class TreeViewWidget extends CompositeWidget<Widget> {

	//region Metrics
	private static final int layerNavWidth = 36;  // Layer navigator width
	private static final int bottomPanelHeight = 24;
	private static final int breadcrumbScrollViewWidthPadding = 4;
	private static final int breadcrumbScrollbarBottomY = 0;
	private static final int breadcrumbScrollbarTrackHeight = 4;
	private static final int breadcrumbScrollbarThumbHeight = 6;
	private static final float crumbScale = 1.0f; // keep pixel text crisp
	private static final int crumbIconSize = 12;
	private static final int layerHeaderHeight = 20;
	private static final int layerHeaderIconSize = 16;
	private static final int layerScrollIndicatorSize = 10;
	private static final int layerScrollIndicatorInset = 2;
	//endregion

	//region State
	private @Nullable ProcessGraph graph;
	private final TreeCanvasWidget stepsWidget;
	private final WidgetGroup<Widget> layerNavContainer;
	private final WidgetGroup<Widget> layerButtonsContainer;
	private final ScrollState layerScrollState;
	private final WidgetGroup<Widget> bottomPanel;
	private int activeLayer = 0;
	private int maxLayers = 0;

	// Separate scroll states for path and trace rows
	private float pathScrollX = 0, pathScrollTargetX = 0;
	private float traceScrollX = 0, traceScrollTargetX = 0;
	private final ScrollState breadcrumbWheelState = CirrusScroll.longVerticalState(15);
	private int pathContentW = 0, traceContentW = 0; // total breadcrumb content width
	private final List<BreadcrumbHit> pathHitAreas = new ArrayList<>();
	private final List<BreadcrumbHit> traceHitAreas = new ArrayList<>();
	private @Nullable String hoveredCrumbId = null;
	private @Nullable String selectedCrumbId = null;
	private @Nullable ScrollRegion activeScrollDrag;
	private float dragThumbOffsetX = 0;
	private boolean pendingCanvasFocus = false;
	//endregion

	//region Types
	private enum ScrollRegion {PATH, TRACE}

	private record ScrollbarThumb(int x, int y, int w, int h, int barX, int barW, int maxScroll) {}

	private record BreadcrumbHit(int x, int y, int w, int h, String pathId) {}
	//endregion

	//region Init
	public TreeViewWidget() {
		useStyle(
				flexColumn(),
				sizeFull(),
				background(Background.inset)
		);

		// ── Main area: Layer Nav + Canvas ──
		var mainArea = new WidgetGroup<>();
		mainArea.useStyle(flexRow(), flexGrow(1), minHeight(0), alignItemsStretch());
		mainArea.onRender((canvas, mx, my, pt, self, ctx) -> {
			syncLayerTrackingFromViewport();
			applyPendingCanvasFocus();
		});

		// Layer navigator (left side)
		layerNavContainer = new WidgetGroup<>();
		layerNavContainer.useStyle(
				flexColumn(),
				widthOf(layerNavWidth),
				minWidth(layerNavWidth),
				maxWidth(layerNavWidth),
				minHeight(0),
				alignSelfStretch(),
				flexShrink(0),
				paddingHorizontal(0),
				paddingVertical(0)
		);
		layerNavContainer.onRender((canvas, mx, my, pt, self, ctx) -> {
			canvas.texture(Background.inset, 0, 0, self.width(), self.height());
			canvas.fill(self.width() - 1, 0, 1, self.height(), borderMain);
		});
		layerNavContainer.onRenderPost((canvas, mx, my, pt, self, ctx) -> renderLayerScrollIndicators(canvas, self));
		layerNavContainer.addWidget(new LayerHeaderWidget());

		layerButtonsContainer = new WidgetGroup<>();
		layerButtonsContainer.useStyle(
				flexColumn(),
				flexGrow(1),
				minHeight(0)
		);
		layerScrollState = CirrusScroll.longVerticalState(12)
									  .smooth(true)
									  .smoothSpeed(0.35f)
									  .trackTexture(Background.scroll)
									  .thumbTexture(Icon.scrollBarVertical)
									  .scrollbarWidth(6)
									  .showScrollbar(false)
									  .viewportInset(0);
		layerButtonsContainer.useEffect(UIEffects.scrollable(layerScrollState));
		layerNavContainer.addWidget(layerButtonsContainer);
		mainArea.addWidget(layerNavContainer);

		// Canvas (TreeCanvasWidget — dark theme, no MC textures)
		stepsWidget = new TreeCanvasWidget();
		stepsWidget.useStyle(flexGrow(1), minWidth(0));
		stepsWidget.setOnSubtreeChanged(() -> {
			activeLayer = 0;
			rebuildLayerNav(graph);
		});
		mainArea.addWidget(stepsWidget);

		addWidget(mainArea);

		// ── Bottom info panel ──
		bottomPanel = new WidgetGroup<>();
		bottomPanel.useStyle(heightOf(bottomPanelHeight), minHeight(bottomPanelHeight), maxHeight(bottomPanelHeight), flexShrink(0));

		bottomPanel.onRender((canvas, mx, my, pt, self, ctx) -> {
			int w = self.width(), h = self.height();
			Font font = Minecraft.getInstance().font;
			var pathCrumbs = stepsWidget.getPathContextBreadcrumbs();
			var traceCrumbs = stepsWidget.getTraceBreadcrumbs();
			int halfW = w / 2;
			pathContentW = computeBreadcrumbContentWidth(font, pathCrumbs);
			traceContentW = computeBreadcrumbContentWidth(font, traceCrumbs);
			int scrollViewW = Math.max(0, halfW - breadcrumbScrollViewWidthPadding);
			sanitizeBreadcrumbScroll(scrollViewW);

			// Smooth scroll animation
			pathScrollX += (pathScrollTargetX - pathScrollX) * 0.35f;
			traceScrollX += (traceScrollTargetX - traceScrollX) * 0.35f;
			if (Math.abs(pathScrollX - pathScrollTargetX) < 0.5f) pathScrollX = pathScrollTargetX;
			if (Math.abs(traceScrollX - traceScrollTargetX) < 0.5f) traceScrollX = traceScrollTargetX;
			sanitizeBreadcrumbScroll(scrollViewW);

			canvas.texture(Background.flat, 0, 0, w, h);
			int leftJoinW = Math.max(0, Math.min(w, layerNavWidth + 1));
			// Merge the bottom edge of the layer panel with path bar: avoid double bright seam.
			canvas.fill(0, 0, leftJoinW, 1, bgPanel);
			if (leftJoinW > 0 && leftJoinW <= w) {
				// Soften the corner transition by one extra pixel row.
				canvas.fill(leftJoinW - 1, 1, 1, 1, bgPanel);
			}

			canvas.fill(halfW, 3, 1, h - 6, borderMain);
			int scrollBarY = h - breadcrumbScrollbarThumbHeight - breadcrumbScrollbarBottomY;

			// ── Left half: PATH ──
			FloatPos absOrigin = self.localToScene(0, 0);
			int absX = (int) absOrigin.x(), absY = (int) absOrigin.y();
			drawBreadcrumbRow(canvas, font, mx, my, pathCrumbs,
					pathHitAreas, pathScrollX, 0, halfW, h, absX, absY,
					textPrimary, textPrimary, false, selectedCrumbId);

			// Path scrollbar
			if (breadcrumbScrollbarVisible(pathContentW, scrollViewW)) {
				drawMiniScrollbar(canvas, 0, scrollBarY, halfW - 2, pathScrollX, pathContentW, scrollViewW);
			}

			// ── Right half: TRACE ──
			drawBreadcrumbRow(canvas, font, mx, my, traceCrumbs,
					traceHitAreas, traceScrollX, halfW + 1, halfW - 1, h, absX + halfW + 1, absY,
					textPrimary, textPrimary, true, selectedCrumbId);

			// Trace scrollbar
			if (breadcrumbScrollbarVisible(traceContentW, scrollViewW)) {
				drawMiniScrollbar(canvas, halfW + 2, scrollBarY, halfW - 2, traceScrollX, traceContentW, scrollViewW);
			}

			updateBreadcrumbHoverFeedback(mx, my);
		});

		// Click handler — path clicks center on node, trace clicks scroll to node
		bottomPanel.onMouseClick((input, clickCount, ctx) -> {
			if (!input.isLeftClick()) {
				stopBreadcrumbScrollDrag();
				return EventDispatch.pass;
			}
			FloatPos local = input.mouseRelative(bottomPanel);
			double mx = local.x(), my = local.y();
			int halfW = bottomPanel.width() / 2;
			int scrollViewW = halfW - breadcrumbScrollViewWidthPadding;
			int scrollBarY = bottomPanel.height() - breadcrumbScrollbarThumbHeight - breadcrumbScrollbarBottomY;
			ScrollbarThumb pathThumb = buildScrollbarThumb(0, scrollBarY, halfW - 2,
					pathScrollX, pathContentW, scrollViewW);
			if (pathThumb != null && isPointInside(pathThumb, mx, my)) {
				activeScrollDrag = ScrollRegion.PATH;
				dragThumbOffsetX = (float) (mx - pathThumb.x);
				return EventDispatch.consumed;
			}
			if (pathThumb != null && isPointInsideTrack(pathThumb, mx, my)) {
				activeScrollDrag = ScrollRegion.PATH;
				dragThumbOffsetX = pathThumb.w / 2.0f;
				pathScrollTargetX = dragThumb(pathScrollTargetX, mx, 0,
						halfW - 2, scrollBarY, pathContentW, scrollViewW);
				return EventDispatch.consumed;
			}

			ScrollbarThumb traceThumb = buildScrollbarThumb(halfW + 2, scrollBarY,
					halfW - 2, traceScrollX, traceContentW, scrollViewW);
			if (traceThumb != null && isPointInside(traceThumb, mx, my)) {
				activeScrollDrag = ScrollRegion.TRACE;
				dragThumbOffsetX = (float) (mx - traceThumb.x);
				return EventDispatch.consumed;
			}
			if (traceThumb != null && isPointInsideTrack(traceThumb, mx, my)) {
				activeScrollDrag = ScrollRegion.TRACE;
				dragThumbOffsetX = traceThumb.w / 2.0f;
				traceScrollTargetX = dragThumb(traceScrollTargetX, mx, halfW + 2,
						halfW - 2, scrollBarY, traceContentW, scrollViewW);
				return EventDispatch.consumed;
			}

			for (var hit : pathHitAreas) {
				if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
					return selectAndJumpToBreadcrumb(hit.pathId());
				}
			}
			for (var hit : traceHitAreas) {
				if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
					return selectAndJumpToBreadcrumb(hit.pathId());
				}
			}
			return EventDispatch.pass;
		});

		bottomPanel.onMouseDragged((input, deltaX, deltaY, ctx) -> {
			if (activeScrollDrag == null) return EventDispatch.pass;
			if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
				stopBreadcrumbScrollDrag();
				return EventDispatch.pass;
			}
			FloatPos local = input.mouseRelative(bottomPanel);
			int halfW = bottomPanel.width() / 2;
			int scrollViewW = halfW - breadcrumbScrollViewWidthPadding;
			int scrollBarY = bottomPanel.height() - breadcrumbScrollbarThumbHeight - breadcrumbScrollbarBottomY;
			if (activeScrollDrag == ScrollRegion.PATH) {
				pathScrollTargetX = dragThumb(pathScrollTargetX, local.x(), 0,
						halfW - 2, scrollBarY, pathContentW, scrollViewW);
			} else {
				traceScrollTargetX = dragThumb(traceScrollTargetX, local.x(), halfW + 2,
						halfW - 2, scrollBarY, traceContentW, scrollViewW);
			}
			ctx.cancel();
			return EventDispatch.consumed;
		});

		bottomPanel.onMouseReleased((input, ctx) -> {
			if (activeScrollDrag == null) return EventDispatch.pass;
			stopBreadcrumbScrollDrag();
			return EventDispatch.consumed;
		});

		bottomPanel.onMouseLeave((mouseX, mouseY, ctx) -> {
			stopBreadcrumbScrollDrag();
			hoveredCrumbId = null;
			stepsWidget.setExternalHoveredNode(null);
			scene().setHoverTooltip(Tooltip.create());
		});

		// Independent scroll for left/right halves
		bottomPanel.onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
			FloatPos local = bottomPanel.sceneToLocal(mouseX, mouseY);
			int halfW = bottomPanel.width() / 2;
			int scrollViewW = Math.max(0, halfW - breadcrumbScrollViewWidthPadding);
			if (local.x() < halfW) {
				int maxScroll = maxBreadcrumbScroll(pathContentW, scrollViewW);
				if (maxScroll <= 0) {
					return EventDispatch.pass;
				}
				pathScrollTargetX = Math.max(0, Math.min(maxScroll, pathScrollTargetX + breadcrumbWheelState.wheelDelta(0, sy).y()));
			} else {
				int maxScroll = maxBreadcrumbScroll(traceContentW, scrollViewW);
				if (maxScroll <= 0) {
					return EventDispatch.pass;
				}
				traceScrollTargetX = Math.max(0, Math.min(maxScroll, traceScrollTargetX + breadcrumbWheelState.wheelDelta(0, sy).y()));
			}
			ctx.cancel();
			return EventDispatch.consumed;
		});

		addWidget(bottomPanel);
	}
	//endregion

	//region Rendering
	private void drawBreadcrumbRow(
			SceneCanvas canvas, Font font,
			double mx, double my,
			List<TreeCanvasWidget.BreadcrumbEntry> crumbs,
			List<BreadcrumbHit> hitAreas,
			float scrollX, int areaX, int areaW, int areaH,
			int absX, int absY,
			int iconColor, int accentColor,
			boolean isTrace,
			@Nullable String selectedPathId) {
		hitAreas.clear();
		float s = crumbScale;
		int rowY = (int) ((areaH - 9 * s) / 2) - 1;

		// Clip to area
		canvas.pushClip(absX, absY, areaW, areaH);
		canvas.pushTransform();
		canvas.translate(areaX - scrollX, 0);
		canvas.scale(s);

		float invS = 1f / s;
		int px = 3;
		int textY = (int) (rowY * invS);

		// Draw leading icon
		canvas.texture(isTrace ? Icon.Calc.Path.branch : Icon.Calc.Path.tree, px, textY - 2, crumbIconSize, crumbIconSize);
		px += 16;

		if (crumbs.isEmpty()) {
			canvas.drawString("--", px, textY, textSecondary, false);
		} else {
			for (int i = 0; i < crumbs.size(); i++) {
				var crumb = crumbs.get(i);
				String displayName = crumb.name();
				int nameW = font.width(displayName);
				boolean isLast = i == crumbs.size() - 1;

				if (i > 0) {
					canvas.texture(Icon.Small.arrowRight, px, textY + 1, 5, 6);
					px += 9;
				}

				// Hit area in panel-local coords (account for scroll and area offset)
				int hitX = (int) (px * s + areaX - scrollX);
				int hitY = rowY - 1;
				int hitW = (int) (nameW * s) + 2;
				int hitH = (int) (9 * s) + 2;
				hitAreas.add(new BreadcrumbHit(hitX, hitY, hitW, hitH, crumb.pathId()));

				// Hover detect in panel-local
				boolean isHover = mx >= hitX && mx <= hitX + hitW && my >= hitY && my <= hitY + hitH;
				boolean isSelected = Objects.equals(selectedPathId, crumb.pathId());
				if (isSelected) {
					canvas.fill(px - 2, textY - 2, nameW + 4, 12, 0x557FAFD6);
				}
				if (isHover) {
					canvas.fill(px - 1, textY - 1, nameW + 2, 11, 0x88FFFFFF);
				}

				int color = isSelected ? 0xFF7FAFD6 : (isLast ? accentColor : (isHover ? textPrimary : textSecondary));
				canvas.drawString(displayName, px, textY, color, false);
				px += nameW + 1;
			}
		}

		// Store total content width for scrollbar
		int contentW = (int) (px * s) + 3;
		if (isTrace) traceContentW = contentW;
		else pathContentW = contentW;

		canvas.popTransform();
		canvas.popClip();
	}

	private void drawMiniScrollbar(
			SceneCanvas canvas,
			int barX, int barY, int barW,
			float scrollPos, int contentW, int viewW) {
		if (!breadcrumbScrollbarVisible(contentW, viewW)) {
			return;
		}
		int trackY = barY + (breadcrumbScrollbarThumbHeight - breadcrumbScrollbarTrackHeight) / 2;
		canvas.texture(Background.Slot.dark, barX, trackY, barW, breadcrumbScrollbarTrackHeight);
		int thumbW = Math.max(8, barW * viewW / contentW);
		int maxScroll = Math.max(1, maxBreadcrumbScroll(contentW, viewW));
		int thumbX = barX + (int) ((barW - thumbW) * (scrollPos / maxScroll));
		int thumbY = barY;
		canvas.texture(Icon.scrollBarVertical, thumbX, thumbY, thumbW, breadcrumbScrollbarThumbHeight);
	}

	private void stopBreadcrumbScrollDrag() {
		activeScrollDrag = null;
		dragThumbOffsetX = 0;
	}
	//endregion

	//region Interactions
	private int computeBreadcrumbContentWidth(Font font, List<TreeCanvasWidget.BreadcrumbEntry> crumbs) {
		int px = 3;
		px += 16; // leading icon
		if (crumbs.isEmpty()) {
			px += font.width("--");
		} else {
			for (int i = 0; i < crumbs.size(); i++) {
				if (i > 0) px += 9; // arrow
				px += font.width(crumbs.get(i).name()) + 1;
			}
		}
		return (int) (px * crumbScale) + 3;
	}

	private void sanitizeBreadcrumbScroll(int scrollViewW) {
		int pathMaxScroll = maxBreadcrumbScroll(pathContentW, scrollViewW);
		int traceMaxScroll = maxBreadcrumbScroll(traceContentW, scrollViewW);
		pathScrollTargetX = Math.max(0, Math.min(pathMaxScroll, pathScrollTargetX));
		traceScrollTargetX = Math.max(0, Math.min(traceMaxScroll, traceScrollTargetX));
		pathScrollX = Math.max(0, Math.min(pathMaxScroll, pathScrollX));
		traceScrollX = Math.max(0, Math.min(traceMaxScroll, traceScrollX));
	}

	private EventDispatch selectAndJumpToBreadcrumb(String pathId) {
		selectedCrumbId = pathId;
		stepsWidget.setExternalSelectedNode(pathId);
		stepsWidget.focusNodeWithoutCropping(pathId);
		Minecraft.getInstance().getSoundManager()
				 .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		return EventDispatch.consumed;
	}

	private void updateBreadcrumbHoverFeedback(double mx, double my) {
		if (my < 0 || my > bottomPanel.height()) {
			return;
		}
		BreadcrumbHit hovered = null;
		String hoverSource = null;
		for (var hit : pathHitAreas) {
			if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
				hovered = hit;
				hoverSource = "Tree Path";
				break;
			}
		}
		if (hovered == null) {
			for (var hit : traceHitAreas) {
				if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
					hovered = hit;
					hoverSource = "Trace Path";
					break;
				}
			}
		}
		hoveredCrumbId = hovered != null ? hovered.pathId() : null;
		stepsWidget.setExternalHoveredNode(hoveredCrumbId);
		if (hoveredCrumbId == null) {
			return;
		}
		Tooltip tip = stepsWidget.buildNodeTooltipForPath(hoveredCrumbId, hoverSource);
		scene().setHoverTooltip(tip != null ? tip : singleLineTooltip("Path: " + hoveredCrumbId));
	}

	public void setGraph(ProcessGraph graph) {
		this.graph = graph;
		this.selectedCrumbId = null;
		stepsWidget.setGraph(graph);
		stepsWidget.setExternalSelectedNode(null);
		rebuildLayerNav(graph);
	}

	void setCraftCount(long count) {
		stepsWidget.setCraftCount(count);
	}

	void setOnProjectionEdit(@Nullable Consumer<RecipeEntry<?>> callback) {
		stepsWidget.setOnProjectionEdit(callback);
	}

	void setOnContentCandidateSelected(@Nullable Consumer<TreeCanvasWidget.ContentCandidateSelection> callback) {
		stepsWidget.setOnContentCandidateSelected(callback);
	}

	void setContentVariantPreferences(Map<String, String> preferences) {
		stepsWidget.setContentVariantPreferences(preferences);
	}

	TreeCanvasWidget.LayoutDebugInfo debugLayoutInfo() {
		return stepsWidget.debugLayoutInfo();
	}

	void requestInitialFocus() {
		pendingCanvasFocus = true;
	}

	boolean debugIsMaterialPanelVisible() {
		return stepsWidget.isMaterialPanelVisible();
	}

	int debugMaterialEntryCount() {
		return stepsWidget.debugMaterialEntryCount();
	}

	boolean debugIsRootFocused() {
		return "root".equals(stepsWidget.getCurrentRootPathId());
	}

	boolean debugCanvasPendingAutoFit() {
		return stepsWidget.debugPendingAutoFit();
	}

	long debugCraftCount() {
		return stepsWidget.debugCraftCount();
	}

	void toggleMaterialPanel() {
		stepsWidget.toggleMaterialPanel();
	}

	void resetToFullTree() {
		stepsWidget.resetToFullTree();
	}

	void expandAllNodes() {
		stepsWidget.expandAllNodes();
	}

	boolean debugIsForceExpanded() {
		return stepsWidget.isForceExpandAll();
	}

	private void rebuildLayerNav(ProcessGraph graph) {
		layerButtonsContainer.clear();

		maxLayers = stepsWidget.getMaxDepth();

		for (int i = 0; i <= maxLayers; i++) {
			if (stepsWidget.getLayerNodeCount(i) > 0) {
				layerButtonsContainer.addWidget(new LayerButton(i));
			}
		}
		syncLayerTrackingFromViewport();
	}

	private void renderLayerScrollIndicators(SceneCanvas canvas, Widget self) {
		int maxScroll = layerScrollState.maxScrollY();
		if (maxScroll <= 0) {
			return;
		}
		int contentWidth = Math.max(0, self.width() - 1);
		int iconX = Math.max(0, (contentWidth - layerScrollIndicatorSize) / 2);
		if (layerScrollState.targetScrollY() > 0.5f) {
			int y = Math.min(
					Math.max(layerHeaderHeight + layerScrollIndicatorInset, layerHeaderHeight),
					Math.max(layerHeaderHeight, self.height() - layerScrollIndicatorSize)
			);
			canvas.fill(0, y - 1, contentWidth, layerScrollIndicatorSize + 2, 0x66000000);
			canvas.texture(Icon.arrowUpSelected, iconX, y, layerScrollIndicatorSize, layerScrollIndicatorSize);
		}
		if (layerScrollState.targetScrollY() < maxScroll - 0.5f) {
			int y = Math.max(layerHeaderHeight, self.height() - layerScrollIndicatorSize - layerScrollIndicatorInset);
			canvas.fill(0, y - 1, contentWidth, layerScrollIndicatorSize + 2, 0x66000000);
			canvas.texture(Icon.arrowDownSelected, iconX, y, layerScrollIndicatorSize, layerScrollIndicatorSize);
		}
	}

	private void setActiveLayer(int layer) {
		int targetLayer = nearestExistingLayer(layer);
		this.activeLayer = targetLayer;
		updateLayerButtons(targetLayer);
		// Sync highlight to canvas and auto-center
		stepsWidget.setHighlightedLayer(targetLayer);
		stepsWidget.centerOnLayer(targetLayer, Math.max(0.25f, stepsWidget.getZoom()));
	}

	private int nearestExistingLayer(int layer) {
		int nearest = layer;
		int bestDist = Integer.MAX_VALUE;
		for (var child : layerButtonsContainer.children()) {
			if (child instanceof LayerButton lb) {
				int d = Math.abs(lb.layer - layer);
				if (d < bestDist) {
					bestDist = d;
					nearest = lb.layer;
				}
			}
		}
		return nearest;
	}

	private void syncLayerTrackingFromViewport() {
		if (graph == null) return;
		int viewportLayer = nearestExistingLayer(stepsWidget.getViewportCenterLayer());
		if (viewportLayer == activeLayer) return;
		activeLayer = viewportLayer;
		updateLayerButtons(viewportLayer);
		stepsWidget.setHighlightedLayer(viewportLayer);
	}

	private @Nullable ScrollbarThumb buildScrollbarThumb(
			int barX, int barY, int barW,
			float scrollPos, int contentW, int viewW) {
		if (contentW <= viewW || contentW <= 0) return null;
		int thumbW = Math.max(8, barW * viewW / contentW);
		int maxScroll = Math.max(1, contentW - viewW);
		int thumbX = barX + (int) ((barW - thumbW) * (scrollPos / maxScroll));
		int thumbY = barY;
		return new ScrollbarThumb(thumbX, thumbY, thumbW, breadcrumbScrollbarThumbHeight, barX, barW, maxScroll);
	}

	private static boolean isPointInside(ScrollbarThumb thumb, double x, double y) {
		return x >= thumb.x && x <= thumb.x + thumb.w && y >= thumb.y && y <= thumb.y + thumb.h;
	}

	private static boolean isPointInsideTrack(ScrollbarThumb thumb, double x, double y) {
		return x >= thumb.barX && x <= thumb.barX + thumb.barW && y >= thumb.y && y <= thumb.y + thumb.h;
	}

	private float dragThumb(float currentValue, double mouseX, int barX, int barW, int barY, int contentW, int viewW) {
		ScrollbarThumb thumb = buildScrollbarThumb(barX, barY, barW, currentValue, contentW, viewW);
		if (thumb == null) return 0;
		float track = Math.max(1, barW - thumb.w);
		float thumbLeft = (float) Math.max(barX, Math.min(mouseX - dragThumbOffsetX, barX + track));
		float progress = track <= 0 ? 0 : (thumbLeft - barX) / track;
		return Math.max(0, Math.min(thumb.maxScroll, progress * thumb.maxScroll));
	}
	//endregion

	//region Public API
	static int computeThumbWidth(int barW, int contentW, int viewW) {
		return Math.max(8, barW * viewW / Math.max(contentW, 1));
	}

	static boolean debugBreadcrumbScrollbarVisible(int contentW, int viewW) {
		return breadcrumbScrollbarVisible(contentW, viewW);
	}

	private static boolean breadcrumbScrollbarVisible(int contentW, int viewW) {
		return maxBreadcrumbScroll(contentW, viewW) > 0;
	}

	private static int maxBreadcrumbScroll(int contentW, int viewW) {
		return Math.max(0, contentW - Math.max(0, viewW));
	}

	static String layerLabel(int layer) {
		return "L" + layer;
	}

	static int debugLayerNavWidth() {
		return layerNavWidth;
	}

	static int debugBottomStripHeight() {
		return bottomPanelHeight;
	}

	static int debugBreadcrumbScrollbarY(int panelHeight) {
		return panelHeight - breadcrumbScrollbarThumbHeight - breadcrumbScrollbarBottomY;
	}

	static int debugLayerHeaderHeight() {
		return layerHeaderHeight;
	}

	static int debugLayerHeaderIconSize() {
		return layerHeaderIconSize;
	}

	static boolean debugLayerHeaderUsesIconOnly() {
		return true;
	}

	static boolean debugLayerScrollbarHidden() {
		return true;
	}

	static int debugLayerScrollViewportInset() {
		return 0;
	}

	static int debugLayerScrollIndicatorSize() {
		return layerScrollIndicatorSize;
	}

	private static Tooltip singleLineTooltip(String text) {
		var tooltip = Tooltip.create();
		tooltip.add(Component.literal(text));
		return tooltip;
	}

	/**
	 * Update layer button highlight state without changing the canvas.
	 */
	public void updateLayerButtons(int layer) {
		this.activeLayer = layer;
		for (var child : layerButtonsContainer.children()) {
			if (child instanceof LayerButton lb) {
				lb.updateActive(layer);
			}
		}
	}

	private void applyPendingCanvasFocus() {
		if (!pendingCanvasFocus || !lifecycle().mounted() || !stepsWidget.lifecycle().mounted()) {
			return;
		}
		scene().requestFocus(stepsWidget);
		pendingCanvasFocus = false;
	}
	//endregion

	// ═══════════════════ LayerButton ═══════════════════

	private class LayerButton extends CompositeWidget<Widget> {

		private final int layer;
		private boolean active = false;

		LayerButton(int layer) {
			this.layer = layer;
			useStyle(alignSelfStretch(), heightOf(20), minHeight(20), maxHeight(20), flexShrink(0), paddingHorizontal(2), marginBottom(1));
			updateActive(activeLayer);
			int nodeCount = stepsWidget.getLayerNodeCount(layer);
			setTooltip(singleLineTooltip("层级 " + layer + " · " + nodeCount + " 节点"));

			onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
				int w = width(), h = height();
				String label = layerLabel(layer);

				if (active) {
					canvas.texture(Background.Outlined.inset, 0, 0, w, h);
					canvas.fill(1, 1, w - 2, h - 2, 0x33FFFFFF);
					canvas.fill(1, 1, 3, h - 2, green);
				} else if (hovered()) {
					canvas.texture(Background.Outlined.flat, 0, 0, w, h);
				} else {
					canvas.texture(Background.flat, 0, 0, w, h);
				}

				int labelW = Minecraft.getInstance().font.width(label);
				canvas.drawString(label, (w - labelW) / 2, h / 2 - 3,
						active ? textPrimary : textSecondary, false);
			});

			onMouseClick((input, clickCount, context) -> {
				setActiveLayer(layer);
				Minecraft.getInstance().getSoundManager()
						 .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
				return EventDispatch.consumed;
			});
		}

		void updateActive(int currentLayer) {
			this.active = (layer == currentLayer);
		}
	}

	private static final class LayerHeaderWidget extends Widget {

		private LayerHeaderWidget() {
			useStyle(heightOf(layerHeaderHeight), minHeight(layerHeaderHeight), maxHeight(layerHeaderHeight), flexShrink(0), marginBottom(0));
			setTooltip(singleLineTooltip("层级栏：点击层号以高亮并定位该层节点"));
		}

		@Override
		protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
			int w = width();
			canvas.texture(Background.flat, 0, 0, w, height());
			canvas.texture(Icon.Calc.layerNav, (w - layerHeaderIconSize) / 2, (height() - layerHeaderIconSize) / 2, layerHeaderIconSize, layerHeaderIconSize);
		}
	}
}
