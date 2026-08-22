package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRoutingRule;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cirrus.ui.CirrusUiAsync;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.CalculatorBuildResultCache;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.CalculatorSettingsPage;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.util.ScreenUtil;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textMuted;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginBottom;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Main Calculator view page — replicates omnicalc-pro's CalculatorView.
 * </pre>
 */
public class OmniCalculatorView extends CompositeWidget<Widget> {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Calculator");

	//region Types
	public enum ProcessView {TREE, MATRIX, MILESTONES}
	//endregion

	//region State
	private ProcessView processView = ProcessView.TREE;
    private boolean calculated = false;
    private boolean settingsPanelOpen = false;
    private boolean calculatingStateVisible = false;
    private long amount = 1;
    private CalculatorProcessGraphBuilder.RouteMode graphRouteMode = CalculatorProcessGraphBuilder.RouteMode.calculator;
    private ProcessGraph graph = new ProcessGraph();
	private @Nullable RoutingPlan routingPlan;
	private long calculationRequestId = 0L;
    private Map<String, String> contentVariantPreferences = Map.of();
	private final Set<String> milestoneManualCompletions = new HashSet<>();
	private @Nullable CompletableFuture<CalculationResult> pendingCalculation;
    private long observedCalculatorTargetVersion = Long.MIN_VALUE;
    private String calculationDebugText = "";
    private List<String> calculationDebugTrace = List.of();
	//endregion

	//region Widgets
	private final ControlBarWidget controlBar;
	private final WidgetGroup<Widget> bodyArea;

	private ViewSwitcherWidget viewSwitcher;
	private boolean deepenActionAvailable;
	private WidgetGroup<Widget> processViewArea;
	private TreeViewWidget treeView;
    private MatrixViewWidget matrixView;
    private MilestonesViewWidget milestonesView;
    private @Nullable Widget suspendedProjectionReturnContent;
    private @Nullable DatabasePage projectionDetailPage;

	private WidgetGroup<Widget> emptyState;
	private CalculatorSettingsPage settingsPage;
	//endregion

	//region Init
	public OmniCalculatorView() {
		setFocusable(true);
        onDestroy(self -> releaseCalculationState());
		useEffect(UIEffects.autoFocus());
		useStyle(
				flexColumn(),
				sizeFull(),
				background(Background.inset)
		);
		setTickable(true);
		onKeyPressed((input, context) -> {
			int key = input.key().getValue();
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				onCalculate();
				return EventDispatch.consumed;
			}
			return EventDispatch.pass;
		});

		// ── Control Bar (top, sticky) ──
		controlBar = new ControlBarWidget(this);
		CalculatorTargetRecipeState.Selection initialSelection = CalculatorTargetRecipeState.current();
        observedCalculatorTargetVersion = CalculatorTargetRecipeState.currentVersion();
		applyCalculatorTarget(initialSelection);
		addWidget(controlBar);

		// ── Body area (below control bar) ──
		bodyArea = new WidgetGroup<>();
		bodyArea.useStyle(
				flexRow(),
				flexGrow(1),
				minHeight(0),
				background(Background.inset)
		);
		addWidget(bodyArea);

