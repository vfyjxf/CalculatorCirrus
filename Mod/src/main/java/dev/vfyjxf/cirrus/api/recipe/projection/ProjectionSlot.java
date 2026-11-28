package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

/**
 * Immutable addressed slot inside a {@link RecipeProjection}, carrying its stable {@link SlotAddress},
 * {@link RecipeContent}, typed {@link ProjectionAttributes}, and GAS-style {@link ProjectionTags}.
 */
public record ProjectionSlot<T extends RecipeContent>(
    SlotAddress address,
    T content,
    ProjectionAttributes attributes,
    ProjectionTags tags
) {
    public ProjectionSlot {
        Checks.checkNotNull(address, "address");
        Checks.checkNotNull(content, "content");
        Checks.checkNotNull(attributes, "attributes");
        Checks.checkNotNull(tags, "tags");
    }

    public static <T extends RecipeContent> ProjectionSlot<T> of(SlotAddress address, T content) {
        return new ProjectionSlot<>(address, content, ProjectionAttributes.empty(), ProjectionTags.empty());
    }

    public static <T extends RecipeContent> ProjectionSlot<T> of(
        SlotAddress address,
        T content,
        ProjectionAttributes attributes,
        ProjectionTags tags
    ) {
        return new ProjectionSlot<>(address, content, attributes, tags);
    }

    public SlotChannel channel() {
        return address.channel();
    }

    public boolean isGridCell() {
        return address.isGridCell();
    }

    public SlotAddress.@Nullable GridCell gridCell() {
        return address instanceof SlotAddress.GridCell cell ? cell : null;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public boolean notEmpty() {
        return content.notEmpty();
    }

    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
        return content.amount();
    }

    public @Range(from = 0, to = 1) float chance() {
        return content.chance();
    }

    public boolean constantly() {
        return content.constantly();
    }

    public boolean mergeable(RecipeContent other) {
        return content.mergeable(other);
    }

    public boolean satisfy(ContentVariant actual) {
        return content.satisfy(actual);
    }

    public boolean hasTag(Namespace tag) {
        return tags.has(tag);
    }

    public boolean hasTagHierarchical(Namespace tag) {
        return tags.hasHierarchical(tag);
    }

    public <V> @Nullable V attribute(ProjectionAttributeKey<V> key) {
        return attributes.find(key);
    }

    public ProjectionSlot<T> withContent(T nextContent) {
        return new ProjectionSlot<>(address, nextContent, attributes, tags);
    }

    public ProjectionSlot<T> withAddress(SlotAddress nextAddress) {
        return new ProjectionSlot<>(nextAddress, content, attributes, tags);
    }

    public <V> ProjectionSlot<T> withAttribute(ProjectionAttributeKey<V> key, V value) {
        return new ProjectionSlot<>(address, content, attributes.with(key, value), tags);
    }

    public ProjectionSlot<T> withoutAttribute(ProjectionAttributeKey<?> key) {
        return new ProjectionSlot<>(address, content, attributes.without(key), tags);
    }

    public ProjectionSlot<T> withAttributes(ProjectionAttributes nextAttributes) {
        return new ProjectionSlot<>(address, content, nextAttributes, tags);
    }

    public ProjectionSlot<T> withTag(Namespace tag) {
        return new ProjectionSlot<>(address, content, attributes, tags.with(tag));
    }

    public ProjectionSlot<T> withoutTag(Namespace tag) {
        return new ProjectionSlot<>(address, content, attributes, tags.without(tag));
    }

    public ProjectionSlot<T> withTags(ProjectionTags nextTags) {
        return new ProjectionSlot<>(address, content, attributes, nextTags);
    }

    @SuppressWarnings("unchecked")
    public ProjectionSlot<T> snapshot() {
        T copied = (T) content.copyWithAmountAndChance(content.amount(), content.chance());
        return new ProjectionSlot<>(address, copied, attributes, tags);
    }
}
