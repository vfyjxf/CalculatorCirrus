package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock.ConsumedResource;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side execution target shared between JEI selection and the overlay.
 */
public final class ExecutionTargetRecipeState {

	public record Selection(RecipeEntry<?> recipe, int times, long version, @Nullable RoutingPlan routingPlan) {
		public Selection {
			if (times <= 0) {
				throw new IllegalArgumentException("times must be positive");
			}
		}

		public Selection(RecipeEntry<?> recipe, int times, long version) {
			this(recipe, times, version, null);
		}
	}

	private static final AtomicLong versionCounter = new AtomicLong(0L);

	private static volatile @Nullable Selection selection;
	private static volatile long cachedPlanVersion = -1L;
	private static volatile long cachedPlanStateFingerprint = -1L;
	private static volatile @Nullable ExecutionInteraction cachedPlanInteraction = null;
	private static volatile @Nullable InteractionExecutionPlan cachedPlan = null;
	private static volatile @Nullable PauseReason cachedPlanPauseReason = null;
	private static volatile long cachedSessionVersion = -1L;
	private static volatile long cachedSessionStateFingerprint = -1L;
	private static volatile @Nullable ExecutionInteraction cachedSessionInteraction = null;
	private static volatile @Nullable ExecutionSession cachedSession = null;

	private ExecutionTargetRecipeState() {
	}

	public static Selection set(RecipeEntry<?> recipe, int times) {
		Selection next = new Selection(recipe, Math.max(1, times), versionCounter.incrementAndGet());
		selection = next;
		clearCaches();
		return next;
	}

	public static Selection setPath(RecipeEntry<?> recipe, int times, RoutingPlan routingPlan) {
		Objects.requireNonNull(routingPlan, "routingPlan");
		if (!recipe.equals(routingPlan.targetRecipe())) {
			throw new IllegalArgumentException("routing plan target does not match selected recipe");
		}
		int normalizedTimes = Math.max(1, times);
		if (routingPlan.targetTimes() != normalizedTimes) {
			throw new IllegalArgumentException("routing plan times does not match selected times");
		}
		Selection next = new Selection(recipe, normalizedTimes, versionCounter.incrementAndGet(), routingPlan);
		selection = next;
		clearCaches();
		return next;
	}

    public static void clear() {
        selection = null;
        versionCounter.incrementAndGet();
        clearCaches();
    }

    public static @Nullable Selection current() {
        return selection;
    }

    public static Component displayName(@Nullable RecipeEntry<?> recipe) {
        RecipeContent preview = previewContent(recipe);
        if (preview.notEmpty()) {
            return RecipeContentRenderer.getRenderer(preview).getName(preview);
        }
        if (recipe == null) {
            return Component.literal("Unknown recipe");
		}
		ResourceLocation id = recipe.id();
		if (id != null) {
			return Component.literal(id.toString());
        }
        return Component.literal(recipe.toString());
    }

    public static RecipeContent previewContent(@Nullable RecipeEntry<?> recipe) {
        ProjectedRecipe projected = projectedRecipe(recipe);
        if (projected == null) {
            return RecipeContent.empty();
        }
        for (OutputContent output : projected.outputs()) {
            if (output != null && output.notEmpty()) {
                return output.copyWithAmount(Math.max(1L, output.amount()));
            }
        }
        return RecipeContent.empty();
    }

	public static @Nullable ProjectedRecipe projectedRecipe(@Nullable RecipeEntry<?> recipe) {
		if (recipe == null) {
			return null;
		}
		RecipeProjection projection = recipe.resolve();
		if (projection == null) {
			return null;
		}
		return new ProjectionEngine().project(ProjectionContexts.global(), projection);
	}

