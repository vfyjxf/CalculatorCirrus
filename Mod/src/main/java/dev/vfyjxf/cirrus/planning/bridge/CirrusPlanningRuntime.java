package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.service.PlanningService;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.human.LayeredHumanGuidanceStore;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class CirrusPlanningRuntime implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus PlannerRuntime");
    private static final AtomicReference<CirrusPlanningRuntime> shared = new AtomicReference<>();

    public record PublishedSnapshot(
        long version,
        long attachEpoch,
        CompiledRecipeGraph graph,
        DefaultPlanningService service,
        PlanningContextFactory contextFactory,
        List<RecipeEntry<?>> entriesBySourceRef,
        CirrusCatalogAdapter.CaptureStats captureStats
    ) {
        public Optional<RecipeEntry<?>> resolveEntry(CompiledRecipeKey key) {
            return ExecutionPlanBridge.resolveEntry(graph, key, entriesBySourceRef);
        }
    }

    private final CirrusCatalogAdapter catalogAdapter;
    private final DefaultRecipeGraphCompiler compiler;
    private final CostMetadataProvider costMetadata;
    private final LayeredHumanGuidanceStore guidanceStore;
    private final Consumer<DeclareOwnedMachineFeedback> machinePersistenceListener;
    private final ExecutorService executor;

    private final AtomicReference<PublishedSnapshot> published = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong(0L);
    private final AtomicLong publishedVersionCounter = new AtomicLong(0L);
    private final AtomicBoolean compiling = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CirrusPlanningRuntime() {
        this(DefaultCostMetadataProvider.instance);
    }

    public CirrusPlanningRuntime(CostMetadataProvider costMetadata) {
        this.catalogAdapter = new CirrusCatalogAdapter();
        this.compiler = new DefaultRecipeGraphCompiler();
        this.costMetadata = Objects.requireNonNull(costMetadata, "costMetadata");
        this.guidanceStore = new LayeredHumanGuidanceStore(
            rule -> WorldFacilityRegistry.installed().ifPresent(reg -> reg.worldSaveListener().accept(rule))
        );
        this.machinePersistenceListener = fb -> WorldFacilityRegistry.installed()
                .ifPresent(reg -> reg.machinePersistenceListener().accept(fb));
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cirrus-planner-compiler");
            t.setDaemon(true);
            return t;
        });
    }

    public static CirrusPlanningRuntime sharedOrCreate() {
        CirrusPlanningRuntime existing = shared.get();
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        CirrusPlanningRuntime created = new CirrusPlanningRuntime();
        if (shared.compareAndSet(existing, created)) {
            WorldFacilityRegistry.installed().ifPresent(reg -> reg.applyRulesTo(created.guidanceStore));
            return created;
        }
        created.close();
        CirrusPlanningRuntime winner = shared.get();
        return winner != null ? winner : sharedOrCreate();
    }

    public static Optional<CirrusPlanningRuntime> shared() {
        CirrusPlanningRuntime existing = shared.get();
        return (existing == null || existing.isClosed()) ? Optional.empty() : Optional.of(existing);
    }

    public static @Nullable CirrusPlanningRuntime sharedIfCreated() {
        CirrusPlanningRuntime existing = shared.get();
        return (existing == null || existing.isClosed()) ? null : existing;
    }

    public static void closeShared() {
        CirrusPlanningRuntime existing = shared.getAndSet(null);
        if (existing != null) {
            existing.close();
        }
    }

    public HumanGuidanceStore guidanceStore() {
        return guidanceStore;
    }

    public long publishedVersion() {
        PublishedSnapshot snap = published.get();
        return snap == null ? 0L : snap.version();
    }

    public Optional<PublishedSnapshot> currentSnapshot() {
        return Optional.ofNullable(published.get());
    }

    public Optional<CompiledRecipeGraph> currentGraph() {
        PublishedSnapshot snap = published.get();
        return snap == null ? Optional.empty() : Optional.of(snap.graph());
    }

    public Optional<PlanningService> currentService() {
        PublishedSnapshot snap = published.get();
        return snap == null ? Optional.empty() : Optional.of(snap.service());
    }

    public Optional<PlanningContextFactory> contextFactory() {
        PublishedSnapshot snap = published.get();
        return snap == null ? Optional.empty() : Optional.of(snap.contextFactory());
    }

    public Optional<RecipeEntry<?>> resolveRecipeEntry(CompiledRecipeKey key) {
        PublishedSnapshot snap = published.get();
        return snap == null ? Optional.empty() : snap.resolveEntry(key);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean isCompiling() {
        return compiling.get();
    }

    public CompletableFuture<Optional<PublishedSnapshot>> reloadFromRuntimeAsync() {
        if (closed.get()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CirrusCatalogAdapter.SystemCapturePlan plan;
        try {
            plan = catalogAdapter.prepareCapturePlan();
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Skipping planner reload because RecipeApi is not ready: {}", ex.toString());
            return CompletableFuture.completedFuture(Optional.empty());
        }

        long gen = generation.incrementAndGet();
        compiling.set(true);
        CompletableFuture<Optional<PublishedSnapshot>> future = new CompletableFuture<>();
        try {
            executor.execute(() -> runCompileTask(gen, plan, future));
        } catch (RuntimeException rejected) {
            if (generation.get() == gen) {
                compiling.set(false);
            }
            future.complete(Optional.empty());
        }
        return future;
    }

    private void runCompileTask(
        long gen,
        CirrusCatalogAdapter.SystemCapturePlan plan,
        CompletableFuture<Optional<PublishedSnapshot>> future
    ) {
        try {
            if (isSuperseded(gen, plan.attachEpoch())) {
                future.complete(Optional.empty());
                return;
            }
            long t0 = System.nanoTime();
            long nextCatalogVersion = publishedVersionCounter.get() + 1L;
            Optional<CirrusCatalogAdapter.CapturedCatalog> capturedOpt = catalogAdapter
                    .capture(plan, nextCatalogVersion, () -> isSuperseded(gen, plan.attachEpoch()));
            if (capturedOpt.isEmpty() || isSuperseded(gen, plan.attachEpoch())) {
                future.complete(Optional.empty());
                return;
            }
            CirrusCatalogAdapter.CapturedCatalog captured = capturedOpt.get();
            CompiledRecipeGraph graph = compiler.compile(captured.snapshot(), captured.policy());
            if (isSuperseded(gen, plan.attachEpoch())) {
                future.complete(Optional.empty());
                return;
            }
            long version = publishedVersionCounter.incrementAndGet();
            DefaultPlanningService service = new DefaultPlanningService(
                graph,
                costMetadata,
                guidanceStore,
                machinePersistenceListener
            );
            PlanningContextFactory factory = new PlanningContextFactory(graph);
            PublishedSnapshot snapshot = new PublishedSnapshot(
                version,
                captured.attachEpoch(),
                graph,
                service,
                factory,
                captured.entriesBySourceRef(),
                captured.stats()
            );
            if (isSuperseded(gen, plan.attachEpoch())) {
                future.complete(Optional.empty());
                return;
            }
            published.set(snapshot);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            logger.info(
                "Published planner graph v{} in {} ms (recipes={}, producible={}, macros={})",
                version,
                elapsedMs,
                graph.stats().compiledRecipes(),
                graph.allProducibleResources().size(),
                graph.stats().cycleMacrosMined()
            );
            future.complete(Optional.of(snapshot));
        } catch (Throwable t) {
            logger.error("Background planner graph compilation failed", t);
            future.complete(Optional.empty());
        } finally {
            if (generation.get() == gen) {
                compiling.set(false);
            }
        }
    }

    private boolean isSuperseded(long gen, long expectedEpoch) {
        return closed.get() || generation.get() != gen || RecipeApiImpl.attachEpoch() != expectedEpoch;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        generation.incrementAndGet();
        published.set(null);
        // Non-blocking shutdown so game lifecycle unload never stalls the main thread.
        executor.shutdownNow();
    }
}
