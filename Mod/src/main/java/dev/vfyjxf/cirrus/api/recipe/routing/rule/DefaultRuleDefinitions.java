package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;

import java.util.List;

public final class DefaultRuleDefinitions {
    public static final RuleCollectionKey defaultCollectionKey = new RuleCollectionKey(RuleCollectionScope.defaults, "builtin");
    public static final Namespace standardDefaultRuleId = Namespace.of("cirrus", "routing/rule/standard_default");
    public static final Namespace standardExecutionRuleId = Namespace.of("cirrus", "routing/rule/standard_execution");
    public static final Namespace calculatorPreviewRuleId = Namespace.of("cirrus", "routing/rule/calculator_preview");
    public static final Namespace calculatorPreviewProfileId = Namespace.of("cirrus", "routing/profile/calculator_preview");
    public static final Namespace assuredBoundedRuleId = Namespace.of("cirrus", "routing/rule/assured_bounded_default");
    public static final Namespace assuredProvenRuleId = Namespace.of("cirrus", "routing/rule/assured_proven_compat");
    private static final double calculatorPreviewHardThreshold = 1.01d;

    private DefaultRuleDefinitions() {
    }

    public static RuleDefinition standardDefault() {
        return builtin(
            standardDefaultRuleId,
            "Standard",
            DefaultRuleProfiles.standardDefaultId,
            RuleProfilePreset.standardDefault,
            UnresolvedInputMode.strict
        );
    }

    public static RuleDefinition standardExecution() {
        return builtin(
            standardExecutionRuleId,
            "Execution",
            DefaultRuleProfiles.standardExecutionId,
            RuleProfilePreset.standardExecution,
            UnresolvedInputMode.directSupplyOnNoSource
        );
    }

    public static RuleDefinition calculatorPreview() {
        return new RuleDefinition(
            calculatorPreviewRuleId,
            "Calculator preview",
            new RuleProfileSpec(
                calculatorPreviewProfileId,
                RuleProfilePreset.standardExecution,
                UnresolvedInputMode.directSupplyOnNoSourceOrSafeOrCycleExhausted,
                8,
                4,
                DefaultRuleProfiles.standardExecution().nearTieEpsilon(),
                DefaultRuleProfiles.standardExecution().amplificationSoftThreshold(),
                calculatorPreviewHardThreshold,
                false
            ),
            SourcePolicy.defaultPolicy(),
            Lists.immutable.empty(),
            false
        );
    }

    public static RuleDefinition assuredBounded() {
        return builtin(
            assuredBoundedRuleId,
            "Assured bounded",
            DefaultRuleProfiles.assuredBoundedDefaultId,
            RuleProfilePreset.assuredBoundedDefault,
            UnresolvedInputMode.strict
        );
    }

    public static RuleDefinition assuredProven() {
        return builtin(
            assuredProvenRuleId,
            "Assured proven",
            DefaultRuleProfiles.assuredProvenCompatId,
            RuleProfilePreset.assuredProvenCompat,
            UnresolvedInputMode.strict
        );
    }

    public static RuleCollection defaultCollection() {
        return new RuleCollection(defaultCollectionKey, List.of(
            standardDefault(),
            standardExecution(),
            calculatorPreview(),
            assuredBounded(),
            assuredProven()
        ));
    }

    public static ActiveRuleSelection defaultActiveSelection() {
        return new ActiveRuleSelection(defaultCollectionKey, standardExecutionRuleId);
    }

    private static RuleDefinition builtin(
        Namespace ruleId,
        String name,
        Namespace profileId,
        RuleProfilePreset preset,
        UnresolvedInputMode unresolvedInputMode
    ) {
        return new RuleDefinition(
            ruleId,
            name,
            RuleProfileSpec.fromPreset(profileId, preset, unresolvedInputMode),
            SourcePolicy.defaultPolicy(),
            Lists.immutable.empty(),
            false
        );
    }
}
