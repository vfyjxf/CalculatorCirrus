package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

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
        this.values = type.getTagValuesList(tagKey);
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
        this.values = type.getTagValuesList(tagKey);
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
        return amount <= 0 || values.allSatisfy(contentType::isEmpty);
    }

    @Override
    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
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
    public boolean isEnumerable() {
        return true;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends ContentVariant> enumerate() {
        return values.collect(value -> ContentKind.fromContent(contentType, value));
    }

    @Override
    public boolean preserveOriginalShortageSemantics() {
        return true;
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
        return other instanceof TagStackContent<?> o && type == o.type && tagKey == o.tagKey;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean satisfy(ContentVariant actual) {
        T contentKind = (T) switch (actual) {
            case ContentKind<?> kind when (kind.type() == contentType) -> kind.value();
            case ContentStack<?, ?> stack when (stack.contentType() == contentType) -> stack.content();
            default -> null;
        };

        if (contentKind == null) return false;
        for (T value : values) {
            if (contentType.matches(value, contentKind)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "TagStackContent{" + "type=" + type + ", tagKey=" + tagKey + ", amount=" + amount + ", chance=" + chance
                + '}';
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.TAG_STACK;
    }
}
