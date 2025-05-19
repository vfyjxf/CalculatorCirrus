package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.content.types.EmptyType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("rawtypes")
public class EmptyContentStack implements ContentStack {
    public static final EmptyContentStack INSTANCE = new EmptyContentStack();

    @Override
    public Object value() {
        return EmptyType.EMPTY;
    }

    @Override
    public ContentType contentType() {
        return CirrusContentTypes.EMPTY;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ContentStack setAmount(long amount) {
        return this;
    }

    @Override
    public @Nullable RichTooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public ContentStack tooltip(RichTooltip richTooltip) {
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        return ContentStack.create(ItemStack.EMPTY);
    }

    @Override
    public ContentStack appendTooltip(List tooltips) {
        return this;
    }

    @Override
    public String toString() {
        return "empty";
    }
}
