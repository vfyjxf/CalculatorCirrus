package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.planning.spi.AcquisitionEstimatorSpi;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;
import dev.vfyjxf.cirrus.api.planning.spi.ContextContributor;
import dev.vfyjxf.cirrus.api.planning.spi.DecisionDetectorExtension;
import dev.vfyjxf.cirrus.api.planning.spi.PlanningTelemetrySink;
import dev.vfyjxf.cirrus.api.planning.spi.RecipeMetadataEnricher;
import dev.vfyjxf.cirrus.api.planning.spi.ScoringContributor;
import dev.vfyjxf.cirrus.api.register.PlanningExtensionRegister;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe runtime registry for planning plugin SPIs registered via {@link dev.vfyjxf.cirrus.api.plugin.CirrusPlugin}.
 */
public final class PlanningExtensionRegistry implements PlanningExtensionRegister {

    private static volatile PlanningExtensionRegistry active = new PlanningExtensionRegistry();

    private final CopyOnWriteArrayList<ContextContributor> contextContributors = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RecipeMetadataEnricher> recipeEnrichers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BaseBoundaryProvider> baseProviders = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AcquisitionEstimatorSpi> acquisitionEstimators = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ScoringContributor> scoringContributors = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DecisionDetectorExtension> decisionDetectors = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<PlanningTelemetrySink> telemetrySinks = new CopyOnWriteArrayList<>();

    public static PlanningExtensionRegistry instance() {
        return active;
    }

    public static void attach(PlanningExtensionRegistry registry) {
        active = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void registerContextContributor(ContextContributor contributor) {
        contextContributors.add(Objects.requireNonNull(contributor, "contributor"));
    }

    @Override
    public void registerRecipeMetadataEnricher(RecipeMetadataEnricher enricher) {
        recipeEnrichers.add(Objects.requireNonNull(enricher, "enricher"));
    }

    @Override
    public void registerBaseBoundaryProvider(BaseBoundaryProvider provider) {
        baseProviders.add(Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public void registerAcquisitionEstimator(AcquisitionEstimatorSpi estimator) {
        acquisitionEstimators.add(Objects.requireNonNull(estimator, "estimator"));
    }

    @Override
    public void registerScoringContributor(ScoringContributor contributor) {
        scoringContributors.add(Objects.requireNonNull(contributor, "contributor"));
    }

    @Override
    public void registerDecisionDetector(DecisionDetectorExtension detector) {
        decisionDetectors.add(Objects.requireNonNull(detector, "detector"));
    }

    @Override
    public void registerTelemetrySink(PlanningTelemetrySink sink) {
        telemetrySinks.add(Objects.requireNonNull(sink, "sink"));
    }

    public List<ContextContributor> contextContributors() {
        return List.copyOf(contextContributors);
    }

    public List<RecipeMetadataEnricher> recipeEnrichers() {
        return List.copyOf(recipeEnrichers);
    }

    public List<BaseBoundaryProvider> baseProviders() {
        return List.copyOf(baseProviders);
    }

    public List<AcquisitionEstimatorSpi> acquisitionEstimators() {
        return acquisitionEstimators.stream()
                .sorted(Comparator.comparingInt(AcquisitionEstimatorSpi::priority).reversed()
                        .thenComparing(AcquisitionEstimatorSpi::estimatorId))
                .toList();
    }

    public List<ScoringContributor> scoringContributors() {
        return List.copyOf(scoringContributors);
    }

    public List<DecisionDetectorExtension> decisionDetectors() {
        return List.copyOf(decisionDetectors);
    }

    public List<PlanningTelemetrySink> telemetrySinks() {
        return List.copyOf(telemetrySinks);
    }
}
