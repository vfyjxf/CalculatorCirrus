package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmptyContentStack implements ContentStack<Unit> {
    public static final EmptyContentStack INSTANCE = new EmptyContentStack();

    @Override
    public Unit value() {
        return Unit.INSTANCE;
    }

    @Override
    public ContentType<Unit> contentType() {
        return CirrusContentTypes.EMPTY;
    }

    @Override
    public @Nullable Tooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public ContentStack<Unit> appendTooltip(List<Tooltip.Entry> tooltips) {
        return this;
    }

    @Override
    public ContentStack<Unit> tooltip(Tooltip tooltip) {
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        return ContentStack.of(ItemStack.EMPTY);
    }
}
