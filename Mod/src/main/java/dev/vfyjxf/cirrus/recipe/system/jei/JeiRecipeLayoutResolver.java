package dev.vfyjxf.cirrus.recipe.system.jei;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.util.TagLookup;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureCapture;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JeiRecipeLayoutResolver {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Database");
    private static final Set<LayoutIgnoreClassKey> ignoredLayoutClasses = ConcurrentHashMap.newKeySet();
    private static final Set<LayoutIgnoreRecipeKey> ignoredLayoutRecipes = ConcurrentHashMap.newKeySet();

    private JeiRecipeLayoutResolver() {
    }

    public static void clearIgnoredLayouts() {
        ignoredLayoutClasses.clear();
        ignoredLayoutRecipes.clear();
    }

    public static <R> ResolveResult resolve(RecipeSystem<R> system, R recipe, RecipeType<R> recipeType) {
        IRecipeManager manager = CirrusJeiPlugin.recipeManagerOrNull();
        if (manager == null || CirrusJeiPlugin.focusFactoryOrNull() == null) {
            return ResolveResult.unsupported();
        }
        return resolve(system, recipe, manager, recipeType);
    }

    public static <R> ResolveResult resolve(RecipeSystem<R> system, R recipe, IRecipeManager manager, RecipeType<R> recipeType) {
        try {
            RoutingRecipeView routingView = routingView(system, recipe, manager, recipeType);
            if (routingView == null) {
                return ResolveResult.failed(ResolveError.invalidRecipe);
            }
            return ResolveResult.resolved(RecipeProjection.create(
                system,
                recipe,
                iterableToList(routingView.inputs()),
                iterableToList(routingView.outputs())
            ));
        } catch (RuntimeException | LinkageError exception) {
            ResourceLocation recipeId = safeRecipeId(system, recipe);
            rememberLayoutFailure(recipeType, recipe, recipeId);
            logger.warn(
                "Failed to resolve JEI recipe layout for database projection: recipeType={}, recipe={}, recipeClass={}, exception={}, message={}",
                recipeType.getUid(),
                recipeId,
                effectiveRecipeClassName(recipe),
                exception.getClass().getName(),
                exception.getMessage(),
                exception
            );
            RecipeDatabaseFailureCapture.record(system, recipe, exception);
            return ResolveResult.unsupported();
        }
    }

    public static <R> @Nullable RoutingRecipeView routingView(
        RecipeSystem<R> system,
        R recipe,
        IRecipeManager manager,
        RecipeType<R> recipeType
    ) {
        var category = manager.getRecipeCategory(recipeType);
        ResourceLocation recipeId = safeRegistryName(system, category, recipe);
        if (shouldSkipLayout(recipeType, category, recipe, recipeId)) {
            return null;
        }
        var focusFactory = CirrusJeiPlugin.focusFactoryOrNull();
        if (focusFactory == null) {
            return null;
        }
        var recipeLayout = manager.createRecipeLayoutDrawable(category, recipe, focusFactory.getEmptyFocusGroup());
        if (recipeLayout.isEmpty()) {
            return null;
        }

        List<InputContent> inputs = new ArrayList<>();
        List<OutputContent> outputs = new ArrayList<>();
        var contentApi = ContentApi.instance();
        for (var slotView : recipeLayout.get().getRecipeSlotsView().getSlotViews()) {
            if (slotView.getRole() == RecipeIngredientRole.INPUT) {
                addInput(inputs, contentApi, slotView.getAllIngredients().toList());
            } else if (slotView.getRole() == RecipeIngredientRole.OUTPUT) {
                addOutputs(outputs, contentApi, slotView.getAllIngredients().toList());
            }
        }

        if (inputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        return new RoutingRecipeView(inputs, outputs);
    }

    private static <T> List<T> iterableToList(Iterable<? extends T> values) {
        if (values instanceof List<? extends T> list) {
            return List.copyOf(list);
        }
        ArrayList<T> out = new ArrayList<>();
        for (T value : values) {
            out.add(value);
        }
        return out;
    }

    public static <R> boolean shouldSkipLayout(
        RecipeType<R> recipeType,
        IRecipeCategory<R> category,
        R recipe,
        @Nullable ResourceLocation recipeId
    ) {
        LayoutIgnoreClassKey classKey = LayoutIgnoreClassKey.create(recipeType, recipe);
        if (ignoredLayoutClasses.contains(classKey)) {
            return true;
        }
        LayoutIgnoreRecipeKey recipeKey = LayoutIgnoreRecipeKey.create(recipeType, recipe, recipeId);
        if (ignoredLayoutRecipes.contains(recipeKey)) {
            return true;
        }
        try {
            if (!category.isHandled(recipe)) {
                rememberLayoutSkip(recipeType, recipe, recipeId);
                return true;
            }
            return false;
        } catch (RuntimeException | LinkageError exception) {
            rememberLayoutSkip(recipeType, recipe, recipeId);
            logger.warn(
                "Failed to check JEI recipe handling for database projection: recipeType={}, recipe={}, recipeClass={}, exception={}, message={}",
                recipeType.getUid(),
                recipeId,
                effectiveRecipeClassName(recipe),
                exception.getClass().getName(),
                exception.getMessage(),
                exception
            );
            RecipeDatabaseFailureCapture.record(exception);
            return true;
        }
    }

    public static <R> void rememberLayoutFailure(
        RecipeType<R> recipeType,
        R recipe,
        @Nullable ResourceLocation recipeId
    ) {
        rememberLayoutSkip(recipeType, recipe, recipeId);
    }

    private static <R> void rememberLayoutSkip(
        RecipeType<R> recipeType,
        R recipe,
        @Nullable ResourceLocation recipeId
    ) {
        if (recipeId == null) {
            ignoredLayoutClasses.add(LayoutIgnoreClassKey.create(recipeType, recipe));
        }
        ignoredLayoutRecipes.add(LayoutIgnoreRecipeKey.create(recipeType, recipe, recipeId));
    }

    private static <R> @Nullable ResourceLocation safeRecipeId(RecipeSystem<R> system, R recipe) {
        try {
            return system.getId(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static <R> @Nullable ResourceLocation safeRegistryName(
        RecipeSystem<R> system,
        IRecipeCategory<R> category,
        R recipe
    ) {
        try {
            ResourceLocation id = category.getRegistryName(recipe);
            if (id != null) {
                return id;
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        try {
            return system.getId(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private record LayoutIgnoreClassKey(ResourceLocation recipeType, String recipeClass) {
        private static LayoutIgnoreClassKey create(RecipeType<?> recipeType, Object recipe) {
            return new LayoutIgnoreClassKey(recipeType.getUid(), effectiveRecipeClassName(recipe));
        }
    }

    private record LayoutIgnoreRecipeKey(ResourceLocation recipeType, String recipeKey) {
        private static LayoutIgnoreRecipeKey create(
            RecipeType<?> recipeType,
            Object recipe,
            @Nullable ResourceLocation recipeId
        ) {
            String key = recipeId == null
                ? effectiveRecipeClassName(recipe) + "#" + System.identityHashCode(recipe)
                : recipeId.toString();
            return new LayoutIgnoreRecipeKey(recipeType.getUid(), key);
        }
    }

    private static String effectiveRecipeClassName(Object recipe) {
        if (recipe instanceof RecipeHolder<?> holder) {
            return holder.value().getClass().getName();
        }
        return recipe.getClass().getName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MatchStrategy resolveMatchStrategy(ITypedIngredient<?> ingredient) {
        var subtypeManager = CirrusJeiPlugin.subtypeManager();
        var type = ingredient.getType();
        if (type instanceof IIngredientTypeWithSubtypes<?,?>) {
            var subtypeType = (IIngredientTypeWithSubtypes) type;
            var value = ingredient.getIngredient();
            return subtypeManager.hasSubtypes(subtypeType, value)
                ? MatchStrategy.identityAndComponent
                : MatchStrategy.identity;
        }
        return MatchStrategy.identityAndComponent;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MatchStrategy resolveMatchStrategy(ResolvedIngredient<?> ingredient) {
        var subtypeManager = CirrusJeiPlugin.subtypeManager();
        var type = ingredient.ingredient().getType();
        if (type instanceof IIngredientTypeWithSubtypes<?,?>) {
            return subtypeManager.hasSubtypes((IIngredientTypeWithSubtypes) type, ingredient.value())
                ? MatchStrategy.identityAndComponent
                : MatchStrategy.identity;
        }
        return MatchStrategy.identityAndComponent;
    }

    private static void addInput(
        List<InputContent> out,
        ContentApi contentApi,
        List<? extends ITypedIngredient<?>> ingredients
    ) {
        if (ingredients.isEmpty()) {
            return;
        }
        if (ingredients.size() == 1) {
            ResolvedIngredient<?> only = ResolvedIngredient.of(ingredients.getFirst());
            var strategy = resolveMatchStrategy(only);
            ContentStack<?, ?> stack = resolveIngredient(contentApi, only);
            if (stack.notEmpty()) {
                InputContent input = InputContent.fromStack(stack, strategy);
                if (!input.isEmpty()) {
                    out.add(input);
                }
                return;
            }
            ContentValue<?> value = resolveValue(contentApi, only);
            if (value != null) {
                InputContent input = InputContent.fromValue(value);
                if (!input.isEmpty()) {
                    out.add(input);
                }
            }
            return;
        }

        boolean sameStackType = true;
        MutableList<ContentStack<?, ?>> stacks = MutableLists.empty();
        for (ITypedIngredient<?> ingredient : ingredients) {
            ContentStack<?, ?> stack = resolveIngredient(contentApi, ResolvedIngredient.of(ingredient));
            if (stack.notEmpty()) {
                if (stacks.notEmpty() && stack.type() != stacks.getFirst().type()) {
                    sameStackType = false;
                }
                stacks.add(stack);
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        InputContent input = resolveStackList(sameStackType, stacks);
        if (!input.isEmpty()) {
            out.add(input);
        }
    }

    private static void addOutputs(
        List<OutputContent> out,
        ContentApi contentApi,
        List<? extends ITypedIngredient<?>> ingredients
    ) {
        for (ITypedIngredient<?> ingredient : ingredients) {
            ResolvedIngredient<?> resolved = ResolvedIngredient.of(ingredient);
            var strategy = resolveMatchStrategy(resolved);
            ContentStack<?, ?> stack = resolveIngredient(contentApi, resolved);
            if (stack.notEmpty()) {
                OutputContent output = OutputContent.fromStack(stack, strategy);
                if (!output.isEmpty()) {
                    out.add(output);
                }
                continue;
            }
            ContentValue<?> value = resolveValue(contentApi, ingredient);
            if (value != null) {
                OutputContent output = OutputContent.fromValue(value);
                if (!output.isEmpty()) {
                    out.add(output);
                }
            }
        }
    }

    private static <I> ContentStack<?, I> resolveIngredient(ContentApi contentApi, ITypedIngredient<I> ingredient) {
        var stackType = contentApi.findStackTypeForIngredientType(ingredient.getType());
        if (stackType != null) {
            return ContentStack.fromStack(stackType, ingredient.getIngredient());
        }
        return ContentStack.empty();
    }

    @SuppressWarnings("unchecked")
    private static <I> ContentStack<?, I> resolveIngredient(ContentApi contentApi, ResolvedIngredient<I> ingredient) {
        StackType<?, I> stackType = contentApi.findStackTypeForIngredientType(ingredient.ingredient().getType());
        if (stackType != null) {
            return (ContentStack<?, I>) ContentStack.fromStack(stackType, ingredient.value());
        }
        return ContentStack.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static InputContent resolveStackList(boolean sameType, MutableList<? extends ContentStack<?, ?>> stacks) {
        var first = stacks.getFirst();
        long maybeAmount = first.amount();
        boolean allSameAmount = stacks.allSatisfy(stack -> stack.amount() == maybeAmount);
        long amount = allSameAmount ? maybeAmount : 1;
        if (sameType) {
            TagType tagType = first.type().tagType();
            if (tagType != null) {
                TagKey<?> tag = TagLookup.findTagMatchStacks(tagType, (Collection) stacks);
                if (tag != null) return InputContent.fromTag(tagType, tag, amount);
            }
        }
        return InputContent.fromStackList(stacks, amount);
    }

    private static <S> @Nullable ContentValue<?> resolveValue(ContentApi contentApi, ITypedIngredient<S> ingredient) {
        var bridge = contentApi.findValueTypeBridge(ingredient.getType());
        if (bridge == null) return null;
        return bridge.resolve(ingredient.getIngredient());
    }

    private static <S> @Nullable ContentValue<?> resolveValue(ContentApi contentApi, ResolvedIngredient<S> ingredient) {
        var bridge = contentApi.findValueTypeBridge(ingredient.ingredient().getType());
        if (bridge == null) return null;
        return bridge.resolve(ingredient.value());
    }

    @SuppressWarnings("unchecked")
    private record ResolvedIngredient<I>(ITypedIngredient<I> ingredient, I value) {
        private static <I> ResolvedIngredient<I> of(ITypedIngredient<?> ingredient) {
            ITypedIngredient<I> typed = (ITypedIngredient<I>) ingredient;
            return new ResolvedIngredient<>(typed, typed.getIngredient());
        }
    }
}
