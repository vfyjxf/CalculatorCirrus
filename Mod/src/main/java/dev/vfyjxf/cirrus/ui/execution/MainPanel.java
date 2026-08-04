package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.ReTranslationHint;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.CirrusUiAsync;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.page.IngredientsPage;
import dev.vfyjxf.cirrus.ui.execution.page.RecipeActiveContext;
import dev.vfyjxf.cirrus.ui.execution.page.RecipePage;
import dev.vfyjxf.cirrus.ui.execution.page.TimelinePage;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionWidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.taffy.style.TaffyDimension;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.inset;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class MainPanel extends CompositeWidget<Widget> implements ExecutionOverlayViewState {
	//region constants

	public static final int jeiBookmarkAlignedPanelWidth = 162;
	public static final int jeiBookmarkSlotWidth = 18;
	public static final int minPanelWidth = jeiBookmarkSlotWidth * 4;
	public static final int maxPanelWidth = jeiBookmarkAlignedPanelWidth;

	private static final int headerHeight = 24;
	private static final int targetTimesDebounceTicks = 8;
	//endregion

	//region state

	private final ExecutionDriverHandle driverHandle;
	private final ExecutionDockBarWidget dockBar;
	private final ExecutionWidgetGroup bodyPanel;
	private final RecipePage recipePage;
	private final TimelinePage timelinePage;
	private final IngredientsPage ingredientsPage;

	private @Nullable ExecutionDriver driver;
	private @Nullable ExecutionDriverSnapshot snapshot;
	private long appliedSelectionVersion = Long.MIN_VALUE;
	private long failedSelectionVersion = Long.MIN_VALUE;
	private long sessionBuildRequestId = 0L;
	private @Nullable PendingSessionBuild pendingSessionBuild;
	private long activeContextRequestId = 0L;
	private @Nullable ExecutionSession activeContextSession;
	private @Nullable RecipeActiveContext activeRecipeContext;
	private @Nullable PendingActiveContextBuild pendingActiveContextBuild;
	private ExecutionTab activeTab = ExecutionTab.recipe;
	private boolean traceEnabled;
	private final TargetTimesDebouncer<RecipeEntry<?>> targetTimesDebouncer =
			new TargetTimesDebouncer<>(targetTimesDebounceTicks);
	//endregion

	//region setup

	public MainPanel(ExecutionDriverHandle driverHandle) {
		this.driverHandle = driverHandle;
		this.traceEnabled = ExecutionOverlayClientConfig.overlayTraceEnabled();

		useStyle(UIStyle.of(
				flexColumn(),
				boxSizingBorderBox(),
				background(Background.Outlined.flat),
				padding(4),
				rowGap(4),
				minWidth(0),
				minHeight(0)
		));
		setTickable(true);
		setFocusable(true);

		dockBar = addWidget(new ExecutionDockBarWidget(this));
		dockBar.useStyle(sizeOf(TaffyDimension.percent(1f), TaffyDimension.length(headerHeight)), minHeight(headerHeight));

		bodyPanel = addWidget(new ExecutionWidgetGroup());
		bodyPanel.useStyle(UIStyle.of(
				background(Background.Outlined.flat),
				flexGrow(1),
				minWidth(0),
				minHeight(0)
		));

		recipePage = bodyPanel.append(new RecipePage(this));
		recipePage.useStyle(pageStyle());

		timelinePage = bodyPanel.append(new TimelinePage(this));
		timelinePage.useStyle(pageStyle());

		ingredientsPage = bodyPanel.append(new IngredientsPage(this));
		ingredientsPage.useStyle(pageStyle());

		updatePageVisibility();
	}
	//endregion

	//region sizing

	public static int preferredPanelWidth(int availableWidth) {
		if (availableWidth <= 0) {
			return maxPanelWidth;
		}
		int clampedAvailable = Math.min(availableWidth, maxPanelWidth);
		int columns = Math.max(1, clampedAvailable / jeiBookmarkSlotWidth);
		int snappedWidth = columns * jeiBookmarkSlotWidth;
		if (clampedAvailable >= maxPanelWidth) {
			return maxPanelWidth;
		}
		return Math.max(jeiBookmarkSlotWidth, Math.min(clampedAvailable, snappedWidth));
	}

	private static UIStyle pageStyle() {
		return UIStyle.of(
				positionAbsolute(),
				inset(0),
				sizeOf(TaffyDimension.percent(1f), TaffyDimension.percent(1f))
		);
	}
	//endregion

	//region lifecycle

	@Override
	public void tick() {
		super.tick();
		tickPendingTargetTimes();
		syncDriverSelection();
		if (driver != null) {
			driver.tick();
			snapshot = driver.snapshot();
			consumeReTranslationHint();
			syncActiveRecipeContext();
		} else {
			snapshot = null;
			clearActiveRecipeContext();
		}
	}
	//endregion

	//region driver

	private void updatePageVisibility() {
		setPageVisible(recipePage, activeTab == ExecutionTab.recipe);
		setPageVisible(timelinePage, activeTab == ExecutionTab.timeline);
		setPageVisible(ingredientsPage, activeTab == ExecutionTab.ingredients);
	}

	private void setPageVisible(Widget page, boolean visible) {
		page.setVisible(visible);
		page.useStyle(visible ? displayFlex() : displayNone());
	}

	private ExecutionDriver ensureDriver() {
		if (driver == null) {
			driver = driverHandle.create();
			driver.setRatePerSecond(ExecutionOverlayClientConfig.overlayRate());
		}
		return driver;
	}

	private ExecutionDriver recreateDriver() {
		double previousRate = snapshot == null ? ExecutionOverlayClientConfig.overlayRate() : snapshot.ratePerSecond();
		driver = driverHandle.create();
		driver.setRatePerSecond(previousRate);
		snapshot = driver.snapshot();
		return driver;
	}

	private void syncDriverSelection() {
		pollPendingSessionBuild();
		ExecutionDriver currentDriver = ensureDriver();
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		if (selection == null) {
			cancelPendingSessionBuild();
			if (hasIncompleteSession(currentDriver)) {
				return;
			}
			if (appliedSelectionVersion != Long.MIN_VALUE) {
				appliedSelectionVersion = Long.MIN_VALUE;
				failedSelectionVersion = Long.MIN_VALUE;
				recreateDriver();
			}
			return;
		}
		if (pendingSessionBuild != null) {
			return;
		}
		if (targetTimesDebouncer.pending()) {
			return;
		}
		if (selection.version() == appliedSelectionVersion) {
			return;
		}
		if (selection.version() == failedSelectionVersion) {
			return;
		}
		startSessionBuild(selection, currentDriver, null);
	}

	private void consumeReTranslationHint() {
		ExecutionDriver currentDriver = driver;
		if (currentDriver == null || snapshot == null) {
			return;
		}
		ReTranslationHint hint = snapshot.reTranslationHint();
		if (hint == null) {
			return;
		}
		if (pendingSessionBuild != null) {
			return;
		}
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		if (selection == null) {
			return;
		}
        var previousSession = snapshot.session();
		ExecutionDriver recreatedDriver = recreateDriver();
		startSessionBuild(selection, recreatedDriver, previousSession);
	}

	private void startSessionBuild(
		ExecutionTargetRecipeState.Selection selection,
		ExecutionDriver targetDriver,
		@Nullable ExecutionSession previousSession
	) {
		targetDriver.stopAutoAdvance();
		ExecutionInteraction interaction = targetDriver.interaction();
		ExecutionPlanningSnapshot planningSnapshot = interaction.planningSnapshot();
		ExecutionInteraction frozenInteraction = ExecutionTargetRecipeState.withPlanningSnapshot(interaction, planningSnapshot);
		long requestId = ++sessionBuildRequestId;
		long selectionVersion = selection.version();
		failedSelectionVersion = Long.MIN_VALUE;
		pendingSessionBuild = new PendingSessionBuild(
			requestId,
			selectionVersion,
			targetDriver,
			CirrusUiAsync.recipeTask(() -> previousSession == null
				? ExecutionTargetRecipeState.buildSession(selection, frozenInteraction)
				: ExecutionTargetRecipeState.rebuildSession(selection, frozenInteraction, previousSession)
			)
		);
	}

	private void pollPendingSessionBuild() {
		PendingSessionBuild pending = pendingSessionBuild;
		if (pending == null || !pending.future().isDone()) {
			return;
		}
		pendingSessionBuild = null;
		ExecutionTargetRecipeState.Selection currentSelection = ExecutionTargetRecipeState.current();
		if (currentSelection == null || currentSelection.version() != pending.selectionVersion()) {
			return;
		}
		try {
			ExecutionSession session = pending.future().join();
			if (pending.driver() == driver && pending.requestId() == sessionBuildRequestId) {
				pending.driver().stopAutoAdvance();
				pending.driver().initialize(session);
				appliedSelectionVersion = pending.selectionVersion();
				snapshot = pending.driver().snapshot();
				invalidateActiveRecipeContext();
			}
		} catch (CompletionException exception) {
			failedSelectionVersion = pending.selectionVersion();
		}
	}

	private void cancelPendingSessionBuild() {
		if (pendingSessionBuild != null) {
			pendingSessionBuild = null;
			sessionBuildRequestId++;
		}
	}

	private boolean hasIncompleteSession(ExecutionDriver currentDriver) {
		try {
			ExecutionSession currentSession = currentDriver.session();
			return currentSession != null && !currentSession.completed();
		} catch (IllegalStateException ignored) {
			ExecutionSession currentSession = snapshot == null ? null : snapshot.session();
			return currentSession != null && !currentSession.completed();
		}
	}

	private void syncActiveRecipeContext() {
		pollPendingActiveRecipeContext();
		ExecutionSession session = snapshot == null ? null : snapshot.session();
		if (session == null) {
			if (calculating()) {
				invalidateActiveRecipeContext();
				return;
			}
			clearActiveRecipeContext();
			return;
		}
		if (calculating()) {
			invalidateActiveRecipeContext();
			return;
		}
		if (pendingActiveContextBuild != null) {
			if (pendingActiveContextBuild.session() == session) {
				return;
			}
			pendingActiveContextBuild = null;
			activeContextRequestId++;
		}
		if (session == activeContextSession) {
			return;
		}
		activeContextSession = session;
		long requestId = ++activeContextRequestId;
		pendingActiveContextBuild = new PendingActiveContextBuild(
			requestId,
			session,
			CirrusUiAsync.recipeTask(() -> RecipeActiveContext.fromSession(session))
		);
	}

	private void pollPendingActiveRecipeContext() {
		PendingActiveContextBuild pending = pendingActiveContextBuild;
		if (pending == null || !pending.future().isDone()) {
			return;
		}
		pendingActiveContextBuild = null;
		try {
			RecipeActiveContext context = pending.future().join();
			ExecutionSession session = snapshot == null ? null : snapshot.session();
			if (session == pending.session()
				&& activeContextSession == pending.session()
				&& pending.requestId() == activeContextRequestId) {
				activeRecipeContext = context;
			}
		} catch (CompletionException exception) {
			activeContextSession = null;
			activeRecipeContext = null;
		}
	}

	private void clearActiveRecipeContext() {
		activeContextRequestId++;
		activeContextSession = null;
		activeRecipeContext = null;
		pendingActiveContextBuild = null;
	}

	private void invalidateActiveRecipeContext() {
		activeContextRequestId++;
		activeContextSession = null;
		pendingActiveContextBuild = null;
	}

	private void tickPendingTargetTimes() {
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		if (selection == null) {
			targetTimesDebouncer.clear();
			return;
		}
		TargetTimesDebouncer.Commit<RecipeEntry<?>> commit =
				targetTimesDebouncer.tick(selection.recipe(), selection.times());
		if (commit != null) {
			stopCurrentExecutionForRecalculation();
			ExecutionTargetRecipeState.set(commit.target(), commit.value());
		}
	}

	private boolean flushPendingTargetTimes() {
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		if (selection == null) {
			targetTimesDebouncer.clear();
			return false;
		}
		TargetTimesDebouncer.Commit<RecipeEntry<?>> commit =
				targetTimesDebouncer.flush(selection.recipe(), selection.times());
		if (commit == null) {
			return false;
		}
		stopCurrentExecutionForRecalculation();
		ExecutionTargetRecipeState.set(commit.target(), commit.value());
		return true;
	}

	private void stopCurrentExecutionForRecalculation() {
		cancelPendingSessionBuild();
		if (driver != null) {
			driver.stopAutoAdvance();
			snapshot = driver.snapshot();
		}
	}
	//endregion

	//region view state

	@Override
	public @Nullable ExecutionDriver driver() {
		return driver;
	}

	@Override
	public @Nullable ExecutionDriverSnapshot snapshot() {
		return snapshot;
	}

	@Override
	public @Nullable ExecutionTelemetrySnapshot telemetry() {
		return snapshot == null ? null : snapshot.telemetry();
	}

	@Override
	public @Nullable ExecutionOverlayTraceSnapshot trace() {
		return snapshot == null ? null : snapshot.trace();
	}

	@Override
	public @Nullable OverlayTargetProgress targetProgress() {
		return snapshot == null ? null : snapshot.targetProgress();
	}

	@Override
	public boolean calculating() {
		return pendingSessionBuild != null;
	}

	@Override
	public @Nullable RecipeActiveContext activeRecipeContext() {
		return activeRecipeContext;
	}

	@Override
	public @Nullable String targetLabel() {
		if (activeRecipeContext != null && activeRecipeContext.label() != null && !activeRecipeContext.label().isBlank()) {
			return activeRecipeContext.label();
		}
		RecipeContent targetContent = targetContent();
		if (targetContent.notEmpty()) {
			return ExecutionOverlayContentHelper.label(targetContent);
		}
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		if (selection != null && selection.recipe().id() != null) {
			return selection.recipe().id().toString();
		}
		return null;
	}

	@Override
	public int targetTimes() {
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		return selection == null ? 1 : targetTimesDebouncer.visibleValue(selection.recipe(), selection.times());
	}

	@Override
	public void setTargetTimes(int times) {
		ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
		if (selection == null) {
			return;
		}
		int normalized = Math.max(1, times);
		if (selection.times() == normalized) {
			targetTimesDebouncer.clear();
			return;
		}
		stopCurrentExecutionForRecalculation();
		targetTimesDebouncer.request(selection.recipe(), normalized, selection.times());
	}

	@Override
	public void flushTargetTimes() {
		if (flushPendingTargetTimes()) {
			syncDriverSelection();
		}
	}

	@Override
	public RecipeContent targetContent() {
		if (activeRecipeContext != null && activeRecipeContext.content().notEmpty()) {
			return activeRecipeContext.content();
		}
		OverlayTargetProgress progress = targetProgress();
		if (progress != null && progress.content().notEmpty()) {
			return progress.content();
		}
		return RecipeContent.empty();
	}

	@Override
	public boolean traceEnabled() {
		return traceEnabled;
	}

	@Override
	public void setTraceEnabled(boolean enabled) {
		traceEnabled = enabled;
		ExecutionOverlayClientConfig.setOverlayTraceEnabled(enabled);
	}
	//endregion

	//region dock
	ExecutionTab activeTab() {
		return activeTab;
	}

	void activateTab(ExecutionTab tab) {
		if (tab == activeTab) {
			return;
		}
		activeTab = tab;
		updatePageVisibility();
	}

	boolean debugSessionBuildPending() {
		return pendingSessionBuild != null;
	}

	boolean debugActiveContextBuildPending() {
		return pendingActiveContextBuild != null;
	}

	@Nullable RecipeActiveContext debugActiveRecipeContext() {
		return activeRecipeContext;
	}

	void debugCompletePendingActiveContextBuild() {
		PendingActiveContextBuild pending = pendingActiveContextBuild;
		if (pending == null) {
			return;
		}
		pendingActiveContextBuild = null;
		RecipeActiveContext context = pending.future().join();
		ExecutionSession session = snapshot == null ? null : snapshot.session();
		if (session == pending.session()
			&& activeContextSession == pending.session()
			&& pending.requestId() == activeContextRequestId) {
			activeRecipeContext = context;
		}
	}

	void debugCompletePendingSessionBuild() {
		PendingSessionBuild pending = pendingSessionBuild;
		if (pending == null) {
			return;
		}
		pendingSessionBuild = null;
		ExecutionSession session = pending.future().join();
		if (pending.driver() == driver && pending.requestId() == sessionBuildRequestId) {
			pending.driver().stopAutoAdvance();
			pending.driver().initialize(session);
			appliedSelectionVersion = pending.selectionVersion();
			snapshot = pending.driver().snapshot();
		}
	}

	private record PendingSessionBuild(
		long requestId,
		long selectionVersion,
		ExecutionDriver driver,
		CompletableFuture<ExecutionSession> future
	) {
	}

	private record PendingActiveContextBuild(
		long requestId,
		ExecutionSession session,
		CompletableFuture<RecipeActiveContext> future
	) {
	}
	//endregion
}
