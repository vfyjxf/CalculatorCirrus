package dev.vfyjxf.cirrus.api.planning.telemetry;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly-typed, extensible telemetry event hierarchy covering all 5 planning data domains + custom SPI events.
 */
public sealed interface PlanningTelemetryEvent permits
        PlanningTelemetryEvent.PlanInteractionEvent,
        PlanningTelemetryEvent.HumanDecisionEvent,
        PlanningTelemetryEvent.BulkGovernanceEvent,
        PlanningTelemetryEvent.FacilityEvolutionEvent,
        PlanningTelemetryEvent.CraftingExecutionEvent,
        PlanningTelemetryEvent.CustomTelemetryEvent {

    String eventId();

    long timestampEpochMs();

    String worldFingerprint();

    String packFingerprint();

    /** 1. Target query, returned multi-regime candidates, and user's implicit pairwise choice. */
    record PlanInteractionEvent(
        String eventId,
        long timestampEpochMs,
        String worldFingerprint,
        String packFingerprint,
        ComponentStackKey primaryTarget,
        long targetAmount,
        ObjectivePreset preset,
        String chosenPlanId,
        List<String> rejectedPlanIds,
        Map<String, Double> chosenTemplateFeatures,
        List<Map<String, Double>> rejectedTemplateFeatures,
        double chosenBaseObjectiveJ,
        List<Double> rejectedBaseObjectivesJ
    ) implements PlanningTelemetryEvent {
        public PlanInteractionEvent {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(worldFingerprint, "worldFingerprint");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(primaryTarget, "primaryTarget");
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(chosenPlanId, "chosenPlanId");
            rejectedPlanIds = List.copyOf(Objects.requireNonNull(rejectedPlanIds, "rejectedPlanIds"));
            chosenTemplateFeatures = Map.copyOf(Objects.requireNonNull(chosenTemplateFeatures, "chosenTemplateFeatures"));
            rejectedTemplateFeatures = List.copyOf(Objects.requireNonNull(rejectedTemplateFeatures, "rejectedTemplateFeatures"));
            rejectedBaseObjectivesJ = List.copyOf(Objects.requireNonNull(rejectedBaseObjectivesJ, "rejectedBaseObjectivesJ"));
        }
    }

    /** 2. Decision prompt interaction, chosen option, dismissal, and scope. */
    record HumanDecisionEvent(
        String eventId,
        long timestampEpochMs,
        String worldFingerprint,
        String packFingerprint,
        DecisionPromptKind promptKind,
        String questionKey,
        String chosenOptionId,
        boolean dismissed,
        GuidanceScope appliedScope,
        double estimatedEvsiSeconds
    ) implements PlanningTelemetryEvent {
        public HumanDecisionEvent {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(worldFingerprint, "worldFingerprint");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(promptKind, "promptKind");
            Objects.requireNonNull(questionKey, "questionKey");
            Objects.requireNonNull(chosenOptionId, "chosenOptionId");
            Objects.requireNonNull(appliedScope, "appliedScope");
        }
    }

    /** 3. Bulk recipe/system filtering and tagging actions. */
    record BulkGovernanceEvent(
        String eventId,
        long timestampEpochMs,
        String worldFingerprint,
        String packFingerprint,
        String filterSpecSummary,
        String actionKind,
        int affectedRecipeCount,
        int orphanExemptedCount,
        List<Namespace> affectedSystemIds,
        List<CanonicalRecipeTag> appliedTags,
        GuidanceScope scope
    ) implements PlanningTelemetryEvent {
        public BulkGovernanceEvent {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(worldFingerprint, "worldFingerprint");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(filterSpecSummary, "filterSpecSummary");
            Objects.requireNonNull(actionKind, "actionKind");
            affectedSystemIds = List.copyOf(Objects.requireNonNull(affectedSystemIds, "affectedSystemIds"));
            appliedTags = List.copyOf(Objects.requireNonNull(appliedTags, "appliedTags"));
            Objects.requireNonNull(scope, "scope");
        }
    }

    /** 4. User machine ownership, automated line, and Base boundary evolution. */
    record FacilityEvolutionEvent(
        String eventId,
        long timestampEpochMs,
        String worldFingerprint,
        String packFingerprint,
        String changeKind,
        String subjectId,
        int countDelta,
        String source
    ) implements PlanningTelemetryEvent {
        public FacilityEvolutionEvent {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(worldFingerprint, "worldFingerprint");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(changeKind, "changeKind");
            Objects.requireNonNull(subjectId, "subjectId");
            Objects.requireNonNull(source, "source");
        }
    }

    /** 5. Execution session telemetry: observed rates, actual crafting, pauses, and attributed failures. */
    record CraftingExecutionEvent(
        String eventId,
        long timestampEpochMs,
        String worldFingerprint,
        String packFingerprint,
        String recipeSemanticKey,
        Namespace systemId,
        String conditionSignature,
        String adapterVersion,
        long batchesCompleted,
        double observedSecondsPerBatch,
        boolean succeeded,
        ExecutionFailureCategory failureCategory
    ) implements PlanningTelemetryEvent {
        public CraftingExecutionEvent {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(worldFingerprint, "worldFingerprint");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(recipeSemanticKey, "recipeSemanticKey");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(conditionSignature, "conditionSignature");
            Objects.requireNonNull(adapterVersion, "adapterVersion");
            Objects.requireNonNull(failureCategory, "failureCategory");
        }
    }

    /** 6. Plugin-defined structured telemetry event. */
    record CustomTelemetryEvent(
        String eventId,
        long timestampEpochMs,
        String worldFingerprint,
        String packFingerprint,
        String schemaId,
        Map<String, String> payload
    ) implements PlanningTelemetryEvent {
        public CustomTelemetryEvent {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(worldFingerprint, "worldFingerprint");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(schemaId, "schemaId");
            payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
        }
    }
}
