package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;

public record RuleRepositoryView(
    RuleCollection defaultCollection,
    RuleCollection playerCollection,
    RuleCollection worldCollection,
    List<RuleCollection> readableWorldCollections,
    ActiveRuleSelection activeSelection
) {
    public RuleRepositoryView {
        Checks.checkNotNull(defaultCollection, "defaultCollection");
        Checks.checkNotNull(playerCollection, "playerCollection");
        Checks.checkNotNull(worldCollection, "worldCollection");
        Checks.checkNotNull(readableWorldCollections, "readableWorldCollections");
        Checks.checkNotNull(activeSelection, "activeSelection");
        readableWorldCollections = List.copyOf(readableWorldCollections);
    }

    public List<RuleCollection> visibleCollections() {
        return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(defaultCollection, playerCollection, worldCollection),
            readableWorldCollections.stream()
        ).toList();
    }

    public RuleDefinition activeRuleOrDefault() {
        for (RuleCollection collection : visibleCollections()) {
            if (!collection.key().equals(activeSelection.collection())) {
                continue;
            }
            for (RuleDefinition rule : collection.rules()) {
                if (rule.ruleId().equals(activeSelection.ruleId())) {
                    return rule;
                }
            }
        }
        return DefaultRuleDefinitions.standardExecution();
    }
}
