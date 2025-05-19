package dev.vfyjxf.calculatorcirrus.api.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.TagStack;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import dev.vfyjxf.calculatorcirrus.api.recipes.ingredients.IngredientStack;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RecipeDatabase {

    Component name();


    ContentStack<?> getDefault(IngredientStack ingredientStack);

    default <STACK, VALUE> void putDefaultContent(TagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey, ContentStack<?> contentStack) {
        TagStack<STACK, VALUE> stack = ContentStack.create(tagType, tagKey);
        putDefaultContent(IngredientStack.create(stack), contentStack);
    }

    void putDefaultContent(IngredientStack ingredientStack, ContentStack<?> contentStack);

    @Nullable CommonRecipe<?> get(ContentStack<?> target);

    @Nullable CommonRecipe<?> getOrDefault(ContentStack<?> target, @Nullable CommonRecipe<?> recipe);

    void put(ContentStack<?> target, @NotNull CommonRecipe<?> recipe);
}
