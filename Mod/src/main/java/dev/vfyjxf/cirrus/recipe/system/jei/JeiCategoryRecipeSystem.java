package dev.vfyjxf.cirrus.recipe.system.jei;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeClaim;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class JeiCategoryRecipeSystem<T> implements RecipeSystem<T> {

    private static final String jeiNamespace = "jei";
    private static final Namespace basedId = Namespace.of(jeiNamespace, "category");

    public static Namespace systemId(RecipeType<?> recipeType) {
        var recipeTypeUid = recipeType.getUid();
        return basedId.resolve(recipeTypeUid.getNamespace()).resolve(recipeTypeUid.getPath());
    }

    public static <T> JeiCategoryRecipeSystem<T> create(IRecipeManager manager, IRecipeCategory<T> category) {
        Checks.checkNotNull(manager, "manager");
        Checks.checkNotNull(category, "category");
        return create(category.getRecipeType());
    }

    public static <T> JeiCategoryRecipeSystem<T> create(RecipeType<T> recipeType) {
        Checks.checkNotNull(recipeType, "recipeType");
        return new JeiCategoryRecipeSystem<>(systemId(recipeType), recipeType);
    }

    private final Namespace systemId;
    private final RecipeType<T> recipeType;
    private volatile @Nullable CatalogSnapshot<T> cachedSnapshot;

    private JeiCategoryRecipeSystem(Namespace systemId, RecipeType<T> recipeType) {
        this.systemId = systemId;
        this.recipeType = recipeType;
    }

    @Override
    public Namespace systemId() {
        return systemId;
    }

    @Override
    public Class<?> basicType() {
        return recipeType.getRecipeClass();
    }

    public RecipeType<T> recipeType() {
        return recipeType;
    }

    @Override
    public RecipeClaim claim(T recipe) {
        return RecipeSystem.super.claim(recipe);
    }

    @Override
    public @Nullable ResourceLocation getId(T recipe) {
        return category().map(category -> category.getRegistryName(recipe)).orElse(null);
    }

    @Override
    public @Nullable T findRecipe(String uniqueId) {
        ResourceLocation parsed = ResourceLocation.tryParse(uniqueId);
        if (parsed != null) {
            return findRecipe(parsed);
        }
        return null;
    }

    @Override
    public @Nullable T findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(T recipe) {
        var id = getId(recipe);
        return id == null ? recipe.hashCode() : id.hashCode();
    }

    @Override
    public boolean sameRecipe(T r1, T r2) {
        var id1 = getId(r1);
        var id2 = getId(r2);
        if (id1 != null && id2 != null) return Objects.equals(id1, id2);
        if (id1 == null && id2 == null) return Objects.equals(r1, r2);
        return false;
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, T recipe) {
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return ResolveResult.unsupported();
        }
        return JeiRecipeLayoutResolver.resolve(this, recipe, manager, recipeType);
    }

    @Override
    public @Nullable RoutingRecipeView routingView(ResolveContext context, T recipe) {
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return null;
        }
        return JeiRecipeLayoutResolver.routingView(this, recipe, manager, recipeType);
    }

    @Override
    public @Unmodifiable RichIterable<T> allRecipes() {
        return snapshot().recipes();
    }

    @Override
    public long lookupVersion() {
        IRecipeManager manager = managerOrNull();
        long catalogVersion = CirrusJeiPlugin.recipeSystemCatalogVersion();
        return (catalogVersion * 31L) ^ (manager == null ? 0L : System.identityHashCode(manager));
    }

    @Override
    public RecipeLookup<T> createLookup() {
        return new Lookup();
    }

    private CatalogSnapshot<T> snapshot() {
        IRecipeManager manager = managerOrNull();
        long catalogVersion = CirrusJeiPlugin.recipeSystemCatalogVersion();
        CatalogSnapshot<T> current = cachedSnapshot;
        if (current != null && current.catalogVersion == catalogVersion && current.manager == manager) {
            return current;
        }
        CatalogSnapshot<T> built = buildSnapshot(manager, catalogVersion);
        cachedSnapshot = built;
        return built;
    }

    private CatalogSnapshot<T> buildSnapshot(@Nullable IRecipeManager manager, long catalogVersion) {
        if (manager == null) {
            return new CatalogSnapshot<>(catalogVersion, null, Lists.immutable.empty(), Map.of());
        }
        try {
            IRecipeCategory<T> category = category().orElse(null);
            List<T> unique = new ArrayList<>();
            Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Map<ResourceLocation, T> byId = new LinkedHashMap<>();
            manager.createRecipeLookup(recipeType).get().forEach(recipe -> {
                if (recipe == null || !seen.add(recipe)) {
                    return;
                }
                unique.add(recipe);
                if (category != null) {
                    try {
                        ResourceLocation id = category.getRegistryName(recipe);
                        if (id != null) {
                            byId.putIfAbsent(id, recipe);
                        }
                    } catch (RuntimeException | LinkageError ignored) {}
                }
            });
            return new CatalogSnapshot<>(catalogVersion, manager, Lists.immutable.withAll(unique), Map.copyOf(byId));
        } catch (RuntimeException | LinkageError ignored) {
            return new CatalogSnapshot<>(catalogVersion, manager, Lists.immutable.empty(), Map.of());
        }
    }

    private @Nullable IRecipeManager managerOrNull() {
        return CirrusJeiPlugin.recipeManagerOrNull();
    }

    private Optional<IRecipeCategory<T>> category() {
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(manager.getRecipeCategory(recipeType));
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private record CatalogSnapshot<T>(
        long catalogVersion,
        @Nullable IRecipeManager manager,
        ImmutableList<T> recipes,
        Map<ResourceLocation, T> byId
    ) {}

    private record StackCriteria(
        RecipeIngredientRole role,
        ContentStack<?, ?> stack,
        MatchStrategy strategy
    ) {}

    private final class Lookup implements RecipeLookup<T> {
        private @Nullable ResourceLocation requiredId;
        private final List<StackCriteria> criteria = new ArrayList<>(2);
        private final List<Predicate<? super T>> predicates = new ArrayList<>(2);
        private boolean impossible;

        @Override
        public RecipeLookup<T> filter(Predicate<? super T> predicate) {
            Checks.checkNotNull(predicate, "predicate");
            predicates.add(predicate);
            return this;
        }

        @Override
        public RecipeLookup<T> withId(ResourceLocation id) {
            Checks.checkNotNull(id, "id");
            if (requiredId != null && !requiredId.equals(id)) {
                impossible = true;
            } else {
                requiredId = id;
            }
            return this;
        }

        @Override
        public RecipeLookup<T> withInput(ContentStack<?, ?> input, MatchStrategy strategy) {
            Checks.checkNotNull(strategy, "strategy");
            if (input == null || input.isEmpty()) {
                impossible = true;
                return this;
            }
            criteria.add(new StackCriteria(RecipeIngredientRole.INPUT, input, strategy));
            return this;
        }

        @Override
        public RecipeLookup<T> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy) {
            Checks.checkNotNull(strategy, "strategy");
            if (inputs == null || inputs.isEmpty()) {
                return this;
            }
            for (ContentStack<?, ?> input : inputs) {
                if (input == null || input.isEmpty()) {
                    impossible = true;
                    return this;
                }
                criteria.add(new StackCriteria(RecipeIngredientRole.INPUT, input, strategy));
            }
            return this;
        }

        @Override
        public RecipeLookup<T> withOutput(ContentStack<?, ?> output, MatchStrategy strategy) {
            Checks.checkNotNull(strategy, "strategy");
            if (output == null || output.isEmpty()) {
                impossible = true;
                return this;
            }
            criteria.add(new StackCriteria(RecipeIngredientRole.OUTPUT, output, strategy));
            return this;
        }

        @Override
        public RecipeLookup<T> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy) {
            Checks.checkNotNull(strategy, "strategy");
            if (outputs == null || outputs.isEmpty()) {
                return this;
            }
            for (ContentStack<?, ?> output : outputs) {
                if (output == null || output.isEmpty()) {
                    impossible = true;
                    return this;
                }
                criteria.add(new StackCriteria(RecipeIngredientRole.OUTPUT, output, strategy));
            }
            return this;
        }

        @Override
        public RichIterable<T> get() {
            if (impossible) {
                return Lists.immutable.empty();
            }
            IRecipeManager manager = managerOrNull();
            if (manager == null) {
                return Lists.immutable.empty();
            }

            if (criteria.isEmpty()) {
                if (requiredId != null) {
                    T found = findRecipe(requiredId);
                    return found != null && matchesPredicates(found)
                            ? Lists.immutable.of(found)
                            : Lists.immutable.empty();
                }
                List<T> out = new ArrayList<>();
                for (T recipe : allRecipes()) {
                    if (matchesPredicates(recipe)) {
                        out.add(recipe);
                    }
                }
                return Lists.immutable.withAll(out);
            }

            IFocusFactory focusFactory = CirrusJeiPlugin.focusFactoryOrNull();
            List<IFocus<?>> focuses = buildFocusesOrNull(focusFactory);
            if (focuses == null) {
                return fallbackIndexedLookup();
            }

            try {
                // JEI's limitFocus(List) uses OR semantics across focuses; intersect per criterion for exact AND semantics.
                List<T> candidates = null;
                for (IFocus<?> focus : focuses) {
                    List<T> slice = manager.createRecipeLookup(recipeType)
                            .limitFocus(List.of(focus))
                            .get()
                            .toList();
                    if (slice.isEmpty()) {
                        return Lists.immutable.empty();
                    }
                    if (candidates == null) {
                        candidates = slice;
                    } else {
                        Set<T> allowed = Collections.newSetFromMap(new IdentityHashMap<>(slice.size()));
                        allowed.addAll(slice);
                        List<T> intersected = new ArrayList<>();
                        for (T candidate : candidates) {
                            if (allowed.contains(candidate)) {
                                intersected.add(candidate);
                            }
                        }
                        candidates = intersected;
                        if (candidates.isEmpty()) {
                            return Lists.immutable.empty();
                        }
                    }
                }
                if (candidates == null) {
                    return Lists.immutable.empty();
                }

                List<T> out = new ArrayList<>(candidates.size());
                for (T candidate : candidates) {
                    if (requiredId != null && !Objects.equals(requiredId, getId(candidate))) {
                        continue;
                    }
                    if (!matchesPredicates(candidate)) {
                        continue;
                    }
                    out.add(candidate);
                }
                return Lists.immutable.withAll(out);
            } catch (RuntimeException | LinkageError ignored) {
                return fallbackIndexedLookup();
            }
        }

        private boolean matchesPredicates(T recipe) {
            for (Predicate<? super T> predicate : predicates) {
                if (!predicate.test(recipe)) {
                    return false;
                }
            }
            return true;
        }

        private @Nullable List<IFocus<?>> buildFocusesOrNull(@Nullable IFocusFactory focusFactory) {
            if (focusFactory == null) {
                return null;
            }
            List<IFocus<?>> out = new ArrayList<>(criteria.size());
            for (StackCriteria criterion : criteria) {
                if (!criterion.strategy.withIdentity() || criterion.strategy.withAmount()) {
                    return null;
                }
                IFocus<?> focus = toFocus(focusFactory, criterion.role, criterion.stack);
                if (focus == null) {
                    return null;
                }
                out.add(focus);
            }
            return out;
        }

        private @Nullable IFocus<?> toFocus(
            IFocusFactory focusFactory,
            RecipeIngredientRole role,
            ContentStack<?, ?> stack
        ) {
            if (stack.isItem()) {
                ContentStack<Item, ItemStack> item = stack.cast();
                ItemStack copy = item.value().copy();
                copy.setCount(Math.max(1, copy.getCount()));
                return focusFactory.createFocus(role, VanillaTypes.ITEM_STACK, copy);
            }
            if (stack.isFluid()) {
                ContentStack<Fluid, FluidStack> fluid = stack.cast();
                FluidStack copy = fluid.value().copy();
                if (copy.getAmount() <= 0) {
                    copy.setAmount(1000);
                }
                return focusFactory.createFocus(role, NeoForgeTypes.FLUID_STACK, copy);
            }
            return null;
        }

        private RichIterable<T> fallbackIndexedLookup() {
            RecipeLookup<T> indexed = RecipeLookup.ofSystem(JeiCategoryRecipeSystem.this);
            if (requiredId != null) {
                indexed.withId(requiredId);
            }
            for (StackCriteria criterion : criteria) {
                if (criterion.role == RecipeIngredientRole.INPUT) {
                    indexed.withInput(criterion.stack, criterion.strategy);
                } else {
                    indexed.withOutput(criterion.stack, criterion.strategy);
                }
            }
            for (Predicate<? super T> predicate : predicates) {
                indexed.filter(predicate);
            }
            return indexed.get();
        }
    }
}
