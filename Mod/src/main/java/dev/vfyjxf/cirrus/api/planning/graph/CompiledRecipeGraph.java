package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CompiledRecipeGraph {

    long catalogVersion();

    SystemAdmission admissionOf(String systemId);

    List<CompiledRecipe> producersOf(ResourceKey output);

    List<CompiledRecipe> consumersOf(ResourceKey input);

    List<CompiledCycleMacro> cycleMacrosProducing(ResourceKey output);

    Optional<CompiledRecipe> recipeByCompactId(int compactId);

    Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key);

    Set<ResourceKey> allProducibleResources();

    SccTopology sccTopology();

    GraphCompilationStats stats();

    default long sourceRefOf(int compactId) {
        return 0L;
    }

    default Optional<CompiledCycleMacro> cycleMacroByKey(CompiledCycleMacroKey key) {
        return Optional.empty();
    }

    default List<CompiledCycleMacro> allCycleMacros() {
        return List.of();
    }

    default Set<ResourceKey> unresolvedOutputs() {
        return Set.of();
    }
}
