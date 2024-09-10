package dev.vfyjxf.calculatorcirrus.api.content;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public interface ContentType<T> {

    String namespace = "content_type";

    static RegistryAccess registryAccess() {
        return switch (FMLEnvironment.dist) {
            case CLIENT -> Minecraft.getInstance().player.registryAccess();
            case DEDICATED_SERVER -> ServerLifecycleHooks.getCurrentServer().registryAccess();
        };
    }

    ResourceLocation getId();

    Class<T> getTypeClass();

    Class<?> getBaseClass();

    /**
     * @return the item of ItemStack, the fluid of FluidStack, etc.
     */
    Object getBase(T content);

    /**
     * @return the id from the content.
     */
    @Nullable
    String getUid(ContentStack<T> content, T value);

    /**
     * @return the owner modid from the content
     */
    @Nullable
    String getModid(ContentStack<T> content, T value);

    boolean isEmpty(ContentStack<T> content, T value);

    T create(ContentStack<T> content, DataComponentPatch components, long amount);

    T copy(ContentStack<T> content, T value);

    T normalize(ContentStack<T> content, T value);

    T wildcard(ContentStack<T> entry, T value);

    default boolean stackable() {
        return true;
    }

    default boolean supportTags() {
        return getTagType() != null;
    }

    @Nullable
    TagType<T, ?> getTagType();

    /**
     * @param content the content
     * @param value   the value
     * @return the amount from the value,return -1 if this tagType is not stackable
     */
    default long getAmount(ContentStack<T> content, T value) {
        if (!stackable()) throw new UnsupportedOperationException("This ContentType is not stackable");
        return getAmount(value);
    }

    long getAmount(T value);

    @Nullable
    default DataComponentMap getComponents(ContentStack<T> content, T value) {
        return null;
    }

    @Nullable
    default ItemStack cheatStack(ContentStack<T> content, T value) {
        return null;
    }

    @Nullable
    T merge(T value1, T value2, MatchContext matchContext);

    boolean matches(T value1, T value2, MatchContext matchContext);

    default boolean matches(ContentStack<T> stack1, ContentStack<T> stack2, MatchContext matchContext) {
        return matches(stack1.value(), stack2.value(), matchContext);
    }

    @Nullable
    ContentSerializer<T> getSerializer();

    default int hash(ContentStack<T> content, T value, MatchContext context) {
        return hash(value, context);
    }

    int hash(T value, MatchContext context);

    Stream<? extends TagKey<?>> getTagsFor(ContentStack<T> content, T value);

    @SuppressWarnings("unchecked")
    default <O> ContentType<O> cast() {
        return (ContentType<O>) this;
    }

    default boolean acceptsNull() {
        return false;
    }

    /**
     * @param value the value
     * @return the toString of the value
     */
    default String getAsString(T value) {
        return value.toString();
    }
}
