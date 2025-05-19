package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

@SuppressWarnings({"rawtypes"})
public class EmptyType implements ContentType, ContentSerializer {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(namespace, "empty");
    public static final Object EMPTY = new Object();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Class getTypeClass() {
        return Object.class;
    }

    @Override
    public Class<?> getBaseClass() {
        return Object.class;
    }

    @Override
    public Object getIdentity(Object content) {
        return EMPTY;
    }

    private ContentType getType() {
        return CirrusContentTypes.EMPTY;
    }

    @Override
    public @Nullable String getUid(ContentStack content, Object value) {
        return "empty";
    }

    @Override
    public @Nullable String getModid(ContentStack content, Object value) {
        return Constants.MOD_ID;
    }

    @Override
    public boolean isEmpty(ContentStack content, Object value) {
        return true;
    }

    @Override
    public Object create(ContentStack content, DataComponentPatch components, long amount) {
        return EMPTY;
    }

    @Override
    public Object copy(ContentStack content, Object value) {
        return EMPTY;
    }

    @Override
    public Object copy(Object value) {
        return EMPTY;
    }

    @Override
    public Object normalize(ContentStack content, Object value) {
        return EMPTY;
    }

    @Override
    public Object normalize(Object value) {
        return EMPTY;
    }

    @Override
    public @Nullable TagType getTagType() {
        return null;
    }

    @Override
    public long getAmount(Object value) {
        return 0;
    }

    @Nullable
    @Override
    public Object merge(Object value1, Object value2, MatchContext matchContext) {
        return EMPTY;
    }

    @Override
    public boolean matches(Object value1, Object value2, MatchContext matchContext) {
        return value1 == value2;
    }

    @Override
    public @Nullable ContentSerializer getSerializer() {
        return this;
    }

    @Override
    public int hash(Object value, MatchContext context) {
        return EMPTY.hashCode();
    }

    @Override
    public Stream<? extends TagKey<?>> getTagsFor(ContentStack content, Object value) {
        return Stream.empty();
    }

    @Override
    public CompoundTag save(ContentStack stack, Object value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().getId().toString());
        tag.putString("content", "empty");
        return tag;
    }

    @Nullable
    @Override
    public Object read(CompoundTag tag) {
        if (tag.contains("type") && tag.contains("content")) {
            ResourceLocation loaded = ResourceLocation.parse(tag.getString("type"));
            if (getType().getId().equals(loaded)) {
                return Unit.INSTANCE;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "empty";
    }
}