    public static ItemStack previewStack(@Nullable RecipeEntry<?> recipe) {
        RecipeContent preview = previewContent(recipe);
        if (!(preview instanceof StackContent<?, ?> stackContent)) {
            return ItemStack.EMPTY;
        }
        var contentStack = stackContent.content();
        if (contentStack.isEmpty() || !contentStack.isItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack previewStack = contentStack.<Item, ItemStack>cast().value().copy();
        previewStack.setCount(1);
        return previewStack;
    }

	public static ExecutionSession rebuildSession(Selection selected, ExecutionInteraction interaction) {
		clearCaches();
		return buildSession(selected, interaction);
	}

    public static ExecutionInteraction withPlanningSnapshot(
        ExecutionInteraction interaction,
        ExecutionPlanningSnapshot planningSnapshot
    ) {
        return interactionWithSnapshot(interaction, planningSnapshot);
    }

    public static ExecutionSession rebuildSession(
        Selection selected,
        ExecutionInteraction interaction,
        @Nullable ExecutionSession currentSession
    ) {
        clearCaches();
        if (currentSession == null || currentSession.completed()) {
            return buildSession(selected, interaction);
        }
        return rebuildSuffixSession(selected, interaction, currentSession);
    }

	public static ExecutionSession buildSession(Selection selected, ExecutionInteraction interaction) {
		long stateFingerprint = planStateFingerprint(selected, interaction);
		if (selected.version() == cachedSessionVersion
				&& cachedSessionStateFingerprint == stateFingerprint
				&& Objects.equals(interaction, cachedSessionInteraction)
				&& cachedSession != null
		) {
			return cachedSession;
		}

		InteractionExecutionPlan plan = buildPlan(selected, interaction);
		PauseReason pauseReason = cachedPlanPauseReason;
		ExecutionFlowNode root = buildFlowRoot(plan, pauseReason == null ? null : pauseReason.guidance());
        ExecutionPlanningSnapshot baselinePlanningSnapshot = interaction.planningSnapshot();
		ExecutionSession session = pauseReason == null
            ? ExecutionSession.start(
                plan,
                root,
                baselinePlanningSnapshot,
                ExecutionOverlayClientConfig.observedStockPolicy()
            )
            : pausedSession(
                plan,
                root,
                pauseReason,
                baselinePlanningSnapshot,
                ExecutionOverlayClientConfig.observedStockPolicy()
            );
		cachedSessionVersion = selected.version();
		cachedSessionStateFingerprint = stateFingerprint;
		cachedSessionInteraction = interaction;
		cachedSession = session;
		return session;
	}

	public static InteractionExecutionPlan buildPlan(Selection selected, ExecutionInteraction interaction) {
		long stateFingerprint = planStateFingerprint(selected, interaction);
		if (selected.version() == cachedPlanVersion
				&& cachedPlanStateFingerprint == stateFingerprint
				&& Objects.equals(interaction, cachedPlanInteraction)
				&& cachedPlan != null
		) {
			return cachedPlan;
		}

		RoutingPlanBuild routingPlan = buildRoutingPlan(selected, interaction);
		InteractionExecutionPlan plan = routingPlan.plan();

		cachedPlanVersion = selected.version();
		cachedPlanStateFingerprint = stateFingerprint;
		cachedPlanInteraction = interaction;
		cachedPlan = plan;
		cachedPlanPauseReason = routingPlan.pauseReason();
		return plan;
	}

	private static RoutingPlanBuild buildRoutingPlan(Selection selected, ExecutionInteraction interaction) {
		RoutingPlan precomputedPlan = selected.routingPlan();
		if (precomputedPlan != null) {
			return new RoutingPlanBuild(new ExecutionTranslator(interaction.interactionApi()).translate(precomputedPlan), null);
		}
		RecipePathSettings.RoutingContext routingContext = RecipePathSettings.executionRoutingContext(selected.recipe());
		RoutingService routingService = routingContext.service();
		RuleDefinition activeRule = routingContext.rule();
		routingService.registerRuleDefinition(activeRule);
		ExecutionPlanningSnapshot planningSnapshot = interaction.planningSnapshot();
		RoutingOutcome outcome = routingService.route(
				RoutingRequest.builder()
							  .plannerMode(routingContext.plannerMode())
							  .targetRecipe(selected.recipe(), selected.times())
							  .ruleProfileId(activeRule.profile().profileId())
							  .sourcePolicyId(activeRule.sourcePolicy().policyId())
							  .stock(planningSnapshot.stock())
							  .inventorySensitivityProfile(routingContext.inventorySensitivity())
							  .loopPolicy(routingContext.loopPolicy())
							  .build()
		);
		if (outcome.plan() == null) {
			return new RoutingPlanBuild(emptyPlan(selected), routingFailurePauseReason(selected, outcome));
		}
		return new RoutingPlanBuild(new ExecutionTranslator(interaction.interactionApi()).translate(outcome.plan()), null);
	}

	private static ExecutionFlowNode buildFlowRoot(InteractionExecutionPlan plan, @Nullable ExecutionGuidance guidance) {
		MutableList<InteractionStep> rootSteps = Lists.mutable.empty();
		MutableList<ExecutionFlowNode> children = Lists.mutable.empty();
		ImmutableList<InteractionStep> steps = plan.steps();

		int recipeSequence = 0;
		int index = 0;
		while (index < steps.size()) {
			InteractionStep step = steps.get(index);
			if (!(step instanceof InteractionStep.OpenInteractionStep open)) {
				rootSteps.add(step);
				index++;
				continue;
			}

			RecipeEntry<?> recipe = open.recipe();
			MutableList<InteractionStep> recipeSteps = Lists.mutable.of(step);
			index++;
			boolean collectedOutputs = false;
			while (index < steps.size()) {
				InteractionStep current = steps.get(index);
				if (current instanceof InteractionStep.OpenInteractionStep) {
					break;
				}
				if (current instanceof InteractionStep.ManualCheckpointStep) {
					recipeSteps.add(current);
					index++;
					continue;
				}
				if (current instanceof InteractionStep.PrepareInputsStep prepare && recipe.equals(prepare.recipe())) {
					recipeSteps.add(current);
					index++;
					continue;
				}
				if (current instanceof InteractionStep.ExecuteRecipeStep execute && recipe.equals(execute.recipe())) {
					recipeSteps.add(current);
					index++;
					continue;
				}
				if (current instanceof InteractionStep.CollectOutputsStep collect && recipe.equals(collect.recipe())) {
					recipeSteps.add(current);
					collectedOutputs = true;
					index++;
					continue;
				}
				if (collectedOutputs && current instanceof InteractionStep.StoreOutputsStep) {
					recipeSteps.add(current);
					index++;
					continue;
				}
				break;
			}

			children.add(ExecutionFlowNode.pending(
					recipeNodeId(recipe, recipeSequence),
					displayName(recipe),
					recipeSteps.toImmutable(),
					null,
					Lists.immutable.empty()
			));
			recipeSequence++;
		}

		ImmutableList<InteractionStep> visibleRootSteps = children.isEmpty() ? plan.steps() : rootSteps.toImmutable();
		return ExecutionFlowNode.pending(
				nodeId(plan.targetRecipe()),
				displayName(plan.targetRecipe()),
				visibleRootSteps,
				guidance,
				children.toImmutable()
		);
	}

	private static ExecutionSession pausedSession(
		InteractionExecutionPlan plan,
		ExecutionFlowNode root,
		PauseReason pauseReason,
        ExecutionPlanningSnapshot baselinePlanningSnapshot,
        ExecutionObservedStockPolicy observedStockPolicy
	) {
		return new ExecutionSession(
			plan,
			root,
			0,
			Sets.immutable.empty(),
			Sets.immutable.empty(),
			null,
			pauseReason,
			null,
			ExecutionFlowViewModel.derive(
				root,
				new ExecutionFlowViewModel.RuntimeState(
					Sets.immutable.empty(),
					Sets.immutable.empty(),
					null,
			null
				)
			),
            baselinePlanningSnapshot,
            observedStockPolicy
		);
	}

    private static ExecutionSession rebuildSuffixSession(
        Selection selected,
        ExecutionInteraction interaction,
        ExecutionSession currentSession
    ) {
        ExecutionPlanningSnapshot observedSnapshot = interaction.planningSnapshot();
        ExecutionPlanningSnapshot effectiveSnapshot = effectivePlanningSnapshot(currentSession, observedSnapshot);
        int remainingTimes = remainingTargetTimes(selected, currentSession, effectiveSnapshot);
        Selection remainingSelection = new Selection(
            selected.recipe(),
            Math.max(1, remainingTimes),
            retranslationSelectionVersion(selected.version(), remainingTimes)
        );
        InteractionExecutionPlan rebuiltPlan = remainingTimes <= 0
            ? new InteractionExecutionPlan(selected.recipe(), 1, Lists.immutable.empty())
            : buildPlan(remainingSelection, interactionWithSnapshot(interaction, effectiveSnapshot));
        ExecutionFlowNode root = buildFlowRoot(rebuiltPlan, null);
        return currentSession.paused()
            ? pausedSession(
                rebuiltPlan,
                root,
                currentSession.pauseReason(),
                currentSession.baselinePlanningSnapshot(),
                currentSession.observedStockPolicy()
            )
            : ExecutionSession.start(
                rebuiltPlan,
                root,
                currentSession.baselinePlanningSnapshot(),
                currentSession.observedStockPolicy()
            );
    }

    private static int remainingTargetTimes(
        Selection selected,
        ExecutionSession currentSession,
        ExecutionPlanningSnapshot effectiveSnapshot
    ) {
        RecipeContent preview = previewContent(selected.recipe());
        if (!(preview instanceof OutputContent output)) {
            return selected.times();
        }
        ContentResource targetResource = output.asResource();
        long unitAmount = Math.max(1L, output.amount());
        long completedAmount = completedCollectedTargetAmount(currentSession, targetResource);
        long observedDelta = Math.max(
            0L,
            effectiveSnapshot.stock().countMatchingResource(targetResource)
                - currentSession.baselinePlanningSnapshot().stock().countMatchingResource(targetResource)
        );
        long progressedAmount = Math.max(completedAmount, observedDelta);
        long progressedTimes = progressedAmount <= 0L ? 0L : progressedAmount / unitAmount;
        long remaining = Math.max(0L, (long) selected.times() - progressedTimes);
        return (int) remaining;
    }

    private static long completedCollectedTargetAmount(ExecutionSession session, ContentResource targetResource) {
        long amount = 0L;
        for (InteractionStep step : session.completedSteps()) {
            if (!(step instanceof InteractionStep.CollectOutputsStep collect)) {
                continue;
            }
            if (!session.plan().targetRecipe().equals(collect.recipe())) {
                continue;
            }
            for (var output : collect.expectedOutputs()) {
                if (output.resource().matches(targetResource, MatchStrategy.identityAndComponent)) {
                    amount += output.amount();
                }
            }
        }
        return amount;
    }

    private static long retranslationSelectionVersion(long selectionVersion, int remainingTimes) {
        return (selectionVersion * 1315423911L) ^ Integer.toUnsignedLong(remainingTimes) ^ Long.MIN_VALUE;
    }

    private static ExecutionPlanningSnapshot effectivePlanningSnapshot(
        ExecutionSession session,
        ExecutionPlanningSnapshot observedSnapshot
    ) {
        if (session.observedStockPolicy() == ExecutionObservedStockPolicy.consumeObservedStock) {
            return observedSnapshot;
        }
        Stock allowed = plannedVisibleStock(session);
        Stock filtered = intersectStock(observedSnapshot.stock(), allowed);
        long fingerprint = 31L * observedSnapshot.fingerprint() + filtered.snapshotResources().hashCode();
        return new ExecutionPlanningSnapshot(filtered, fingerprint);
    }

    private static Stock plannedVisibleStock(ExecutionSession session) {
        Stock stock = session.baselinePlanningSnapshot().stock().copy();
        for (InteractionStep step : session.completedSteps()) {
            switch (step) {
                case InteractionStep.CollectOutputsStep collect -> {
                    for (var output : collect.expectedOutputs()) {
                        stock.addResource(resourceWithAmount(output.resource(), output.amount()));
                    }
                }
                default -> {
                }
            }
        }
        return stock;
    }

    private static ContentResource resourceWithAmount(ContentResource resource, long amount) {
        if (resource instanceof dev.vfyjxf.cirrus.api.content.value.ContentStack<?, ?> stack) {
            return stack.copyWithAmount(amount);
        }
        if (resource instanceof dev.vfyjxf.cirrus.api.content.value.ContentValue<?> value) {
            return valueWithAmount(value, amount);
        }
        return resource.normalize();
    }

    private static <T> ContentResource valueWithAmount(dev.vfyjxf.cirrus.api.content.value.ContentValue<T> value, long amount) {
        return dev.vfyjxf.cirrus.api.content.value.ContentValue.fromValue(value.type(), value.type().fromValue(amount));
    }

    private static Stock intersectStock(Stock observed, Stock allowed) {
        Stock out = Stock.empty();
        for (ConsumedResource observedResource : observed.snapshotResources()) {
            long permitted = allowed.countMatchingResource(observedResource.resource());
            if (permitted <= 0L) {
                continue;
            }
            long accepted = Math.min(permitted, observedResource.amount());
            if (accepted <= 0L) {
                continue;
            }
            out.addResource(resourceWithAmount(observedResource.resource(), accepted));
        }
        return out;
    }

    private static ExecutionInteraction interactionWithSnapshot(
        ExecutionInteraction interaction,
        ExecutionPlanningSnapshot planningSnapshot
    ) {
        return new ExecutionInteraction(
            interaction.interactionId(),
            new SnapshotPlanningInteractionApi(interaction.interactionApi(), planningSnapshot)
        );
    }

	private static InteractionExecutionPlan emptyPlan(Selection selected) {
		return new InteractionExecutionPlan(selected.recipe(), selected.times(), Lists.immutable.empty());
	}

	private static PauseReason routingFailurePauseReason(Selection selected, RoutingOutcome outcome) {
		return new PauseReason(PauseReasons.routingPlanFailed, routingFailureGuidance(selected, outcome));
	}

	private static ExecutionGuidance routingFailureGuidance(Selection selected, RoutingOutcome outcome) {
		MutableList<Component> issues = routingIssueTexts(outcome);
		if (issues.isEmpty()) {
			return new ExecutionGuidance(
				Component.translatable("cirrus.ui.execution.routing_failed.summary.generic"),
				Component.translatable("cirrus.ui.execution.routing_failed.detail.generic", displayName(selected.recipe())),
				Lists.immutable.empty(),
				Sets.immutable.empty()
			);
		}

		Component summary = issues.getFirst();
		MutableList<Component> rest = Lists.mutable.ofAll(issues);
		rest.remove(0);
		return new ExecutionGuidance(
			summary,
			Component.translatable("cirrus.ui.execution.routing_failed.detail.generic", displayName(selected.recipe())),
			rest.toImmutable(),
			Sets.immutable.empty()
		);
	}

	private static MutableList<Component> routingIssueTexts(RoutingOutcome outcome) {
		MutableList<Component> texts = Lists.mutable.empty();
		LinkedHashSet<String> seen = new LinkedHashSet<>();
		for (RoutingIssue issue : outcome.issues()) {
			Component text = routingIssueText(issue);
			if (seen.add(text.getString())) {
				texts.add(text);
			}
		}
		return texts;
	}

	private static Component routingIssueText(RoutingIssue issue) {
		Object[] args = issue.translationArgs();
		if (args.length == 0) {
			return Component.translatable(issue.messageKey());
		}
		return Component.translatable(issue.messageKey(), args);
	}

	private static long planStateFingerprint(@Nullable Selection selected, ExecutionInteraction interaction) {
		if (selected == null) {
			return 0L;
		}
		return 31L * interaction.planningSnapshot().fingerprint() + RecipePathSettings.executionSettingsFingerprint();
	}

    private static void clearCaches() {
		cachedPlanVersion = -1L;
		cachedPlanStateFingerprint = -1L;
		cachedPlanInteraction = null;
		cachedPlan = null;
		cachedPlanPauseReason = null;
		cachedSessionVersion = -1L;
		cachedSessionStateFingerprint = -1L;
		cachedSessionInteraction = null;
		cachedSession = null;
	}

	private static String nodeId(RecipeEntry<?> recipe) {
		ResourceLocation id = recipe.id();
		if (id == null) {
			return "target/" + Integer.toHexString(recipe.hashCode());
		}
		return "target/" + id.toString().replace(':', '/');
	}

	private static String recipeNodeId(RecipeEntry<?> recipe, int sequence) {
		return nodeId(recipe) + "/expanded/" + sequence;
	}

	private record RoutingPlanBuild(
		InteractionExecutionPlan plan,
		@Nullable PauseReason pauseReason
	) {
	}

    private record SnapshotPlanningInteractionApi(
        dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi delegate,
        ExecutionPlanningSnapshot planningSnapshot
    ) implements dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPlanningApi {

        @Override
        public dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return delegate.probe(recipe);
        }

        @Override
        public dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return delegate.guide(recipe);
        }

        @Override
        public dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult perform(
            dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext context
        ) {
            return delegate.perform(context);
        }

        @Override
        public int maxExecuteBatchSize(dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext context) {
            return delegate.maxExecuteBatchSize(context);
        }

        @Override
        public ExecutionPlanningSnapshot planningSnapshot() {
            return planningSnapshot;
        }
    }
}
