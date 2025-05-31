package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

@ApiStatus.Internal
public class EmptyStackContentType implements StackContentType<Object>, ContentSerializer<Object> {

    public static final EmptyStackContentType INSTANCE = new EmptyStackContentType();
    private static final ResourceLocation ID = Locations.of(namespace, "empty");
    private static final ResourceLocation LOCATION = Locations.of("empty_content", "empty");


    public static ContentStack<?> empty() {
        return EmptyContentStack.INSTANCE;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    public Class<Object> valueType() {
        return Object.class;
    }

    @Override
    public @Nullable TagType<?, Object> tagType() {
        return null;
    }

    @Override
    public Class<?> baseType() {
        return Object.class;
    }

    @Override
    public Object getIdentity(@NotNull Object content) {
        return EmptyContentStack.EMPTY;
    }

    @Override
    public Object create(Object value, long amount, DataComponentPatch componentPatch) {
        return EmptyContentStack.EMPTY;
    }

    @Override
    public @Nullable ResourceLocation getLocation(Object value) {
        return LOCATION;
    }

    @Override
    public boolean isEmpty(Object value) {
        return true;
    }

    @Override
    public Object copy(Object value) {
        return EmptyContentStack.EMPTY;
    }

    @Override
    public Object normalize(Object value) {
        return EmptyContentStack.EMPTY;
    }

    @Override
    public Object wildcard(Object value) {
        return EmptyContentStack.EMPTY;
    }

    @Override
    public @Nullable DataComponentMap getComponents(Object value) {
        return null;
    }

    @Override
    public DataComponentPatch getComponentsPatch(Object value) {
        return DataComponentPatch.EMPTY;
    }

    @Override
    public boolean matches(Object value1, Object value2, MatchStrategy strategy) {
        return value1 == EmptyContentStack.EMPTY && value2 == EmptyContentStack.EMPTY;
    }

    @Override
    public @Nullable ContentSerializer<Object> getSerializer() {
        return this;
    }

    @Override
    public int hash(Object value, MatchStrategy strategy) {
        return EmptyContentStack.EMPTY.hashCode();
    }

    @Override
    public long maxAmount() {
        return 0;
    }

    @Override
    public long getAmount(Object value) {
        return 0;
    }

    @Override
    public long mergeDiff(Object value1, Object value2, MatchStrategy strategy) {
        return 0;
    }

    @Override
    public Stream<? extends TagKey<?>> getTags(Object value) {
        return Stream.empty();
    }

    @Override
    public ItemStack cheatStack(Object value) {
        return ItemStack.EMPTY;
    }

    @Override
    public CompoundTag save(ContentStack<Object> stack, Object value) {
        return new CompoundTag();
    }

    @Override
    public @Nullable Object read(CompoundTag tag) {
        return null;
    }

}
