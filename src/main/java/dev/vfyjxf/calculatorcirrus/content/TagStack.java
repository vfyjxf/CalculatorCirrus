package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import dev.vfyjxf.calculatorcirrus.api.content.ITagStack;
import dev.vfyjxf.calculatorcirrus.api.content.ITagType;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagStack<STACK, VALUE> implements ITagStack<STACK, VALUE> {

    private final IContentType<STACK> stackType;
    private final ITagType<STACK, VALUE> tagType;
    private final TagKey<VALUE> tagKey;
    private int amount = 0;

    public TagStack(ITagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey) {
        this.stackType = tagType.getStackType();
        this.tagType = tagType;
        this.tagKey = tagKey;
    }

    public TagStack(ITagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey, long amount) {
        this.stackType = tagType.getStackType();
        this.tagType = tagType;
        this.tagKey = tagKey;
    }

    @Override
    public @Nullable ITooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public IContentStack<STACK> appendTooltip(List<ITooltip.Entry> tooltips) {
        return null;
    }

    @Override
    public IContentStack<STACK> tooltip(ITooltip tooltip) {
        return null;
    }

    @Override
    public IContentStack<ItemStack> cheatsStack() {
        return null;
    }

    @Override
    public ITagType<STACK, VALUE> tagType() {
        return tagType;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public List<VALUE> getValues() {
        return tagType.getTag(tagKey)
                .stream()
                .map(Holder::value)
                .collect(Collectors.toList());
    }

    @Override
    public TagKey<VALUE> getTag() {
        return tagKey;
    }

    @Override
    public STACK value() {
        Stream<VALUE> valueStream = tagType.getTag(tagKey).stream().map(Holder::value);
        return valueStream.findAny()
                .map(it -> tagType.makeStackWithAmount(it, amount))
                .orElseThrow(() -> new IllegalArgumentException("Tag: " + tagKey + " is not known"));
    }

    @Override
    public IContentType<STACK> type() {
        return stackType;
    }
}
