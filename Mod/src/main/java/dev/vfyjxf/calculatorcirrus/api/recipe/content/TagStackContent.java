package dev.vfyjxf.calculatorcirrus.api.recipe.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentObject;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Range;

public final class TagStackContent<T> implements InputContent {

    private final TagType<T> type;
    private final ContentType<T> contentType;
    private final TagKey<T> tagKey;
    private final ImmutableList<T> values;
    private final long amount;
    private final float chance;

    TagStackContent(TagType<T> type, TagKey<T> tagKey) {
        this.type = type;
        this.contentType = type.contentType();
        this.tagKey = tagKey;
        this.values = type.getTagValues(tagKey).toImmutableList();
        var contentApi = ContentApi.instance();
        var hasStackType = contentApi.hasStackType(contentType);
        this.amount = hasStackType ? contentApi.getStackType(contentType).quantity() : 1;
        this.chance = MAX_CHANCE;
    }

    TagStackContent(TagType<T> type, TagKey<T> tagKey, long amount, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.type = type;
        this.contentType = type.contentType();
        this.tagKey = tagKey;
        this.values = type.getTagValues(tagKey).toImmutableList();
        this.amount = amount;
        this.chance = chance;
    }

    public ContentType<T> contentType() {
        return contentType;
    }

    public TagType<T> type() {
        return type;
    }

    public TagKey<T> tagKey() {
        return tagKey;
    }

    public ImmutableList<T> values() {
        return values;
    }

    @Override
    public boolean isEmpty() {
        return amount <= 0 ||
               !values.anySatisfy(contentType::isEmpty);
    }

    @Override
    public @Range(from = 0, to = Long.MAX_VALUE)  long amount() {
        return amount;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public TagStackContent<T> copy() {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public TagStackContent<T> copyWithAmount(long amount) {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public TagStackContent<T> copyWithChance(float chance) {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public TagStackContent<T> copyWithAmountAndChance(long amount, float chance) {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        return other instanceof TagStackContent<?> o &&
               type == o.type &&
               tagKey == o.tagKey;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean satisfy(ContentVariant actual) {
        T contentObject = (T) switch (actual) {
            case ContentObject<?> object when (object.type() == contentType) -> object.value();
            case ContentStack<?, ?> stack when (stack.contentType() == contentType) -> stack.content();
            default -> null;
        };

        if (contentObject == null) return false;
        for (T value : values) {
            if (contentType.matches(value, contentObject)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "TagStackContent{" +
               "type=" + type +
               ", tagKey=" + tagKey +
               ", amount=" + amount +
               ", chance=" + chance +
               '}';
    }
}
