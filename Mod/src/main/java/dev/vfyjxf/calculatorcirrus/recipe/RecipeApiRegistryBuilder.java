package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystemExtension;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.calculatorcirrus.api.register.*;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class RecipeApiRegistryBuilder
    implements RecipeSystemRegister,
               RecipeFacetRegister,
               MachineEntryRegister,
               IngredientResolverRegister,
               JeiRecipeTypeBridgeRegister {

    final ConcurrentHashMap<ResourceLocation, RecipeSystem<?>> recipeSystems = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, RecipeFacetType> facets = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RecipeFacetType, FacetTransformer<?, ?>> facetTransformers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RecipeFacetType, ViewTransformer<?>> viewTransformers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, Set<MachineEntry>> handlerEntries = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, Set<RecipeType<?>>> jeiRecipeTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<IngredientResolverType<?>, Set<IngredientResolver<?>>> ingredientResolvers = new ConcurrentHashMap<>();

    public void registerSystem(RecipeSystem<?> system) {
        if (recipeSystems.containsKey(system.systemId())) {
            throw new IllegalArgumentException("Recipe system with id: " + system.systemId() + " already registered");
        }
        recipeSystems.put(system.systemId(), system);
    }

    @Override
    public void registerExtension(ResourceLocation systemId, RecipeSystemExtension extension) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    public <T extends RecipeContent, F extends RecipeFacet> void registerFacetTransformer(RecipeFacetType type, FacetTransformer<T, F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        if (facets.containsKey(type.id())) {
            throw new IllegalArgumentException("Recipe facet with id: " + type.id() + " already registered");
        }
        facets.put(type.id(), type);
        facetTransformers.put(type, transformer);
    }

    public void registerFacetTransformer(RecipeFacetType type) {
        Checks.checkNotNull(type, "type");
        if (facets.containsKey(type.id())) {
            throw new IllegalArgumentException("Recipe facet with id: " + type.id() + " already registered");
        }
        facets.put(type.id(), type);
    }


    public <F extends RecipeFacet> void registerViewTransformer(RecipeFacetType type, ViewTransformer<F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        if (viewTransformers.containsKey(type)) {
            throw new IllegalArgumentException("View transformer for facet with id: " + type.id() + " already registered");
        }
        viewTransformers.put(type, transformer);
    }

    @Override
    public void registerForSystem(ResourceLocation systemId, MachineEntry... entries) {
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(entries, "entries");
        handlerEntries.computeIfAbsent(systemId, k -> ConcurrentHashMap.newKeySet())
                      .addAll(Set.of(entries));
    }

    @Override
    public void registerForSystem(ResourceLocation systemId, Collection<? extends MachineEntry> entries) {
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(entries, "entries");
        handlerEntries.computeIfAbsent(systemId, k -> ConcurrentHashMap.newKeySet())
                      .addAll(entries);
    }

    @Override
    public void registerRecipeTypeForSystem(ResourceLocation systemId, RecipeType<?> recipeType) {
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(recipeType, "recipeType");
        jeiRecipeTypes.computeIfAbsent(systemId, k -> ConcurrentHashMap.newKeySet())
                      .add(recipeType);
    }

    @Override
    public <T> void registerIngredientResolver(IngredientResolverType<T> type, IngredientResolver<T> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(resolver, "resolver");
        ingredientResolvers.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet())
                           .add(resolver);
    }
}
