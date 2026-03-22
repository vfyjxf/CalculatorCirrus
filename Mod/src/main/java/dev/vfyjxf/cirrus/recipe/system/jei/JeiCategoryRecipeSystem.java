package dev.vfyjxf.cirrus.recipe.system.jei;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.util.TagLookup;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class JeiCategoryRecipeSystem<T> implements RecipeSystem<T> {

    private static final Namespace basedId = Namespace.of("jei", "category");

    public static <T> RecipeSystem<T> create(IRecipeManager manager, IRecipeCategory<T> category) {
        Checks.checkNotNull(manager, "manager");
        Checks.checkNotNull(category, "category");
        var recipeTypeUid = category.getRecipeType().getUid();
        Namespace systemId = basedId.resolve(recipeTypeUid.getNamespace()).resolve(recipeTypeUid.getPath());
        return new JeiCategoryRecipeSystem<>(systemId, manager, category);
    }

    private final Namespace systemId;
    private final IRecipeManager manager;
    private final IRecipeCategory<T> category;

    private JeiCategoryRecipeSystem(Namespace systemId, IRecipeManager manager, IRecipeCategory<T> category) {
        this.systemId = systemId;
        this.manager = manager;
        this.category = category;

    }

    @Override
    public Namespace systemId() {
        return systemId;
    }

    @Override
    public Class<?> basicType() {
        return category.getRecipeType().getRecipeClass();
    }

    @Override
    public @Nullable ResourceLocation getId(T recipe) {
        return category.getRegistryName(recipe);
    }

    @Override
    public @Nullable T findRecipe(String uniqueId) {
        return manager.createRecipeLookup(category.getRecipeType())
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
        return manager.createRecipeLookup(category.getRecipeType())
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
    public RecipeProjection resolveRecipe(T recipe) {
        //TODO:empty or err when recipe can't be resolved
        var focusFactory = CirrusJeiPlugin.focusFactory();
        var recipeLayout = manager.createRecipeLayoutDrawable(category, recipe, focusFactory.getEmptyFocusGroup());
        return recipeLayout.map(layout -> {
            List<InputContent> inputs = new ArrayList<>();
            List<OutputContent> outputs = new ArrayList<>();
            //region collect
            var contentApi = ContentApi.instance();
            for (var slotView : layout.getRecipeSlotsView().getSlotViews()) {
                if (slotView.getRole() == RecipeIngredientRole.INPUT) {
                    var unknownTypeIngredients = slotView.getAllIngredients().toList();
                    //jei的设计让我很为难，因为我把时间和能量单独设为了一种类型，但jei允许能量时间之类的和物品放在一起，因此我选择某种fail fast
                    if (unknownTypeIngredients.size() == 1) {
                        var only = unknownTypeIngredients.getFirst();

                        var stack = resolveIngredient(contentApi, only);
                        if (stack.notEmpty()) {
                            inputs.add(InputContent.fromStack(stack));
                            continue;
                        }

                        var value = resolveValue(contentApi, only);
                        if (value != null) inputs.add(InputContent.fromValue(value));
                        continue;
                    }

                    boolean sameStackType = true;
                    MutableList<ContentStack<?, ?>> stacks = MutableLists.empty();
                    for (var typedIngredient : unknownTypeIngredients) {
                        var stack = resolveIngredient(contentApi, typedIngredient);
                        if (stack.notEmpty()) stacks.add(stack);
                        //we simply ignore value type here because we don't have a good way to handle it
                        sameStackType &= stack.type() == stacks.getFirst().type();
                    }
                    if (stacks.isEmpty()) continue;
                    inputs.add(resolveStackList(sameStackType, stacks));
                }
                if (slotView.getRole() == RecipeIngredientRole.OUTPUT) {
                    var unknownTypeIngredients = slotView.getAllIngredients().toList();
                    //we only support single output
                    if (unknownTypeIngredients.size() != 1) continue;
                    var typedIngredient = unknownTypeIngredients.getFirst();
                    var stack = resolveIngredient(contentApi, typedIngredient);
                    if (stack.notEmpty()) {
                        outputs.add(OutputContent.fromStack(stack));
                        continue;
                    }
                    var value = resolveValue(contentApi, typedIngredient);
                    if (value != null) outputs.add(OutputContent.fromValue(value));
                }
            }
            //endregion
            return RecipeProjection.create(this, recipe, inputs, outputs);
        }).orElseThrow();
    }

    private static <I> ContentStack<?, I> resolveIngredient(ContentApi contentApi, ITypedIngredient<I> ingredient) {
        var stackType = contentApi.findStackTypeForIngredientType(ingredient.getType());
        if (stackType != null) {
            return ContentStack.fromStack(stackType, ingredient.getIngredient());
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
                var tag = TagLookup.findTagMatchStacks(tagType, (Collection) stacks);
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

    @Override
    public @Unmodifiable RichIterable<T> allRecipes() {
        return manager.createRecipeLookup(category.getRecipeType())
                      .get()
                      .collect(Collectors2.toSet());
    }
}
