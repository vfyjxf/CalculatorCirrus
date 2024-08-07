package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.content.types.EmptyContentType;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmptyContentStack implements IContentStack<Unit> {
    public static final EmptyContentStack INSTANCE = new EmptyContentStack();

    @Override
    public Unit value() {
        return Unit.INSTANCE;
    }

    @Override
    public IContentType<Unit> type() {
        return CirrusContentTypes.EMPTY;
    }

    @Override
    public @Nullable ITooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public IContentStack<Unit> appendTooltip(List<ITooltip.Entry> tooltips) {
        return this;
    }

    @Override
    public IContentStack<Unit> tooltip(ITooltip tooltip) {
        return this;
    }

    @Override
    public IContentStack<ItemStack> cheatsStack() {
        return IContentStack.of(ItemStack.EMPTY);
    }
}
