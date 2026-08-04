package dev.vfyjxf.cirrus.recipe.system.jei;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeClaim;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class JeiCategoryRecipeSystem<T> implements RecipeSystem<T> {

    private static final Namespace basedId = Namespace.of("jei", "category");

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
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return null;
        }
        return manager.createRecipeLookup(recipeType)
            .get()
            .filter(r -> {
                var id = getId(r);
                if (id == null) return false;
                return Objects.equals(id.toString(), uniqueId);
            })
            .findAny()
            .orElse(null);
    }

    @Override
    public @Nullable T findRecipe(ResourceLocation registryName) {
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return null;
        }
        return manager.createRecipeLookup(recipeType)
            .get()
            .filter(r -> Objects.equals(getId(r), registryName))
            .findAny()
            .orElse(null);
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
    public RoutingRecipeView routingView(ResolveContext context, T recipe) {
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return null;
        }
        return JeiRecipeLayoutResolver.routingView(this, recipe, manager, recipeType);
    }

    @Override
    public @Unmodifiable RichIterable<T> allRecipes() {
        IRecipeManager manager = managerOrNull();
        if (manager == null) {
            return Lists.immutable.empty();
        }
        return manager.createRecipeLookup(recipeType)
            .get()
            .collect(Collectors2.toSet());
    }

    @Override
    public RecipeLookup<T> createLookup() {
        return new Lookup();
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

    private final class Lookup implements RecipeLookup<T> {
        private Predicate<T> predicate = ignored -> true;
        private final ArrayList<IFocus<?>> focuses = new ArrayList<>();
        private boolean impossible;

        @Override
        public RecipeLookup<T> filter(Predicate<T> predicate) {
            this.predicate = this.predicate.and(predicate);
            return this;
        }

        @Override
        public RecipeLookup<T> withInput(ContentStack<?, ?> input, MatchStrategy strategy) {
            addFocus(RecipeIngredientRole.INPUT, input);
            return this;
        }

        @Override
        public RecipeLookup<T> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy) {
            if (inputs != null) {
                for (ContentStack<?, ?> input : inputs) {
                    addFocus(RecipeIngredientRole.INPUT, input);
                }
            }
            return this;
        }

        @Override
        public RecipeLookup<T> withOutput(ContentStack<?, ?> output, MatchStrategy strategy) {
            addFocus(RecipeIngredientRole.OUTPUT, output);
            return this;
        }

        @Override
        public RecipeLookup<T> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy) {
            if (outputs != null) {
                for (ContentStack<?, ?> output : outputs) {
                    addFocus(RecipeIngredientRole.OUTPUT, output);
                }
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
            try {
                var lookup = manager.createRecipeLookup(recipeType);
                if (!focuses.isEmpty()) {
                    lookup.limitFocus(List.copyOf(focuses));
                }
                return Lists.immutable.withAll(lookup.get().filter(predicate).toList());
            } catch (RuntimeException | LinkageError ignored) {
                return Lists.immutable.empty();
            }
        }

        private void addFocus(RecipeIngredientRole role, ContentStack<?, ?> stack) {
            if (stack == null || stack.isEmpty()) {
                impossible = true;
                return;
            }
            if (!stack.isItem()) {
                impossible = true;
                return;
            }
            IFocusFactory focusFactory = CirrusJeiPlugin.focusFactoryOrNull();
            if (focusFactory == null) {
                impossible = true;
                return;
            }
            ContentStack<Item, ItemStack> item = stack.cast();
            ItemStack copy = item.value().copy();
            copy.setCount(Math.max(1, copy.getCount()));
            focuses.add(focusFactory.createFocus(role, VanillaTypes.ITEM_STACK, copy));
        }
    }

}
