package dev.vfyjxf.cirrus.api.planning.graph;

public interface RecipeGraphCompiler {

    CompiledRecipeGraph compile(RawRecipeCatalogSnapshot rawCatalog, SystemAdmissionPolicy policy);
}
