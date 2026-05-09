package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public interface RecipeContentType<C extends RecipeContent> {

    RecipeContentType<EmptyRecipeContent> EMPTY = builtin("empty");
    RecipeContentType<StackContent<?, ?>> STACK = builtin("stack");
    RecipeContentType<ValueContent<?>> VALUE = builtin("value");
    RecipeContentType<TagStackContent<?>> TAG_STACK = builtin("tag_stack");
    RecipeContentType<StackListContent> STACK_LIST = builtin("stack_list");
    RecipeContentType<TypedStackListContent<?, ?>> TYPED_STACK_LIST = builtin("typed_stack_list");
    RecipeContentType<ComplexContent> COMPLEX = builtin("complex");

    Namespace typeId();

    @SuppressWarnings("unchecked")
    static <C extends RecipeContent> RecipeContentType<C> of(RecipeContent content) {
        Checks.checkNotNull(content, "content");
        return (RecipeContentType<C>) switch (content) {
            case EmptyRecipeContent ignored -> EMPTY;
            case StackContent<?, ?> ignored -> STACK;
            case ValueContent<?> ignored -> VALUE;
            case TagStackContent<?> ignored -> TAG_STACK;
            case StackListContent ignored -> STACK_LIST;
            case TypedStackListContent<?, ?> ignored -> TYPED_STACK_LIST;
            case ComplexContent ignored -> COMPLEX;
        };
    }

    private static <C extends RecipeContent> RecipeContentType<C> builtin(String path) {
        return new BuiltinRecipeContentType<>(CirrusNamespaces.ofMod("recipe_content/" + path));
    }
}

record BuiltinRecipeContentType<C extends RecipeContent>(Namespace typeId) implements RecipeContentType<C> {}
