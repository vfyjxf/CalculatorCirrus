package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
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
        return chance == 0 ||
                amount <= 0 ||
                !values.anySatisfy(contentType::isEmpty);
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public InputContent copy() {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public InputContent copyWithAmount(long amount) {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public InputContent copyWithChance(float chance) {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public InputContent copyWithAmountAndChance(long amount, float chance) {
        return new TagStackContent<>(type, tagKey, amount, chance);
    }

    @Override
    public MutableList<? extends ContentVariant> equivalents() {
        var contentApi = ContentApi.instance();
        if (contentApi.hasStackType(contentType)) {
            StackType<T, ?> stackType = contentApi.getStackType(contentType);
            return values.collect(value -> ContentStack.fromContent(stackType, value, amount))
                    .toList();
        } else {
            return values.collect(value -> ContentValue.fromContent(contentType, value))
                    .toList();
        }
    }

    @Override
    public boolean satisfy(ContentValue<?> value) {
        return value.notEmpty() && contentType == value.type() &&
                values.anySatisfyWith(contentType.cast()::matches, value.value());
    }

    @Override
    public boolean satisfy(ContentStack<?, ?> stack, MatchStrategy strategy) {
        if (stack.isEmpty()) return false;

        var contentApi = ContentApi.instance();
        if (contentApi.hasStackType(contentType)) {
            var stackType = contentApi.getStackType(contentType);
            return stack.type() == stackType &&
                    values.anySatisfyWith(contentType::matches, stack.<T, Object>cast().content());
        } else {
            return contentType == stack.type().contentType() &&
                    values.anySatisfyWith(contentType.cast()::matches, stack.content());
        }
    }

    @Override
    public boolean equivalent(RecipeContent other) {
        if (!(other instanceof TagStackContent<?> content)) return false;

        return type == content.type && tagKey == content.tagKey;
    }
}
