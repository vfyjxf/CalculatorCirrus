package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cirrus.planning.adapter.ClassifierVerdictBridge;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.normalize.JeiNormalizationPatch;
import dev.vfyjxf.cirrus.planning.normalize.JeiRecipeNormalizer;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.classify.RecipeClassificationService;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.RichIterable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

public final class CirrusCatalogAdapter {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus PlannerBridge");
    private static final int jeiCircuitBreakerThreshold = 8;
    private static final Namespace recyclingRole = CirrusNamespaces.ofInternal("screening/role/recycling");

    public record RecipeContentSource(
        List<InputContent> inputs,
        List<OutputContent> outputs,
        Map<Integer, Integer> durabilityCosts
    ) {
        public RecipeContentSource(List<InputContent> inputs, List<OutputContent> outputs) {
            this(inputs, outputs, Map.of());
        }

        public RecipeContentSource {
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
            durabilityCosts = durabilityCosts == null || durabilityCosts.isEmpty()
                    ? Map.of()
                    : Map.copyOf(durabilityCosts);
        }
    }

    public record SystemSnapshot(RecipeSystem<?> system, List<MachineEntry> machines, Set<Namespace> systemTags) {
        public SystemSnapshot(RecipeSystem<?> system, List<MachineEntry> machines) {
            this(system, machines, Set.of());
        }

        public SystemSnapshot {
            Objects.requireNonNull(system, "system");
            machines = List.copyOf(Objects.requireNonNull(machines, "machines"));
            if (systemTags == null || systemTags.isEmpty()) {
                systemTags = Set.of();
            } else {
                TreeSet<Namespace> sorted = new TreeSet<>(Comparator.comparing(Namespace::toString));
                sorted.addAll(systemTags);
                systemTags = Collections.unmodifiableSet(sorted);
            }
        }
    }

    public record SystemCapturePlan(long attachEpoch, List<SystemSnapshot> systems) {}

    public record CaptureStats(
        int systemsVisited,
        int systemsFailed,
        int recipesVisited,
        int recipesKept,
        int recipesResolveFailed,
        int recipesExceptionFailed,
        int unresolvedOutputsRecorded,
        Map<DropReason, Integer> droppedByReason
    ) {}

    public record CapturedCatalog(
        long attachEpoch,
        RawRecipeCatalogSnapshot snapshot,
        SystemAdmissionPolicy policy,
        List<RecipeEntry<?>> entriesBySourceRef,
        CaptureStats stats
    ) {}

    public CirrusCatalogAdapter() {}

    public SystemCapturePlan prepareCapturePlan() {
        long epoch = RecipeApiImpl.attachEpoch();
        RecipeApi api = RecipeApi.instance();
        List<SystemSnapshot> snapshots = new ArrayList<>();
        for (RecipeSystem<?> system : api.allSystems()) {
            List<MachineEntry> machines = extractSystemMachines(api, system);
            Set<Namespace> tags = extractSystemTags(api, system);
            snapshots.add(new SystemSnapshot(system, machines, tags));
        }
        return new SystemCapturePlan(epoch, List.copyOf(snapshots));
    }

