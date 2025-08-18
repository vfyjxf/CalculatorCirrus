package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.neoforged.fml.loading.FMLEnvironment;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

abstract non-sealed class BasicContentValue<T> implements ContentValue<T> {

    private final MutableList<BiConsumer<RichTooltip, TooltipContext>> tooltipConfigurers = Lists.mutable.empty();
    protected final T value;

    BasicContentValue(T value) {this.value = value;}

    @Override
    public T value() {
        return value;
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
    public ContentValue<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        if (FMLEnvironment.dist.isDedicatedServer()) return this;
        this.tooltipConfigurers.add(configurator);
        return this;
    }

    public abstract boolean equals(@Nullable Object o);

    public abstract int hashCode();

}
