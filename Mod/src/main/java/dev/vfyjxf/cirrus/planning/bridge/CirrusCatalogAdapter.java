package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cirrus.planning.adapter.ClassifierVerdictBridge;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.classify.RecipeClassificationService;
import dev.vfyjxf.cirrus.recipe.clause.DurabilityCostClause;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final Logger LOGGER = LoggerFactory.getLogger("CalculatorCirrus PlannerBridge");

    public record RecipeContentSource(
            List<InputContent> inputs,
            List<OutputContent> outputs,
            Map<Integer, DurabilityCostClause> durabilityClauses
    ) {
        public RecipeContentSource(List<InputContent> inputs, List<OutputContent> outputs) {
            this(inputs, outputs, Map.of());
        }

        public RecipeContentSource {
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
            durabilityClauses = durabilityClauses == null || durabilityClauses.isEmpty()
                    ? Map.of()
                    : Map.copyOf(durabilityClauses);
        }
    }

    public record SystemSnapshot(RecipeSystem<?> system, List<String> machineIds) {
        public SystemSnapshot {
            Objects.requireNonNull(system, "system");
            machineIds = List.copyOf(Objects.requireNonNull(machineIds, "machineIds"));
        }
    }

    public record SystemCapturePlan(long attachEpoch, List<SystemSnapshot> systems) {
    }

    public record CaptureStats(
            int systemsVisited,
            int systemsFailed,
            int recipesVisited,
            int recipesKept,
            int recipesResolveFailed,
            int recipesExceptionFailed,
            int unresolvedOutputsRecorded,
            Map<DropReason, Integer> droppedByReason
    ) {
    }

    public record CapturedCatalog(
            long attachEpoch,
            RawRecipeCatalogSnapshot snapshot,
            SystemAdmissionPolicy policy,
            List<RecipeEntry<?>> entriesBySourceRef,
            CaptureStats stats
    ) {
    }

    public CirrusCatalogAdapter() {
    }

    public SystemCapturePlan prepareCapturePlan() {
        long epoch = RecipeApiImpl.attachEpoch();
        RecipeApi api = RecipeApi.instance();
        List<SystemSnapshot> snapshots = new ArrayList<>();
        for (RecipeSystem<?> system : api.allSystems()) {
            List<String> machines = extractSystemMachines(api, system);
            snapshots.add(new SystemSnapshot(system, machines));
        }
        return new SystemCapturePlan(epoch, List.copyOf(snapshots));
    }

    public Optional<CapturedCatalog> capture(
            SystemCapturePlan plan,
            long catalogVersion,
            BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cancelled, "cancelled");
        if (cancelled.getAsBoolean() || RecipeApiImpl.attachEpoch() != plan.attachEpoch()) {
            return Optional.empty();
        }

        List<RawRecipeRecord> records = new ArrayList<>(4096);
        List<RecipeEntry<?>> entriesByRef = new ArrayList<>(4096);
        List<RecipeClassifyView> classifyViews = new ArrayList<>(4096);
        Set<ResourceKey> unresolvedOutputs = new TreeSet<>();
        EnumMap<DropReason, Integer> drops = new EnumMap<>(DropReason.class);

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
            RichIterable<?> allRecipes;
            try {
                allRecipes = rawSystem.allRecipes();
            } catch (RuntimeException | LinkageError e) {
                systemsFailed++;
                LOGGER.warn("Failed enumerating recipes for system {}: {}", rawSystem.systemId(), e.toString());
                continue;
            }
            if (allRecipes == null) {
                continue;
            }

            String systemId = rawSystem.systemId().toString();
            String sourceKind = sourceKindOf(rawSystem);
            boolean isJeiSystem = rawSystem instanceof JeiCategoryRecipeSystem<?>;
            boolean isVanillaSystem = rawSystem instanceof VanillaRecipeSystem<?>;

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
                    Optional<RecipeContentSource> sourceOpt = extractContentSourceSafe(entry, isJeiSystem);
                    if (sourceOpt.isEmpty()) {
                        recipesResolveFailed++;
                        if (isVanillaSystem) {
                            recordVanillaUnresolvedOutput(rawRecipe, unresolvedOutputs);
                        }
                        continue;
                    }
                    AdaptOutcome adapted = adaptEntry(
                            entry,
                            systemId,
                            sourceKind,
                            sysSnap.machineIds(),
                            sourceOpt.get(),
                            entriesByRef.size() + 1L,
                            classifyViews.size()
                    );
                    if (adapted.record != null) {
                        records.add(adapted.record);
                        entriesByRef.add(entry);
                        classifyViews.add(adapted.classifyView);
                    } else if (adapted.dropReason != null) {
                        drops.merge(adapted.dropReason, 1, Integer::sum);
                    }
                } catch (RuntimeException | LinkageError e) {
                    recipesExceptionFailed++;
                }
            }
        }

        if (cancelled.getAsBoolean() || RecipeApiImpl.attachEpoch() != plan.attachEpoch()) {
            return Optional.empty();
        }

        long[] sourceRefs = new long[records.size()];
        for (int i = 0; i < records.size(); i++) {
            sourceRefs[i] = records.get(i).sourceRef();
        }
        SystemAdmissionPolicy policy = ClassifierVerdictBridge.classifyToPolicy(
                DefaultSystemAdmissionPolicy.defaults(),
                RecipeClassificationService.classifier(),
                classifyViews,
                sourceRefs
        );

        RawRecipeCatalogSnapshot snapshot = new RawRecipeCatalogSnapshot(catalogVersion, records, unresolvedOutputs);
        CaptureStats stats = new CaptureStats(
                systemsVisited,
                systemsFailed,
                recipesVisited,
                records.size(),
                recipesResolveFailed,
                recipesExceptionFailed,
                unresolvedOutputs.size(),
                Collections.unmodifiableMap(drops)
        );
        return Optional.of(new CapturedCatalog(
                plan.attachEpoch(),
                snapshot,
                policy,
                Collections.unmodifiableList(entriesByRef),
                stats
        ));
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

    private static Optional<RecipeContentSource> extractContentSourceSafe(
            RecipeEntry<?> entry,
            boolean isJeiSystem
    ) {
        RoutingRecipeView view;
        try {
            view = routingViewUnchecked(entry);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
        if (view != null) {
            List<InputContent> inputs = toList(view.inputs());
            List<OutputContent> outputs = toList(view.outputs());
            return Optional.of(new RecipeContentSource(inputs, outputs, Map.of()));
        }
        if (isJeiSystem) {
            return Optional.empty();
        }
        ResolveResult resolved;
        try {
            resolved = entry.resolveResult(ResolveContext.empty());
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
        if (resolved == null || !resolved.resolved()) {
            return Optional.empty();
        }
        RecipeProjection projection = resolved.projection();
        if (projection == null) {
            return Optional.empty();
        }
        List<InputContent> inputs = new ArrayList<>(projection.inputs().size());
        Map<Integer, DurabilityCostClause> durabilityClauses = null;
        int idx = 0;
        for (ClausedContent<InputContent> claused : projection.inputs()) {
            inputs.add(claused.content());
            if (claused.hasClause(CirrusTypes.Clauses.durabilityCost)) {
                RecipeClause clause = claused.getClause(CirrusTypes.Clauses.durabilityCost);
                if (clause instanceof DurabilityCostClause dur) {
                    if (durabilityClauses == null) {
                        durabilityClauses = new HashMap<>(2);
                    }
                    durabilityClauses.put(idx, dur);
                }
            }
            idx++;
        }
        List<OutputContent> outputs = new ArrayList<>(projection.outputs().size());
        for (ClausedContent<OutputContent> claused : projection.outputs()) {
            outputs.add(claused.content());
        }
        return Optional.of(new RecipeContentSource(
                inputs,
                outputs,
                durabilityClauses == null ? Map.of() : durabilityClauses
        ));
    }

    private static AdaptOutcome adaptEntry(
            RecipeEntry<?> entry,
            String systemId,
            String sourceKind,
            List<String> machineIds,
            RecipeContentSource source,
            long sourceRef,
            int classifyIndex
    ) {
        if (source.inputs().isEmpty() || source.outputs().isEmpty()) {
            return AdaptOutcome.dropped(DropReason.EMPTY_SLOT_LISTS);
        }
        List<RawIngredientSlot> inputs = new ArrayList<>(source.inputs().size());
        List<RawOutputSlot> outputs = new ArrayList<>(source.outputs().size());
        List<RawOutputSlot> catalystReturns = null;
        Map<String, Double> recipeValues = null;
        List<ClassifyContent> cInputs = new ArrayList<>(source.inputs().size());
        List<ClassifyContent> cOutputs = new ArrayList<>(source.outputs().size());

        for (int i = 0; i < source.inputs().size(); i++) {
            InputContent in = source.inputs().get(i);
            DurabilityCostClause durClause = source.durabilityClauses().get(i);
            CirrusContentBridge.InputMapping mapped = CirrusContentBridge.mapInput(in, durClause);
            if (mapped.dropReason() != null) {
                return AdaptOutcome.dropped(mapped.dropReason());
            }
            if (mapped.valueTypeId() != null) {
                if (recipeValues == null) {
                    recipeValues = new LinkedHashMap<>();
                }
                recipeValues.merge(mapped.valueTypeId(), mapped.valueAmount(), Math::max);
                cInputs.add(new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, mapped.valueAmount(), in.chance()));
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
            return AdaptOutcome.dropped(DropReason.NO_INPUT_AFTER_VALUES);
        }

        for (OutputContent out : source.outputs()) {
            CirrusContentBridge.OutputMapping mapped = CirrusContentBridge.mapOutput(out);
            if (mapped.dropReason() != null) {
                return AdaptOutcome.dropped(mapped.dropReason());
            }
            if (mapped.valueTypeId() != null) {
                if (recipeValues == null) {
                    recipeValues = new LinkedHashMap<>();
                }
                recipeValues.merge(mapped.valueTypeId(), mapped.valueAmount(), Math::max);
                cOutputs.add(new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, mapped.valueAmount(), out.chance()));
            } else if (mapped.slot() != null) {
                outputs.add(mapped.slot());
                cOutputs.add(ClassifyContent.stack(mapped.slot().resource().resourceId(), mapped.slot().amount(), out.chance()));
            }
        }

        if (outputs.isEmpty()) {
            return AdaptOutcome.dropped(DropReason.NO_OUTPUT_AFTER_VALUES);
        }
        if (catalystReturns != null) {
            outputs.addAll(catalystReturns);
        }

        ResourceLocation rawId = null;
        try {
            rawId = entry.id();
        } catch (RuntimeException | LinkageError ignored) {
        }
        String explicitId = rawId == null ? "" : rawId.toString();

        RawRecipeRecord record = new RawRecipeRecord(
                systemId,
                sourceKind,
                explicitId.isEmpty() ? Optional.empty() : Optional.of(explicitId),
                machineIds,
                inputs,
                outputs,
                recipeValues == null ? Map.of() : recipeValues,
                sourceRef
        );
        RecipeClassifyView view = new RecipeClassifyView(
                classifyIndex,
                systemId,
                explicitId,
                machineIds,
                cInputs,
                cOutputs
        );
        return AdaptOutcome.kept(record, view);
    }

    private static ClassifyContent toClassifyInput(RawIngredientSlot slot, float chance) {
        if (slot.tagId().isPresent()) {
            Set<String> ids = new LinkedHashSet<>(slot.candidates().size());
            for (ResourceKey cand : slot.candidates()) {
                ids.add(cand.resourceId());
            }
            return ClassifyContent.tag(slot.tagId().get(), ids, slot.amount(), chance);
        }
        if (slot.candidates().size() == 1) {
            return ClassifyContent.stack(slot.candidates().get(0).resourceId(), slot.amount(), chance);
        }
        Set<String> ids = new LinkedHashSet<>(slot.candidates().size());
        for (ResourceKey cand : slot.candidates()) {
            ids.add(cand.resourceId());
        }
        return new ClassifyContent(ClassifyContent.Kind.any, ids, null, slot.amount(), chance);
    }

    private static void recordVanillaUnresolvedOutput(Object rawRecipe, Set<ResourceKey> unresolvedOutputs) {
        if (!(rawRecipe instanceof RecipeHolder<?> holder)) {
            return;
        }
        try {
            ItemStack result = RecipeHelper.getOutput(holder.value());
            CirrusContentBridge.toResourceKey(result).ifPresent(unresolvedOutputs::add);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static List<String> extractSystemMachines(RecipeApi api, RecipeSystem<?> system) {
        TreeSet<String> ids = new TreeSet<>();
        try {
            MachineEntry convention = system.conventionMachine();
            extractMachineItemId(convention).ifPresent(ids::add);
        } catch (RuntimeException | LinkageError ignored) {
        }
        try {
            Namespace systemId = system.systemId();
            for (MachineEntry entry : api.getMachines(systemId, true)) {
                extractMachineItemId(entry).ifPresent(ids::add);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    private static Optional<String> extractMachineItemId(@Nullable MachineEntry entry) {
        if (entry == null || entry.isEmpty()) {
            return Optional.empty();
        }
        if (entry instanceof ItemMachineEntry itemEntry) {
            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(itemEntry.machineStack().content());
            if (loc != null && !loc.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                return Optional.of(loc.toString());
            }
        }
        ItemStack stack = entry.toItemStack();
        if (stack != null && !stack.isEmpty()) {
            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (loc != null && !loc.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                return Optional.of(loc.toString());
            }
        }
        return Optional.empty();
    }

    private static String sourceKindOf(RecipeSystem<?> system) {
        if (system instanceof VanillaRecipeSystem<?>) {
            return "vanilla";
        }
        if (system instanceof JeiCategoryRecipeSystem<?>) {
            return "jei";
        }
        String id = system.systemId().toString();
        return id.startsWith("jei:") ? "jei" : "custom";
    }

    @SuppressWarnings("unchecked")
    private static <R> RecipeEntry<R> createEntryUnchecked(RecipeSystem<?> system, Object recipe) {
        return RecipeEntry.of((RecipeSystem<R>) system, (R) recipe);
    }

    @SuppressWarnings("unchecked")
    private static <R> @Nullable RoutingRecipeView routingViewUnchecked(RecipeEntry<R> entry) {
        return entry.system().routingView(ResolveContext.empty(), entry.recipe());
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
