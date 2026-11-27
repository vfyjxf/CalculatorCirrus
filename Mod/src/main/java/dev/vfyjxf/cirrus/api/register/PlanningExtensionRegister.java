package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.planning.spi.AcquisitionEstimatorSpi;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;
import dev.vfyjxf.cirrus.api.planning.spi.ContextContributor;
import dev.vfyjxf.cirrus.api.planning.spi.DecisionDetectorExtension;
import dev.vfyjxf.cirrus.api.planning.spi.PlanningTelemetrySink;
import dev.vfyjxf.cirrus.api.planning.spi.RecipeMetadataEnricher;
import dev.vfyjxf.cirrus.api.planning.spi.ScoringContributor;
import org.jetbrains.annotations.ApiStatus;

/**
 * Plugin registration entrypoint for all 7 Planning v3 extension SPIs.
 */
@ApiStatus.NonExtendable
public interface PlanningExtensionRegister {

    void registerContextContributor(ContextContributor contributor);

    void registerRecipeMetadataEnricher(RecipeMetadataEnricher enricher);

    void registerBaseBoundaryProvider(BaseBoundaryProvider provider);

    void registerAcquisitionEstimator(AcquisitionEstimatorSpi estimator);

    void registerScoringContributor(ScoringContributor contributor);

    void registerDecisionDetector(DecisionDetectorExtension detector);

    void registerTelemetrySink(PlanningTelemetrySink sink);
}