		if (CalculatorTargetRecipeState.current() == null) {
			showEmptyState();
		} else {
			onCalculate();
		}
	}
	//endregion

	//region View Management
	@Override
	public void tick() {
		super.tick();
        applyExternalCalculatorTargetChange();
		applyCompletedCalculation();
	}

	private void showEmptyState() {
		bodyArea.clear();
        suspendedProjectionReturnContent = null;
        projectionDetailPage = null;
		calculated = false;
		settingsPanelOpen = false;
		calculatingStateVisible = false;

		emptyState = new WidgetGroup<>();
		emptyState.useStyle(
				flexColumn(),
				flexGrow(1),
				alignItemsCenter(),
				justifyCenter()
		);

		var icon = new Widget();
		icon.useStyle(marginBottom(4), sizeOf(24, 24));
		icon.onRender((canvas, mx, my, pt, self, ctx) -> {
			canvas.texture(Background.Slot.dark, 3, 3, 18, 18);
			canvas.texture(Icon.Calc.Tree.tabUnselected, 4, 4, 16, 16);
		});
		emptyState.addWidget(icon);

		var readyLabel = LabelWidget.of(CirrusLangs.Calc.empty)
									.setColor(textMuted)
									.setShadow(false);
		emptyState.addWidget(readyLabel);

		bodyArea.addWidget(emptyState);
		refreshHoverTooltipSoon();
	}

	private void showCalculatingState() {
		bodyArea.clear();
        suspendedProjectionReturnContent = null;
        projectionDetailPage = null;
		calculated = false;
		settingsPanelOpen = false;
		calculatingStateVisible = true;
		treeView = null;
		matrixView = null;
		milestonesView = null;

		var calculatingState = new WidgetGroup<Widget>();
		calculatingState.useStyle(
				flexColumn(),
				flexGrow(1),
				alignItemsCenter(),
				justifyCenter()
		);

		var icon = new Widget();
		icon.useStyle(marginBottom(4), sizeOf(24, 24));
		icon.onRender((canvas, mx, my, pt, self, ctx) -> {
			canvas.texture(Background.Slot.dark, 3, 3, 18, 18);
			canvas.texture(Icon.Calc.calculate, 4, 4, 16, 16);
		});
		calculatingState.addWidget(icon);

		var label = LabelWidget.of(CirrusLangs.Calc.calculating)
							   .setColor(textMuted)
							   .setShadow(false);
		calculatingState.addWidget(label);

		bodyArea.addWidget(calculatingState);
		refreshHoverTooltipSoon();
	}

	void showCalculationResult() {
        showCalculationResult("unspecified");
	}

	private void showCalculationResult(String reason) {
        if (pendingCalculation != null && !pendingCalculation.isDone()) {
            logger.warn(
                "calculator.ui.showResultWhilePending reason={} requestId={} target={} graphNodes={} debug={}",
                reason,
                calculationRequestId,
                CalculatorTargetRecipeState.current() == null ? "none" : describeRecipe(CalculatorTargetRecipeState.current().recipe()),
                graph.nodeCount(),
                calculationDebugText
            );
        } else if (graph.nodeCount() == 0) {
            logger.warn(
                "calculator.ui.showResultEmptyGraph reason={} requestId={} target={} pending={} calculated={} debug={}",
                reason,
                calculationRequestId,
                CalculatorTargetRecipeState.current() == null ? "none" : describeRecipe(CalculatorTargetRecipeState.current().recipe()),
                pendingCalculation != null,
                calculated,
                calculationDebugText
            );
        } else if (logger.isDebugEnabled()) {
            logger.debug(
                "calculator.ui.showResult reason={} requestId={} graphNodes={}",
                reason,
                calculationRequestId,
                graph.nodeCount()
            );
        }
		ProcessView viewToShow = processView;
		bodyArea.clear();
        suspendedProjectionReturnContent = null;
        projectionDetailPage = null;
		calculated = true;
		settingsPanelOpen = false;
		calculatingStateVisible = false;
		treeView = null;
		matrixView = null;
		milestonesView = null;

		// ── Left area: View Switcher + Process View ──
		var leftArea = new WidgetGroup<>();
		leftArea.useStyle(
				flexColumn(),
				flexGrow(1),
				minWidth(0)
		);

		// Pending-input banner (budget-limited plans the user can deepen)
		Widget pendingBanner = createPendingInputsBanner();
		if (pendingBanner != null) {
			leftArea.addWidget(pendingBanner);
		}

		// View switcher tabs
		viewSwitcher = new ViewSwitcherWidget(this);
		viewSwitcher.setDeepenAvailable(deepenActionAvailable);
		leftArea.addWidget(viewSwitcher);

		// Process view container
		processViewArea = new WidgetGroup<>();
		processViewArea.useStyle(
				flexGrow(1),
				minHeight(0)
		);
		leftArea.addWidget(processViewArea);
		bodyArea.addWidget(leftArea);

		switchProcessView(viewToShow);
		refreshHoverTooltipSoon();
	}

	/**
	 * Banner shown above the process views when the routed plan left inputs
	 * unresolved: reports how many inputs still lack a source and offers a
	 * deepen button that re-runs the router (bypassing the build cache) with
	 * the deepen budget waves forced on. A pending input counts as continuable
	 * when its candidate set was never fully tried (or never enumerated).
	 */
	private @Nullable Widget createPendingInputsBanner() {
		RoutingPlan plan = routingPlan;
		if (plan == null) {
			return null;
		}
		int pending = 0;
		int continuable = 0;
		for (PlanAction action : plan.actions()) {
			if (action instanceof PlanAction.PendingInput pendingInput) {
				pending++;
				if (pendingInput.candidateCount() == 0 || pendingInput.attemptedCount() < pendingInput.candidateCount()) {
					continuable++;
				}
			}
		}
		deepenActionAvailable = continuable > 0;
		if (viewSwitcher != null) {
			viewSwitcher.setDeepenAvailable(deepenActionAvailable);
		}
		if (pending == 0) {
			return null;
		}
		// the deepen action lives on the view toolbar next to expand; the
		// "have no source yet" text banner is intentionally gone
		return null;
	}

	void deepenCalculation() {
		if (pendingCalculation != null && !pendingCalculation.isDone()) {
			return;
		}
		CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
		if (selection == null) {
			return;
		}
		startCalculatorSelectionGraph(selection, true);
	}

	void switchProcessView(ProcessView view) {
		this.processView = view;
		if (processViewArea == null) return;
		ensureProcessViewWidgets(view);
		setProcessViewVisible(treeView, view == ProcessView.TREE);
		setProcessViewVisible(matrixView, view == ProcessView.MATRIX);
		setProcessViewVisible(milestonesView, view == ProcessView.MILESTONES);

		if (viewSwitcher != null) {
			viewSwitcher.setActiveView(view);
			viewSwitcher.setTreeActionsVisible(view == ProcessView.TREE);
		}
		refreshHoverTooltipSoon();
	}

	private void ensureProcessViewWidgets(ProcessView requestedView) {
		if (requestedView == ProcessView.TREE && treeView == null) {
			treeView = createTreeView();
			addProcessViewWidget(treeView);
		}
		if (requestedView == ProcessView.MATRIX && matrixView == null) {
			matrixView = createMatrixView();
			addProcessViewWidget(matrixView);
		}
		if (requestedView == ProcessView.MILESTONES && milestonesView == null) {
			milestonesView = createMilestonesView();
			addProcessViewWidget(milestonesView);
		}
	}

	private void addProcessViewWidget(Widget widget) {
		widget.useStyle(flexGrow(1), minWidth(0), minHeight(0));
		processViewArea.addWidget(widget);
	}

	private static void setProcessViewVisible(@Nullable Widget widget, boolean visible) {
		if (widget == null) {
			return;
		}
		widget.setVisible(visible);
		widget.useStyle(visible ? displayFlex() : displayNone());
	}

	//endregion

	//region Public API
	ProcessView currentProcessView() {
		return processView;
	}

	boolean isCalculated() {
		return calculated;
	}

	long getAmount() {
		return amount;
	}

	void showSettingsPanel() {
		bodyArea.clear();
        suspendedProjectionReturnContent = null;
        projectionDetailPage = null;
		settingsPanelOpen = true;
		calculatingStateVisible = false;
		settingsPage = new CalculatorSettingsPage();
		settingsPage.useStyle(flexGrow(1), minWidth(0), minHeight(0));
		bodyArea.addWidget(settingsPage);
		refreshHoverTooltipSoon();
	}

	void toggleSettingsPanel() {
		if (settingsPanelOpen) {
			if (calculated) {
				showCalculationResult("toggleSettingsPanel");
			} else {
				showEmptyState();
			}
		} else {
			showSettingsPanel();
		}
	}

    void setAmount(long amount) {
        this.amount = amount;
    }

    void setCurrentPathAsExecutionTarget() {
        if (!canSetCurrentPathAsExecutionTarget()) {
            return;
        }
        RoutingPlan currentPlan = routingPlan;
        if (currentPlan == null) {
            currentPlan = routeCurrentCalculatorSelection();
            routingPlan = currentPlan;
        }
        if (currentPlan == null) {
            return;
        }
        ExecutionTargetRecipeState.setPath(currentPlan.targetRecipe(), currentPlan.targetTimes(), currentPlan);
    }

	public void setProcessGraph(ProcessGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        applyCalculatorTarget(CalculatorTargetRecipeState.current());
		if (calculated) {
			showCalculationResult("externalSetProcessGraph");
		}
	}

    public void releaseCalculationState() {
        cancelPendingCalculation();
        graph = new ProcessGraph();
        routingPlan = null;
        contentVariantPreferences = Map.of();
        milestoneManualCompletions.clear();
        calculationDebugText = "";
        calculationDebugTrace = List.of();
        calculated = false;
        settingsPanelOpen = false;
        calculatingStateVisible = false;
        if (treeView != null) {
            treeView.releaseGraph();
        }
        treeView = null;
        matrixView = null;
        milestonesView = null;
        viewSwitcher = null;
        processViewArea = null;
        suspendedProjectionReturnContent = null;
        projectionDetailPage = null;
        settingsPage = null;
        bodyArea.clear();
    }
	//endregion

	//region View Factory
	private TreeViewWidget createTreeView() {
		var treeView = new TreeViewWidget();
		treeView.setGraph(graph);
		treeView.setCraftCount(amount);
		treeView.setContentVariantPreferences(contentVariantPreferences);
		treeView.setOnContentCandidateSelected(this::onContentCandidateSelected);
		treeView.setOnProjectionEdit(this::openProjectionDetail);
		treeView.requestInitialFocus();
		logger.info(
            "calculator.ui.treeLayout target={} {}",
            CalculatorTargetRecipeState.current() == null ? "none" : describeRecipe(CalculatorTargetRecipeState.current().recipe()),
            treeView.debugLayoutInfo().toDebugText()
        );
		return treeView;
	}

	private MatrixViewWidget createMatrixView() {
		return new MatrixViewWidget(graph);
	}

	private MilestonesViewWidget createMilestonesView() {
		return new MilestonesViewWidget(
				graph,
				milestoneManualCompletions,
				MilestonesViewWidget::countStorageAvailable
		);
	}
	//endregion

	//region Actions
	public void onCalculate() {
		amount = controlBar.getAmount();
		startCalculatorSelectionGraph(CalculatorTargetRecipeState.current());
	}

	private void onContentCandidateSelected(TreeCanvasWidget.ContentCandidateSelection selection) {
		LinkedHashMap<String, String> next = new LinkedHashMap<>(contentVariantPreferences);
		next.put(selection.inputKey(), selection.variantKey());
		contentVariantPreferences = Map.copyOf(next);
		startCalculatorSelectionGraph(CalculatorTargetRecipeState.current());
	}
	//endregion

	//region Helpers
	private static ItemStack resolveTargetItem(ProcessGraph graph) {
		if (graph.sinks().isEmpty()) return ItemStack.EMPTY;
		var sink = graph.sinks().getFirst();
		if (sink.outputs().isEmpty()) return ItemStack.EMPTY;
		var output = sink.outputs().getFirst();
		ContentStack<?, ?> stack = output.visualStack();
		if (stack.notEmpty() && stack.isItem()) {
			return stack.<net.minecraft.world.item.Item, ItemStack>cast().value().copy();
		}
		return ItemStack.EMPTY;
	}

	private void applyCalculatorTarget(@Nullable CalculatorTargetRecipeState.Selection selection) {
		if (selection == null) {
			controlBar.setTargetItem(resolveTargetItem(graph));
			return;
		}
		amount = selection.times();
		controlBar.setAmount(selection.times());
		controlBar.setTargetItem(CalculatorTargetRecipeState.previewStack(selection.recipe()));
	}

	private void startCalculatorSelectionGraph(@Nullable CalculatorTargetRecipeState.Selection selection) {
        startCalculatorSelectionGraph(selection, false);
	}

	private void startCalculatorSelectionGraph(@Nullable CalculatorTargetRecipeState.Selection selection, boolean forceDeepen) {
        observedCalculatorTargetVersion = CalculatorTargetRecipeState.currentVersion();
		cancelPendingCalculation();
		if (selection == null) {
			calculationRequestId++;
			routingPlan = null;
			showCalculationResult("noSelection");
			return;
		}
		int times = amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) amount);
		long requestId = ++calculationRequestId;
		long requestedAmount = amount;
		RecipeEntry<?> recipe = selection.recipe();
        CalculatorProcessGraphBuilder.RouteMode requestedRouteMode = graphRouteMode;
        calculationDebugText = "queued target=" + describeRecipe(recipe) + " times=" + times + " mode=" + graphRouteMode;
        calculationDebugTrace = List.of(calculationDebugText);
		Stock stock = RecipePathSettings.calculatorStock();
		Map<String, String> preferences = contentVariantPreferences;
        logger.info(
            "calculator.ui.start requestId={} target={} times={} requestedAmount={} mode={} stockEmpty={} preferredVariants={}",
            requestId,
            describeRecipe(recipe),
            times,
            requestedAmount,
            graphRouteMode,
            stock.isEmpty(),
            preferences.size()
        );
		routingPlan = null;
		controlBar.setTargetItem(CalculatorTargetRecipeState.previewStack(selection.recipe()));
		showCalculatingState();
        pendingCalculation = CirrusUiAsync.recipeTask(() -> {
            long startNanos = System.nanoTime();
            logger.info(
                "calculator.ui.worker.start requestId={} target={} times={} mode={}",
                requestId,
                describeRecipe(recipe),
                times,
                graphRouteMode
            );
            try {
                CalculatorProcessGraphBuilder.BuildResult buildResult = buildResultCached(
                    recipe,
                    times,
                    stock,
                    preferences,
                    requestedRouteMode,
                    forceDeepen
                );
                logger.info(
                    "calculator.ui.worker.finish requestId={} target={} elapsedMs={} graphNodes={} planActions={} debug={}",
                    requestId,
                    describeRecipe(recipe),
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    buildResult.graph().nodeCount(),
                    buildResult.routingPlan() == null ? 0 : buildResult.routingPlan().actions().size(),
                    buildResult.debugInfo().toDebugText()
                );
                return new CalculationResult(
                    requestId,
                    recipe,
                    requestedAmount,
                    buildResult.routeMode(),
                    buildResult.graph(),
                    buildResult.routingPlan(),
                    buildResult.debugInfo(),
                    buildResult.debugTrace()
                );
            } catch (RuntimeException exception) {
                logger.error(
                    "calculator.ui.worker.exception requestId={} target={} elapsedMs={} cause={}",
                    requestId,
                    describeRecipe(recipe),
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    describeThrowable(exception),
                    exception
                );
                throw exception;
            }
		});
    }

	private void applyCompletedCalculation() {
		CompletableFuture<CalculationResult> future = pendingCalculation;
		if (future == null || !future.isDone()) {
			return;
		}
		consumeCompletedCalculation(future);
	}

    public void refreshCalculatorTargetFromState() {
        if (projectionDetailOpen()) {
            return;
        }
        long currentVersion = CalculatorTargetRecipeState.currentVersion();
        if (currentVersion == observedCalculatorTargetVersion) {
            return;
        }
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
        observedCalculatorTargetVersion = currentVersion;
        applyCalculatorTarget(selection);
        startCalculatorSelectionGraph(selection);
    }

    private void applyExternalCalculatorTargetChange() {
        refreshCalculatorTargetFromState();
    }

    private boolean projectionDetailOpen() {
        return projectionDetailPage != null
            && projectionDetailPage.parent() == bodyArea
            && projectionDetailPage.debugRecipeDetailOpen();
    }

    private void completeCalculation(CalculationResult result) {
        if (result.requestId() != calculationRequestId) {
            return;
        }
        graphRouteMode = result.routeMode();
        graph = result.graph();
        routingPlan = result.routingPlan();
        calculationDebugText = result.debugInfo().toDebugText();
        calculationDebugTrace = result.debugTrace();
        amount = result.amount();
        controlBar.setAmount(result.amount());
        controlBar.setTargetItem(CalculatorTargetRecipeState.previewStack(result.recipe()));
        String graphIssue = CalculatorProcessGraphBuilder.debugTargetGraphIssueForTests(result.recipe(), result.graph());
        boolean failed = result.debugInfo().routingStatus() == RoutingStatus.failed
            || result.debugInfo().exception() != null
            || graphIssue != null;
        if (failed || result.debugInfo().budgetExhausted()) {
            logger.warn(
                "calculator.ui.finish requestId={} target={} mode={} graphNodes={} graphIssue={} planActions={} debug={}",
                result.requestId(),
                describeRecipe(result.recipe()),
                result.routeMode(),
                result.graph().nodeCount(),
                graphIssue,
                result.routingPlan() == null ? 0 : result.routingPlan().actions().size(),
                result.debugInfo().toDebugText()
            );
        } else {
            logger.info(
                "calculator.ui.finish requestId={} target={} mode={} graphNodes={} planActions={} debug={}",
                result.requestId(),
                describeRecipe(result.recipe()),
                result.routeMode(),
                result.graph().nodeCount(),
                result.routingPlan() == null ? 0 : result.routingPlan().actions().size(),
                result.debugInfo().toDebugText()
            );
        }
        if (!result.debugTrace().isEmpty()) {
            logger.debug(
                "calculator.ui.finish trace requestId={}\n{}",
                result.requestId(),
                String.join("\n", result.debugTrace())
            );
        }
        showCalculationResult("calculationComplete");
	}

	private void consumeCompletedCalculation(CompletableFuture<CalculationResult> future) {
		pendingCalculation = null;
		try {
			completeCalculation(future.join());
		} catch (CompletionException exception) {
			Throwable cause = exception.getCause() == null ? exception : exception.getCause();
			completeFailedCalculation(cause);
		} catch (CancellationException exception) {
			logger.debug("calculator.ui.cancelled requestId={}", calculationRequestId);
		}
	}

	private @Nullable RoutingPlan routeCurrentCalculatorSelection() {
		CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
		if (selection == null) {
			return null;
		}
		return CalculatorProcessGraphBuilder.routePlan(
			selection.recipe(),
			requestedRoutingTimes(),
			RecipePathSettings.calculatorStock(),
			contentVariantPreferences,
			graphRouteMode
		);
	}

	/**
	 * Runs the calculator build through the session result cache: reopening the
	 * calculator screen or re-picking the same target replays the previous
	 * result instead of rerunning the full routing pipeline. Calculations with
	 * live stock bypass the cache — inventory mutates under the result, so a
	 * replayed plan would be stale. {@code forceDeepen} (the pending-input
	 * banner's deepen action) skips the cache read so the router re-runs with
	 * deepen waves forced on, then stores the deeper result under the same key.
	 */
	private static CalculatorProcessGraphBuilder.BuildResult buildResultCached(
		RecipeEntry<?> recipe,
		int times,
		Stock stock,
		Map<String, String> preferences,
		CalculatorProcessGraphBuilder.RouteMode routeMode,
		boolean forceDeepen
	) {
		if (!stock.isEmpty()) {
			return CalculatorProcessGraphBuilder.buildResult(recipe, times, stock, preferences, routeMode, forceDeepen);
		}
		CalculatorBuildResultCache.Key key = new CalculatorBuildResultCache.Key(
			recipe,
			times,
			preferences,
			routeMode,
			calculatorSettingsFingerprint(recipe)
		);
		CalculatorProcessGraphBuilder.BuildResult cached = forceDeepen ? null : CalculatorBuildResultCache.get(key);
		if (cached != null) {
			logger.info(
				"calculator.ui.worker.cacheHit target={} times={} mode={}",
				describeRecipe(recipe),
				times,
				routeMode
			);
			return cached;
		}
		CalculatorProcessGraphBuilder.BuildResult result = CalculatorProcessGraphBuilder.buildResult(
			recipe,
			times,
			stock,
			preferences,
			routeMode,
			forceDeepen
		);
		CalculatorBuildResultCache.put(key, result);
		return result;
	}

	/**
	 * Fingerprint of every calculator input that is not part of the explicit
	 * cache key: recipe-system catalog version, catalog content, rule
	 * selection/content, planner mode, stock sensitivity and screening toggle.
	 * Runs on worker threads — {@code calculatorCatalog} is cache-backed there.
	 */
	private static long calculatorSettingsFingerprint(RecipeEntry<?> target) {
		long fingerprint = CirrusJeiPlugin.recipeSystemCatalogVersion();
		fingerprint = 31L * fingerprint + RecipeApiImpl.attachEpoch();
		fingerprint = 31L * fingerprint + RecipePathSettings.calculatorCatalog(target).fingerprint();
		fingerprint = 31L * fingerprint + ExecutionOverlayClientConfig.calculatorRuleSelection().hashCode();
		fingerprint = 31L * fingerprint + ActiveRoutingRule.version();
		fingerprint = 31L * fingerprint + ExecutionOverlayClientConfig.calculatorPlannerMode().hashCode();
		fingerprint = 31L * fingerprint + ExecutionOverlayClientConfig.calculatorInventorySensitivity().hashCode();
		fingerprint = 31L * fingerprint + Boolean.hashCode(ExecutionOverlayClientConfig.calculatorRecipeScreeningEnabled());
		fingerprint = 31L * fingerprint + Boolean.hashCode(ExecutionOverlayClientConfig.calculatorUseLiveStock());
		return fingerprint;
	}

	private void completeFailedCalculation(@Nullable Throwable cause) {
		graph = new ProcessGraph();
		routingPlan = null;
        calculationDebugText = "failed requestId=" + calculationRequestId + (cause == null ? "" : " cause=" + describeThrowable(cause));
        calculationDebugTrace = cause == null
            ? List.of(calculationDebugText)
            : List.of("cause=" + describeThrowable(cause));
        logger.warn("calculator.ui.failed requestId={} diagnostics=\n{}", calculationRequestId, debugCalculationDiagnostics());
        if (cause != null) {
            logger.warn("calculator.ui.failed stack requestId={}", calculationRequestId, cause);
        }
		showCalculationResult("calculationFailed");
	}

    private static String describeRecipe(RecipeEntry<?> recipe) {
        return recipe.id() == null ? recipe.systemId() + "#" + recipe.hashCode() : recipe.id().toString();
    }

    private static String describeThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ":" + message);
    }

	void toggleTreeMaterialPanel() {
		if (treeView != null) {
			treeView.toggleMaterialPanel();
		}
	}

	void resetTreeViewFocus() {
		if (treeView != null) {
			treeView.resetToFullTree();
		}
	}

	void expandTreeViewAll() {
		if (treeView != null) {
			treeView.expandAllNodes();
		}
	}

	private void openProjectionDetail(RecipeEntry<?> recipe) {
		ProcessView returnView = processView;
        Widget returnContent = bodyArea.children().isEmpty() ? null : bodyArea.children().getFirst();
        if (returnContent != null) {
            setSuspendedProjectionContentVisible(returnContent, false);
        }
        suspendedProjectionReturnContent = returnContent;
		settingsPanelOpen = false;
		DatabasePage databasePage = DatabasePage.calculatorProjection(
				recipe,
				projectionContextTimes(),
				projectionContextVersion(recipe),
				() -> returnFromProjectionDetail(returnView)
		);
		if (!databasePage.openRecipe(recipe)) {
			returnFromProjectionDetail(returnView);
			return;
		}
		databasePage.useStyle(flexGrow(1), minWidth(0), minHeight(0));
        projectionDetailPage = databasePage;
		bodyArea.addWidget(databasePage);
	}

	private void returnFromProjectionDetail(ProcessView returnView) {
        DatabasePage detailPage = projectionDetailPage;
        projectionDetailPage = null;
        if (detailPage != null && detailPage.parent() == bodyArea) {
            bodyArea.remove(detailPage);
        }
        Widget returnContent = suspendedProjectionReturnContent;
        suspendedProjectionReturnContent = null;
        if (returnContent == null) {
            showCalculationResult("returnFromProjectionDetail");
        } else {
            if (returnContent.parent() != bodyArea) {
                bodyArea.addWidget(returnContent);
            }
            setSuspendedProjectionContentVisible(returnContent, true);
            calculated = true;
            settingsPanelOpen = false;
            calculatingStateVisible = false;
        }
        switchProcessView(returnView);
        refreshCalculatorTargetFromState();
	}

    private static void setSuspendedProjectionContentVisible(Widget widget, boolean visible) {
        widget.setVisible(visible)
            .setActive(visible)
            .setInteractive(visible);
        widget.useStyle(visible ? displayFlex() : displayNone());
    }

	private int projectionContextTimes() {
		if (amount >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return Math.max(1, (int) amount);
	}

	private long projectionContextVersion(RecipeEntry<?> recipe) {
		CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
		return selection != null && recipe.equals(selection.recipe()) ? selection.version() : 0L;
	}

	boolean isTreeMaterialPanelVisible() {
		return treeView != null && treeView.debugIsMaterialPanelVisible();
	}

	boolean isTreeFocusedSubgraph() {
		return treeView != null && !treeView.debugIsRootFocused();
	}

	boolean isTreeForceExpanded() {
		return treeView != null && treeView.debugIsForceExpanded();
	}

	boolean debugTreeCanvasPendingAutoFit() {
		return treeView != null && treeView.debugCanvasPendingAutoFit();
	}

	long debugTreeCraftCount() {
		return treeView == null ? 0 : treeView.debugCraftCount();
	}

	TreeCanvasWidget.LayoutDebugInfo debugTreeLayoutInfo() {
		return treeView == null ? null : treeView.debugLayoutInfo();
	}

	int debugTreeHeaderActionCount() {
		return viewSwitcher == null ? 0 : viewSwitcher.debugTreeActionCount();
	}

	boolean debugTreeHeaderActionsVisible() {
		return viewSwitcher != null && viewSwitcher.debugTreeActionsVisible();
	}

	boolean isSettingsPanelOpen() {
		return settingsPanelOpen;
	}

	boolean debugSettingsPanelOpen() {
		return settingsPanelOpen;
	}

	void debugOpenProjectionDetail(RecipeEntry<?> recipe) {
		openProjectionDetail(recipe);
	}

	void debugCloseProjectionDetail() {
		DatabasePage databasePage = projectionDetailPage;
        if (databasePage == null && !bodyArea.children().isEmpty() && bodyArea.children().getFirst() instanceof DatabasePage firstPage) {
            databasePage = firstPage;
        }
		if (databasePage != null) {
			databasePage.debugLeaveRecipeDetail();
		}
	}

	boolean debugProjectionDetailOpen() {
		if (bodyArea.children().isEmpty()) {
			return false;
		}
		Widget child = bodyArea.children().getFirst();
		if (child instanceof DatabasePage databasePage && databasePage.debugRecipeDetailOpen()) {
            return true;
        }
        return projectionDetailPage != null && projectionDetailPage.debugRecipeDetailOpen();
	}

    void debugSetCalculatorTargetFromProjectionDetail() {
        if (bodyArea.children().isEmpty()) {
            return;
        }
        DatabasePage databasePage = projectionDetailPage;
        if (databasePage == null && bodyArea.children().getFirst() instanceof DatabasePage firstPage) {
            databasePage = firstPage;
        }
        if (databasePage != null) {
            databasePage.debugActivateFirstResultCard("setCalculatorTarget");
        }
    }

	int debugGraphNodeCount() {
		return graph.nodeCount();
	}

	int debugGraphEdgeCount() {
		return graph.edgeCount();
	}

	boolean debugGraphContainsRecipe(String recipePath) {
		for (var node : graph.nodes()) {
			RecipeEntry<?> recipe = node.recipe();
			if (recipe != null && recipe.id() != null && recipePath.equals(recipe.id().getPath())) {
				return true;
			}
		}
		return false;
	}

	boolean debugCalculationPending() {
		return pendingCalculation != null;
	}

    @Nullable RecipeEntry<?> debugCurrentCalculatorTargetRecipe() {
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
        return selection == null ? null : selection.recipe();
    }

	boolean canSetCurrentPathAsExecutionTarget() {
		RoutingPlan currentPlan = routingPlan;
		CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
		if (!calculated || selection == null) {
			return false;
		}
		if (currentPlan == null) {
			return amount == requestedRoutingTimes();
		}
		if (!selection.recipe().equals(currentPlan.targetRecipe())) {
			return false;
		}
		return currentPlan.targetTimes() == requestedRoutingTimes();
	}

	int debugContentVariantPreferenceCount() {
		return contentVariantPreferences.size();
	}

	@Nullable String debugPreferredVariant(String inputKey) {
		return contentVariantPreferences.get(inputKey);
	}

    boolean debugShowingCalculatingState() {
        return calculatingStateVisible;
    }

    String debugCalculationDiagnostics() {
        if (calculationDebugTrace.isEmpty()) {
            return calculationDebugText;
        }
        return calculationDebugText + "\n" + String.join("\n", calculationDebugTrace);
    }

    boolean debugExecutionPathViewEnabled() {
        return canSetCurrentPathAsExecutionTarget();
    }

	boolean debugCurrentPathCanSetExecutionTarget() {
		return canSetCurrentPathAsExecutionTarget();
	}

	void debugCompletePendingCalculation() {
		CompletableFuture<CalculationResult> future = pendingCalculation;
		if (future == null) {
			return;
		}
		consumeCompletedCalculation(future);
	}

	private void cancelPendingCalculation() {
		CompletableFuture<CalculationResult> future = pendingCalculation;
		pendingCalculation = null;
		if (future != null) {
			future.cancel(true);
		}
	}

	private record CalculationResult(
        long requestId,
        RecipeEntry<?> recipe,
        long amount,
        CalculatorProcessGraphBuilder.RouteMode routeMode,
        ProcessGraph graph,
		@Nullable RoutingPlan routingPlan,
        CalculatorProcessGraphBuilder.CalculatorBuildDebugInfo debugInfo,
        List<String> debugTrace
    ) {
        private CalculationResult {
            debugTrace = debugTrace == null ? List.of() : List.copyOf(debugTrace);
        }
    }

	private int requestedRoutingTimes() {
		long requested = controlBar.getAmount();
		if (requested >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return Math.max(1, (int) requested);
	}

	private void refreshHoverTooltipSoon() {
		if (!lifecycle().mounted()) {
			return;
		}
		scene().requestHoverRefresh(ScreenUtil.getMouseX(), ScreenUtil.getMouseY());
	}
	//endregion
}
