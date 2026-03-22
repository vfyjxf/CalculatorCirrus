package dev.vfyjxf.cirrus.api.recipe.system;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cloudlib.api.util.Games;
import dev.vfyjxf.cloudlib.util.Locations;
import mezz.jei.api.helpers.ICodecHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * Recipe system which use {@link RecipeManager}
 *
 * @param <T>
 */
public interface VanillaRecipeSystem<T extends Recipe<?>> extends RecipeSystem<RecipeHolder<T>> {

    /**
     * Based on {@link ICodecHelper#getRecipeHolderCodec()}
     */
    Codec<RecipeHolder<?>> recipeHolderCodec = Codec.lazyInitialized(() -> {
        RecipeManager recipeManager = recipeManager();
        return Codec.either(
            ResourceLocation.CODEC,
            RecordCodecBuilder.<RecipeHolder<?>>create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(RecipeHolder::id),
                Recipe.CODEC.fieldOf("value").forGetter(RecipeHolder::value)
            ).apply(instance, instance.stable(RecipeHolder::new)))
        ).flatXmap(
            either -> either.map(
                id -> recipeManager.byKey(id)
                                   .map(DataResult::success)
                                   .orElse(DataResult.error(() -> "Could not find recipe for id: " + id)),
                recipeHolder -> DataResult.success(recipeHolder)
            ),
            holder -> {
                ResourceLocation id = holder.id();
                var maybeHolder = recipeManager.byKey(id);
                if (maybeHolder.isPresent() && maybeHolder.get().equals(holder)) {
                    return DataResult.success(Either.left(id));
                } else return DataResult.success(Either.right(holder));
            }
        );
    });

    static RecipeManager recipeManager() {
        return Games.recipeManager();
    }

    @Nullable
    static <T extends Recipe<?>> VanillaRecipeSystem<T> byRecipeType(RecipeType<T> recipeType) {
        return RecipeApi.instance().findVanillaSystem(recipeType);
    }

    RecipeType<T> recipeType();

    @Override
    default Class<?> basicType() {
        return RecipeHolder.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    default Codec<RecipeHolder<T>> codec() {
        return (Codec) recipeHolderCodec;
    }

    @Override
    default boolean supports(RecipeHolder<T> recipe) {
        return recipeType() == recipe.value().getType();
    }

    @Override
    default ResourceLocation getId(RecipeHolder<T> recipe) {
        return recipe.id();
    }

    @Override
    default boolean sameRecipe(RecipeHolder<T> r1, RecipeHolder<T> r2) {
        return r1.value().getType() == r2.value().getType() &&
               r1.id().equals(r2.id());
    }

    @Override
    default int hash(RecipeHolder<T> recipe) {
        return recipe.id().hashCode();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    default RecipeHolder<T> findRecipe(ResourceLocation registryName) {
        RecipeType<T> recipeType = recipeType();
        return (RecipeHolder<T>) recipeManager().byKey(registryName)
                                                .filter(recipeHolder -> recipeHolder.value().getType() == recipeType)
                                                .orElse(null);
    }

    @Nullable
    @Override
    default RecipeHolder<T> findRecipe(String uniqueId) {
        return findRecipe(Locations.parse(uniqueId));
    }
}
