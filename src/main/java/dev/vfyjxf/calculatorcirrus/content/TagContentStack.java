package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagStack;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TagContentStack<STACK, VALUE> implements TagStack<STACK, VALUE> {

    private final ContentType<STACK> stackType;
    private final TagType<STACK, VALUE> tagType;
    private final TagKey<VALUE> tagKey;
    private Tooltip append;
    private long amount;
    private ImmutableList<? extends ContentStack<STACK>> values;

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
    public Tooltip getTooltip(TooltipContext context) {
        return append;
    }

    @Override
    public TagStack<STACK, VALUE> appendTooltip(List<Tooltip.Entry> tooltips) {
        append.addEntries(tooltips);
        return this;
    }

    @Override
    public TagStack<STACK, VALUE> tooltip(Tooltip tooltip) {
        append = tooltip;
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
    public MutableList<? extends ContentStack<STACK>> getValues() {
        if (values == null) {
            this.values = tagType.getValues(tagKey).toImmutable();
        }
        return values.toList();
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
}