    public Optional<CapturedCatalog> capture(SystemCapturePlan plan, long catalogVersion, BooleanSupplier cancelled) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cancelled, "cancelled");
        if (cancelled.getAsBoolean() || RecipeApiImpl.attachEpoch() != plan.attachEpoch()) {
            return Optional.empty();
        }

        List<RawRecipeRecord> records = new ArrayList<>(4096);
        List<RecipeEntry<?>> entriesByRef = new ArrayList<>(4096);
        List<RecipeClassifyView> classifyViews = new ArrayList<>(4096);
        Map<Long, RecipeUsePolicy> systemHintPolicies = new HashMap<>();
        Set<ComponentStackKey> unresolvedOutputs = new TreeSet<>();
        EnumMap<DropReason, Integer> drops = new EnumMap<>(DropReason.class);
        DefaultSystemAdmissionPolicy defaultPolicy = DefaultSystemAdmissionPolicy.defaults();

        int systemsVisited = 0;
        int systemsFailed = 0;
        int recipesVisited = 0;
        int recipesResolveFailed = 0;
        int recipesExceptionFailed = 0;

        for (SystemSnapshot sysSnap : plan.systems()) {
            if (cancelled.getAsBoolean() || RecipeApiImpl.attachEpoch() != plan.attachEpoch()) {
                return Optional.empty();
            }
            systemsVisited++;
            RecipeSystem<?> rawSystem = sysSnap.system();
            Namespace systemId = rawSystem.systemId();
            String sourceKind = sourceKindOf(rawSystem);
            if (defaultPolicy.classify(systemId, sourceKind) == SystemAdmission.excludedDisplay
                    || sysSnap.systemTags().contains(RecipeSystemTags.displayOnly)
                    || sysSnap.systemTags().contains(RecipeSystemTags.mirrorDuplicate)) {
                continue;
            }

            RichIterable<?> allRecipes;
            try {
                allRecipes = rawSystem.allRecipes();
            } catch (RuntimeException | LinkageError e) {
                systemsFailed++;
                logger.warn("Failed enumerating recipes for system {}: {}", rawSystem.systemId(), e.toString());
                continue;
            }
            if (allRecipes == null) {
                continue;
            }

            boolean isJeiSystem = rawSystem instanceof JeiCategoryRecipeSystem<?>;
            boolean isVanillaSystem = rawSystem instanceof VanillaRecipeSystem<?>;
            int sysSuccesses = 0;
            int consecutiveExceptions = 0;

            for (Object rawRecipe : allRecipes) {
                if (cancelled.getAsBoolean()) {
                    return Optional.empty();
                }
                if (rawRecipe == null) {
                    continue;
                }
                recipesVisited++;
                try {
                    RecipeEntry<?> entry = createEntryUnchecked(rawSystem, rawRecipe);
                    Optional<RecipeContentSource> sourceOpt = extractContentSourceOrThrow(entry, isJeiSystem);
                    if (sourceOpt.isEmpty()) {
                        recipesResolveFailed++;
                        if (isVanillaSystem) {
                            recordVanillaUnresolvedOutput(rawRecipe, unresolvedOutputs);
                        }
                        continue;
                    }
                    long sourceRef = entriesByRef.size() + 1L;
                    AdaptOutcome adapted = adaptEntry(
                        entry,
                        systemId,
                        sourceKind,
                        sysSnap.machines(),
                        sysSnap.systemTags(),
                        sourceOpt.get(),
                        sourceRef,
                        classifyViews.size()
                    );
                    sysSuccesses++;
                    consecutiveExceptions = 0;
                    if (adapted.record != null) {
                        records.add(adapted.record);
                        entriesByRef.add(entry);
                        classifyViews.add(adapted.classifyView);
                        RecipeUsePolicy hintPolicy = extractRoutingHintPolicy(entry);
                        if (hintPolicy != null && hintPolicy != RecipeUsePolicy.normal) {
                            systemHintPolicies.put(sourceRef, hintPolicy);
                        }
                    } else if (adapted.dropReason != null) {
                        drops.merge(adapted.dropReason, 1, Integer::sum);
                    }
                } catch (RuntimeException | LinkageError e) {
                    recipesExceptionFailed++;
                    consecutiveExceptions++;
                    if (isJeiSystem && sysSuccesses == 0 && consecutiveExceptions >= jeiCircuitBreakerThreshold) {
                        systemsFailed++;
                        logger.warn(
                            "Circuit-breaking JEI recipe system {} after {} consecutive exceptions: {}",
                            systemId,
                            consecutiveExceptions,
                            e.toString()
                        );
                        break;
                    }
                }
            }
        }

        if (cancelled.getAsBoolean() || RecipeApiImpl.attachEpoch() != plan.attachEpoch()) {
            return Optional.empty();
        }

        // Stage J1-J4 universal JEI normalization, dual-layer canonical tagging, and SPI enrichers.
        JeiRecipeNormalizer.NormalizationResult normalized = JeiRecipeNormalizer.normalize(
            records,
            List.of(),
            PlanningExtensionRegistry.instance().recipeEnrichers()
        );
        List<RawRecipeRecord> finalRecords = normalized.normalizedRecords();

        long[] sourceRefs = new long[finalRecords.size()];
        for (int i = 0; i < finalRecords.size(); i++) {
            sourceRefs[i] = finalRecords.get(i).sourceRef();
        }
        SystemAdmissionPolicy policy = ClassifierVerdictBridge.classifyToPolicy(
            defaultPolicy,
            RecipeClassificationService.classifier(),
            classifyViews,
            sourceRefs,
            systemHintPolicies
        );

        RawRecipeCatalogSnapshot snapshot = new RawRecipeCatalogSnapshot(catalogVersion, finalRecords, unresolvedOutputs);
        CaptureStats stats = new CaptureStats(
            systemsVisited,
            systemsFailed,
            recipesVisited,
            finalRecords.size(),
            recipesResolveFailed,
            recipesExceptionFailed,
            unresolvedOutputs.size(),
            Collections.unmodifiableMap(drops)
        );
        return Optional.of(
            new CapturedCatalog(plan.attachEpoch(), snapshot, policy, Collections.unmodifiableList(entriesByRef), stats)
        );
    }

    public static Map<CompiledRecipeKey, RecipeEntry<?>> buildRecipeIndex(
        CompiledRecipeGraph graph,
        List<RecipeEntry<?>> entriesBySourceRef
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(entriesBySourceRef, "entriesBySourceRef");
        int count = graph.stats().compiledRecipes();
        Map<CompiledRecipeKey, RecipeEntry<?>> index = new HashMap<>((int) (count / 0.75) + 16);
        for (int cid = 0; cid < count; cid++) {
            long ref = graph.sourceRefOf(cid);
            if (ref <= 0L || ref > entriesBySourceRef.size()) {
                continue;
            }
            Optional<CompiledRecipe> recipeOpt = graph.recipeByCompactId(cid);
            if (recipeOpt.isPresent()) {
                index.put(recipeOpt.get().key(), entriesBySourceRef.get((int) (ref - 1L)));
            }
        }
        return Collections.unmodifiableMap(index);
    }

    public static Optional<RecipeContentSource> contentSource(RecipeEntry<?> entry) {
        Objects.requireNonNull(entry, "entry");
        return extractContentSourceSafe(entry, entry.system() instanceof JeiCategoryRecipeSystem<?>);
    }

    private static Optional<RecipeContentSource> extractContentSourceSafe(RecipeEntry<?> entry, boolean isJeiSystem) {
        try {
            return extractContentSourceOrThrow(entry, isJeiSystem);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static Optional<RecipeContentSource> extractContentSourceOrThrow(
        RecipeEntry<?> entry,
        boolean isJeiSystem
    ) {
        RoutingRecipeView view = routingViewUnchecked(entry);
        if (view != null) {
            List<InputContent> inputs = toList(view.inputs());
            List<OutputContent> outputs = toList(view.outputs());
            Map<Integer, Integer> durabilityCosts = null;
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) instanceof DurabilityContent durability && durability.durabilityCost() > 0) {
                    if (durabilityCosts == null) {
                        durabilityCosts = new HashMap<>(2);
                    }
                    durabilityCosts.put(i, durability.durabilityCost());
                }
            }
            return Optional.of(new RecipeContentSource(inputs, outputs, durabilityCosts == null ? Map.of() : durabilityCosts));
        }
        if (isJeiSystem) {
            return Optional.empty();
        }
        ResolveResult resolved = entry.resolveResult(ResolveContext.empty());
        if (resolved == null || !resolved.resolved()) {
            return Optional.empty();
        }
        RecipeProjection projection = resolved.projection();
        if (projection == null) {
            return Optional.empty();
        }
        List<InputContent> inputs = new ArrayList<>(projection.inputs().size());
        Map<Integer, Integer> durabilityCosts = null;
        int idx = 0;
        for (ProjectionSlot<InputContent> slot : projection.inputs()) {
            InputContent content = slot.content();
            inputs.add(content);
            Integer cost;
            if (content instanceof DurabilityContent durability) {
                cost = durability.durabilityCost();
            } else {
                cost = slot.attribute(ProjectionKeys.durabilityCost);
                if (cost == null && slot.tags().has(ProjectionKeys.Tags.durabilityTool)) {
                    cost = 1;
                }
            }
            if (cost != null && cost > 0) {
                if (durabilityCosts == null) {
                    durabilityCosts = new HashMap<>(2);
                }
                durabilityCosts.put(idx, cost);
            }
            idx++;
        }
        List<OutputContent> outputs = new ArrayList<>(projection.outputs().size());
        for (ProjectionSlot<OutputContent> slot : projection.outputs()) {
            outputs.add(slot.content());
        }
        return Optional
                .of(new RecipeContentSource(inputs, outputs, durabilityCosts == null ? Map.of() : durabilityCosts));
    }

    private static @Nullable RecipeUsePolicy extractRoutingHintPolicy(RecipeEntry<?> entry) {
        try {
            RecipeScreeningHints hints = routingHintsUnchecked(entry);
            if (hints == null || hints.isEmpty()) {
                return null;
            }
            RecipeUsePolicy policy = hints.usePolicy();
            if (policy == RecipeUsePolicy.disabledByDefault || policy == RecipeUsePolicy.lastResort) {
                return policy;
            }
            if (hints.roles().contains(recyclingRole)) {
                return RecipeUsePolicy.lastResort;
            }
            return policy == RecipeUsePolicy.normal ? null : policy;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static AdaptOutcome adaptEntry(
        RecipeEntry<?> entry,
        Namespace systemId,
        String sourceKind,
        List<MachineEntry> machines,
        Set<Namespace> systemTags,
        RecipeContentSource source,
        long sourceRef,
        int classifyIndex
    ) {
        if (source.inputs().isEmpty() || source.outputs().isEmpty()) {
            return AdaptOutcome.dropped(DropReason.emptySlotLists);
        }
        List<RawIngredientSlot> inputs = new ArrayList<>(source.inputs().size());
        List<RawOutputSlot> outputs = new ArrayList<>(source.outputs().size());
        List<RawOutputSlot> catalystReturns = null;
        Map<Namespace, Double> recipeValues = null;
        List<ClassifyContent> cInputs = new ArrayList<>(source.inputs().size());
        List<ClassifyContent> cOutputs = new ArrayList<>(source.outputs().size());

        for (int i = 0; i < source.inputs().size(); i++) {
            InputContent in = source.inputs().get(i);
            int durCost = source.durabilityCosts().getOrDefault(i, 0);
            CirrusContentBridge.InputMapping mapped = CirrusContentBridge.mapInput(in, durCost);
            if (mapped.dropReason() != null) {
                return AdaptOutcome.dropped(mapped.dropReason());
            }
            if (mapped.valueTypeId() != null) {
                if (recipeValues == null) {
                    recipeValues = new LinkedHashMap<>();
                }
                recipeValues.merge(mapped.valueTypeId(), mapped.valueAmount(), Double::sum);
                cInputs.add(
                    new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, mapped.valueAmount(), in.chance())
                );
            } else if (mapped.slot() != null) {
                inputs.add(mapped.slot());
                if (mapped.catalystReturnOutput() != null) {
                    if (catalystReturns == null) {
                        catalystReturns = new ArrayList<>(2);
                    }
                    catalystReturns.add(mapped.catalystReturnOutput());
                }
                cInputs.add(toClassifyInput(mapped.slot(), in.chance()));
            }
        }

        if (inputs.isEmpty()) {
            return AdaptOutcome.dropped(DropReason.noInputAfterValues);
        }

        for (OutputContent out : source.outputs()) {
            CirrusContentBridge.OutputMapping mapped = CirrusContentBridge.mapOutput(out);
            if (mapped.dropReason() != null) {
                return AdaptOutcome.dropped(mapped.dropReason());
            }
            if (mapped.valueTypeId() != null) {
                cOutputs.add(
                    new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, mapped.valueAmount(), out.chance())
                );
            } else if (mapped.slot() != null) {
                outputs.add(mapped.slot());
                cOutputs.add(
                    ClassifyContent.stack(mapped.slot().resource().displayId(), mapped.slot().amount(), out.chance())
                );
            }
        }

        if (outputs.isEmpty()) {
            return AdaptOutcome.dropped(DropReason.noOutputAfterValues);
        }
        if (catalystReturns != null) {
            outputs.addAll(catalystReturns);
        }

        ResourceLocation rawId = null;
        try {
            rawId = entry.id();
        } catch (RuntimeException | LinkageError ignored) {}

        boolean opaqueConstraint = hasOpaqueRecipeConstraint(entry);
        RecipeEvidenceTier evidenceTier = "vanilla".equals(sourceKind) || "mod_native".equals(sourceKind)
                ? RecipeEvidenceTier.tierA
                : RecipeEvidenceTier.tierC;
        double initialFidelity = evidenceTier == RecipeEvidenceTier.tierA ? (opaqueConstraint ? 0.65 : 1.0) : 0.70;

        RawRecipeRecord record = new RawRecipeRecord(
            systemId,
            sourceKind,
            Optional.ofNullable(rawId),
            machines,
            inputs,
            outputs,
            recipeValues == null ? Map.of() : recipeValues,
            sourceRef,
            systemTags,
            Set.of(),
            evidenceTier,
            initialFidelity,
            Optional.empty(),
            opaqueConstraint
        );
        List<String> classifyMachineIds = new ArrayList<>(machines.size());
        for (MachineEntry m : machines) {
            classifyMachineIds.add(m.contentKey().map(ComponentStackKey::displayId).orElseGet(m::canonicalKey));
        }
        RecipeClassifyView view = new RecipeClassifyView(
            classifyIndex,
            systemId.toString(),
            rawId == null ? "" : rawId.toString(),
            classifyMachineIds,
            cInputs,
            cOutputs
        );
        return AdaptOutcome.kept(record, view);
    }

    private static ClassifyContent toClassifyInput(RawIngredientSlot slot, float chance) {
        if (slot.tagKey().isPresent()) {
            Set<String> ids = new LinkedHashSet<>(slot.candidates().size());
            for (ComponentStackKey cand : slot.candidates()) {
                ids.add(cand.displayId());
            }
            return ClassifyContent.tag(slot.tagKey().get().location().toString(), ids, slot.amount(), chance);
        }
        if (slot.candidates().size() == 1) {
            return ClassifyContent.stack(slot.candidates().get(0).displayId(), slot.amount(), chance);
        }
        Set<String> ids = new LinkedHashSet<>(slot.candidates().size());
        for (ComponentStackKey cand : slot.candidates()) {
            ids.add(cand.displayId());
        }
        return new ClassifyContent(ClassifyContent.Kind.any, ids, null, slot.amount(), chance);
    }

    private static void recordVanillaUnresolvedOutput(Object rawRecipe, Set<ComponentStackKey> unresolvedOutputs) {
        if (!(rawRecipe instanceof RecipeHolder<?> holder)) {
            return;
        }
        try {
            ItemStack result = RecipeHelper.getOutput(holder.value());
            CirrusContentBridge.toKey(result).ifPresent(unresolvedOutputs::add);
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private static List<MachineEntry> extractSystemMachines(RecipeApi api, RecipeSystem<?> system) {
        TreeSet<MachineEntry> entries = new TreeSet<>(MachineEntry.comparator);
        try {
            MachineEntry convention = system.conventionMachine();
            if (convention != null && !convention.isEmpty()) {
                entries.add(convention);
            }
        } catch (RuntimeException | LinkageError ignored) {}
        try {
            Namespace systemId = system.systemId();
            for (MachineEntry entry : api.getMachines(systemId, true)) {
                if (entry != null && !entry.isEmpty()) {
                    entries.add(entry);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {}
        return entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    private static Set<Namespace> extractSystemTags(RecipeApi api, RecipeSystem<?> system) {
        TreeSet<Namespace> tags = new TreeSet<>(Comparator.comparing(Namespace::toString));
        try {
            api.getSystemTags(system.systemId()).forEach(ns -> {
                if (ns != null) {
                    tags.add(ns);
                }
            });
        } catch (RuntimeException | LinkageError ignored) {}
        if (system instanceof VanillaRecipeSystem<?>) {
            tags.add(RecipeSystemTags.vanilla);
        } else if (system instanceof JeiCategoryRecipeSystem<?> || "jei".equals(system.systemId().root())) {
            tags.add(RecipeSystemTags.jeiFallback);
        } else {
            tags.add(RecipeSystemTags.modNative);
        }
        return tags.isEmpty() ? Set.of() : Collections.unmodifiableSet(tags);
    }

    @SuppressWarnings("unchecked")
    private static <R> boolean hasOpaqueRecipeConstraint(RecipeEntry<R> entry) {
        try {
            RecipeConstraint<R> constraint = entry.system().getRecipeConstraint(entry.recipe());
            return constraint != null && constraint != RecipeConstraint.ingredientConstraint();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static String sourceKindOf(RecipeSystem<?> system) {
        if (system instanceof VanillaRecipeSystem<?>) {
            return "vanilla";
        }
        if (system instanceof JeiCategoryRecipeSystem<?>) {
            return "jei_fallback";
        }
        String id = system.systemId().toString();
        return id.startsWith("jei:") ? "jei_fallback" : "mod_native";
    }

    @SuppressWarnings("unchecked")
    private static <R> RecipeEntry<R> createEntryUnchecked(RecipeSystem<?> system, Object recipe) {
        return RecipeEntry.of((RecipeSystem<R>) system, (R) recipe);
    }

    @SuppressWarnings("unchecked")
    private static <R> @Nullable RoutingRecipeView routingViewUnchecked(RecipeEntry<R> entry) {
        return entry.system().routingView(ResolveContext.empty(), entry.recipe());
    }

    @SuppressWarnings("unchecked")
    private static <R> @Nullable RecipeScreeningHints routingHintsUnchecked(RecipeEntry<R> entry) {
        return entry.system().routingHints(entry.recipe());
    }

    private static <T> List<T> toList(Iterable<? extends T> iterable) {
        if (iterable instanceof List<? extends T> list) {
            return List.copyOf(list);
        }
        List<T> out = new ArrayList<>();
        for (T elem : iterable) {
            out.add(elem);
        }
        return out;
    }

    private record AdaptOutcome(
        @Nullable RawRecipeRecord record,
        @Nullable RecipeClassifyView classifyView,
        @Nullable DropReason dropReason
    ) {
        static AdaptOutcome kept(RawRecipeRecord record, RecipeClassifyView view) {
            return new AdaptOutcome(record, view, null);
        }

        static AdaptOutcome dropped(DropReason reason) {
            return new AdaptOutcome(null, null, reason);
        }
    }
}
