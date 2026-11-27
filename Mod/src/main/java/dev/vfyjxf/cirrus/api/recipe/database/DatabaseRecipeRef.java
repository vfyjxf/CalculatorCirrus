package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

public record DatabaseRecipeRef(Namespace system, ResourceLocation recipe) {
    public static final Codec<DatabaseRecipeRef> codec = RecordCodecBuilder.create(
        instance -> instance.group(
            Namespace.codec.fieldOf("system").forGetter(DatabaseRecipeRef::system),
            ResourceLocation.CODEC.fieldOf("recipe").forGetter(DatabaseRecipeRef::recipe)
        ).apply(instance, DatabaseRecipeRef::new)
    );

    public DatabaseRecipeRef {
        Checks.checkNotNull(system, "system");
        Checks.checkNotNull(recipe, "recipe");
    }

    public static @Nullable DatabaseRecipeRef fromEntry(RecipeEntry<?> entry) {
        if (entry == null || entry.id() == null) {
            return null;
        }
        return new DatabaseRecipeRef(entry.systemId(), entry.id());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public @Nullable RecipeEntry<?> resolve(RecipeApi api) {
        if (api == null) {
            return null;
        }
        RecipeSystem system = api.findSystem(system());
        if (system == null) {
            return null;
        }
        Object value = system.findRecipe(recipe());
        if (value == null) {
            value = system.findRecipe(recipe().toString());
        }
        if (value == null || !system.basicType().isInstance(value)) {
            return null;
        }
        return RecipeEntry.of(system, value);
    }
}
