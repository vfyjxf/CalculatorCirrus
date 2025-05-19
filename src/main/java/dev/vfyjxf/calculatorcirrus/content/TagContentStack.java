package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagStack;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class TagContentStack<STACK, VALUE> implements TagStack<STACK, VALUE> {

    private final ContentType<STACK> stackType;
    private final TagType<STACK, VALUE> tagType;
    private final TagKey<VALUE> tagKey;
    private RichTooltip append;
    private long amount;
    private MutableList<? extends ContentStack<STACK>> values;

    public TagContentStack(TagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey) {
        this.stackType = tagType.getStackType();
        this.tagType = tagType;
        this.tagKey = tagKey;
        this.amount = 1;
    }

    public TagContentStack(TagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey, long amount) {
        this.stackType = tagType.getStackType();
        this.tagType = tagType;
        this.tagKey = tagKey;
        this.amount = amount;
    }

    @Nullable
    @Override
    public RichTooltip getTooltip(TooltipContext context) {
        return append;
    }

    @Override
    public TagStack<STACK, VALUE> appendTooltip(List<Tooltip> tooltips) {
        append.tooltip(tooltips);
        return this;
    }

    @Override
    public TagStack<STACK, VALUE> tooltip(RichTooltip richTooltip) {
        append = richTooltip;
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = contentType().cheatStack(this, value());
        return ContentStack.create(itemStack);
    }

    @Override
    public TagType<STACK, VALUE> tagType() {
        return tagType;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public ContentStack<STACK> setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public @Unmodifiable MutableList<? extends ContentStack<STACK>> getValues() {
        if (values == null) {
            this.values = tagType.getValues(tagKey);
        }
        return values.asUnmodifiable();
    }

    @Override
    public TagKey<VALUE> getTag() {
        return tagKey;
    }

    @Override
    public STACK value() {
        ContentStack<STACK> any = getValues().getAny();
        if (any == null) {
            throw new IllegalStateException("Tag: " + tagKey + " is empty");
        }
        return any.copyWithAmount(amount).value();
    }

    @Override
    public ContentType<STACK> contentType() {
        return stackType;
    }

    @Override
    public TagStack<STACK, VALUE> copy() {
        return new TagContentStack<>(tagType, tagKey, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagContentStack<?, ?> that = (TagContentStack<?, ?>) o;
        return amount == that.amount && stackType.equals(that.stackType) && tagType.equals(that.tagType) && tagKey.equals(that.tagKey);
    }

    @Override
    public int hashCode() {
        int result = stackType.hashCode();
        result = 31 * result + tagType.hashCode();
        result = 31 * result + tagKey.hashCode();
        result = 31 * result + Long.hashCode(amount);
        return result;
    }

    @Override
    public String toString() {
        return "TagContentStack{" +
                "stackType=" + stackType +
                ", tagType=" + tagType +
                ", values=" + values +
                ", tagKey=" + tagKey +
                ", amount=" + amount +
                '}';
    }
}
