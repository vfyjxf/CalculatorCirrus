package dev.vfyjxf.cirrus.api.planning.graph;

public record GraphCompilationStats(
        int totalRawRecipes,
        int compiledRecipes,
        int excludedInvalidOrComplex,
        int excludedDisplaySystems,
        int deduplicatedRecipes,
        int reversibleEdgesDetected,
        int cycleMacrosMined,
        int largestSccSize,
        int excludedByPolicy,
        int excludedInsaneAmount,
        int excludedNoInputs,
        int normalizedResourceIds,
        int discouragedRecipes,
        int lastResortRecipes
) {

    public GraphCompilationStats(
            int totalRawRecipes,
            int compiledRecipes,
            int excludedInvalidOrComplex,
            int excludedDisplaySystems,
            int deduplicatedRecipes,
            int reversibleEdgesDetected,
            int cycleMacrosMined,
            int largestSccSize
    ) {
        this(totalRawRecipes, compiledRecipes, excludedInvalidOrComplex, excludedDisplaySystems,
                deduplicatedRecipes, reversibleEdgesDetected, cycleMacrosMined, largestSccSize,
                0, 0, 0, 0, 0, 0);
    }
}
