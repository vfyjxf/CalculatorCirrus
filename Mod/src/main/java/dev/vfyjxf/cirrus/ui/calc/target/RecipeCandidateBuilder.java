package dev.vfyjxf.cirrus.ui.calc.target;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseDefinition;
import org.eclipse.collections.api.factory.Lists;

import java.util.*;

public final class RecipeCandidateBuilder {
    private static final Comparator<DatabaseDefinition> DATABASE_PRIORITY = Comparator
        .comparingInt(DatabaseDefinition::priority)
        .thenComparing(DatabaseDefinition::id, (left, right) -> left.compareToIgnoreCase(right));

    public List<RecipeCandidate> buildFromSources(Map<DatabaseDefinition, List<RecipeEntry<?>>> sources) {
        Map<RecipeEntry<?>, List<DatabaseDefinition>> byRecipe = new HashMap<>();
        for (Map.Entry<DatabaseDefinition, List<RecipeEntry<?>>> entry : sources.entrySet()) {
            for (RecipeEntry<?> recipe : entry.getValue()) {
                byRecipe.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }
        try {
            RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.withAll(byRecipe.keySet()));
            return buildFromGraph(graph, byRecipe);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("RecipeApi not initialized")) {
                return buildFromProjections(byRecipe);
            }
            throw ex;
        }
    }

    private List<RecipeCandidate> buildFromGraph(RecipeGraph graph, Map<RecipeEntry<?>, List<DatabaseDefinition>> byRecipe) {
        List<RecipeCandidate> candidates = new ArrayList<>();
        for (RecipeGraph.Node node : graph.nodes()) {
            List<DatabaseDefinition> dbs = byRecipe.getOrDefault(node.recipe(), List.of());
            if (dbs.isEmpty()) {
                continue;
            }
            DatabaseDefinition chosen = dbs.stream()
                .min(DATABASE_PRIORITY)
                .orElseThrow();
            String recipeId = node.recipe().id() != null ? node.recipe().id().toString() : node.recipe().toString();
            candidates.add(new RecipeCandidate(
                node.recipe().systemId().toString(),
                recipeId,
                recipeId,
                chosen,
                List.copyOf(dbs),
                node.recipe(),
                node,
                node.mergedOutputs().collect(o -> o.asResource().toString()).toList(),
                node.mergedInputs().collect(input -> input.toString()).toList()
            ));
        }
        return candidates;
    }

    private List<RecipeCandidate> buildFromProjections(Map<RecipeEntry<?>, List<DatabaseDefinition>> byRecipe) {
        List<RecipeCandidate> candidates = new ArrayList<>();
        for (Map.Entry<RecipeEntry<?>, List<DatabaseDefinition>> entry : byRecipe.entrySet()) {
            RecipeEntry<?> recipeEntry = entry.getKey();
            List<DatabaseDefinition> dbs = entry.getValue();
            if (dbs.isEmpty()) {
                continue;
            }
            DatabaseDefinition chosen = dbs.stream()
                .min(DATABASE_PRIORITY)
                .orElseThrow();
            RecipeProjection projection = recipeEntry.resolve();
            List<String> outputs = projection.outputs()
                                             .collect(ClausedContent::content)
                                             .collect(output -> output.asResource().toString())
                                             .toList();
            List<String> inputs = projection.inputs()
                                            .collect(ClausedContent::content)
                                            .collect(input -> input.toString())
                                            .toList();
            String recipeId = recipeEntry.id() != null ? recipeEntry.id().toString() : recipeEntry.toString();
            candidates.add(new RecipeCandidate(
                recipeEntry.systemId().toString(),
                recipeId,
                recipeId,
                chosen,
                List.copyOf(dbs),
                recipeEntry,
                null,
                outputs,
                inputs
            ));
        }
        return candidates;
    }
}
