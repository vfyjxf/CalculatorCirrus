package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.TagStack;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RecipeDatabase {

    Component name();


    ContentStack<?> getDefault(IngredientStack ingredientStack);

    default <STACK, VALUE> void putDefaultContent(TagType<VALUE, STACK> tagType, TagKey<VALUE> tagKey, ContentStack<?> contentStack) {
        TagStack<VALUE, STACK> stack = TagStack.create(tagType, tagKey);
        putDefaultContent(IngredientStack.create(stack), contentStack);
    }

    void putDefaultContent(IngredientStack ingredientStack, ContentStack<?> contentStack);

    @Nullable CommonRecipe<?> get(ContentStack<?> target);

    @Nullable CommonRecipe<?> getOrDefault(ContentStack<?> target, @Nullable CommonRecipe<?> recipe);

    void put(ContentStack<?> target, @NotNull CommonRecipe<?> recipe);
}
