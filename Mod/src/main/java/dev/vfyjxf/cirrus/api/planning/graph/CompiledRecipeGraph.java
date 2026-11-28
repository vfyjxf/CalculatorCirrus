package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CompiledRecipeGraph {

    long catalogVersion();

    SystemAdmission admissionOf(Namespace systemId);

    List<CompiledRecipe> producersOf(ComponentStackKey output);

    List<CompiledRecipe> consumersOf(ComponentStackKey input);

    List<CompiledCycleMacro> cycleMacrosProducing(ComponentStackKey output);

    Optional<CompiledRecipe> recipeByCompactId(int compactId);

    Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key);

    Set<ComponentStackKey> allProducibleResources();

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

    default Set<ComponentStackKey> unresolvedOutputs() {
        return Set.of();
    }
}
