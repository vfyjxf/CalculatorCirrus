package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.function.BiConsumer;

final class TagContentStack<VALUE, STACK> implements TagStack<VALUE, STACK> {

    private final TagType<VALUE, STACK> tagType;
    private final TagKey<VALUE> tagKey;
    private long amount;
    private final MutableList<BiConsumer<RichTooltip, TooltipContext>> tooltipConfigurers = Lists.mutable.empty();

    private MutableList<? extends VALUE> values;
    private MutableList<? extends ContentStack<STACK>> stacks;

    public TagContentStack(TagType<VALUE, STACK> tagType, TagKey<VALUE> tagKey) {
        this(tagType, tagKey, 1);
    }

    public TagContentStack(TagType<VALUE, STACK> tagType, TagKey<VALUE> tagKey, long amount) {
        this.tagType = tagType;
        this.tagKey = tagKey;
        this.amount = amount;
    }

    @Nullable
    @Override
    public RichTooltip getTooltip(TooltipContext context) {
        if (FMLEnvironment.dist.isDedicatedServer()) return null;
        RichTooltip richTooltip = RichTooltip.create();
        this.configureTooltip(richTooltip, context);
        return richTooltip;
    }

    @Override
    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {
        for (var configurer : tooltipConfigurers) {
            configurer.accept(tooltip, context);
        }
    }

    @Override
    public ContentStack<STACK> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        if (FMLEnvironment.dist.isDedicatedServer()) return this;
        this.tooltipConfigurers.add(configurator);
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = contentType().cheatStack(this, value());
        return ContentStack.create(itemStack);
    }

    @Override
    public TagType<VALUE, STACK> tagType() {
        return tagType;
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public ContentStack<STACK> setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public @Unmodifiable MutableList<? extends ContentStack<STACK>> getAsStacks() {
        if (stacks == null) {
            this.stacks = tagType.getAsStacks(tagKey);
        }
        return stacks.asUnmodifiable();
    }

    @Override
    public @Unmodifiable MutableList<? extends VALUE> getValues() {
        if (values == null) {
            this.values = tagType.getValues(tagKey);
        }
        return values.asUnmodifiable();
    }

    @Override
    public TagKey<VALUE> tagKey() {
        return tagKey;
    }

    @Override
    public STACK value() {
        ContentStack<STACK> any = getAsStacks().getAny();
        if (any == null) {
            throw new IllegalStateException("Tag: " + tagKey + " is empty");
        }
        return any.copyWithAmount(amount).value();
    }

    @Override
    public StackContentType<STACK> contentType() {
        return tagType.stackType();
    }

    @Override
    public TagStack<VALUE, STACK> copy() {
        return new TagContentStack<>(tagType, tagKey, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagContentStack<?, ?> that = (TagContentStack<?, ?>) o;
        return tagType.equals(that.tagType) &&
                tagKey.equals(that.tagKey) &&
                amount == that.amount;
    }

    @Override
    public int hashCode() {
        int result = tagType.hashCode();
        result = 31 * result + tagKey.hashCode();
        result = 31 * result + Long.hashCode(amount);
        return result;
    }

    @Override
    public String toString() {
        return "TagContentStack{" +
                "tagType=" + tagType +
                ", tagKey=" + tagKey +
                ", amount=" + amount +
                '}';
    }
}
