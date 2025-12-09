package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

record TypedContentObject<T>(
    ContentType<T> type,
    T value,
    MutableList<BiConsumer<RichTooltip, TooltipContext>> tooltipConfigurers
) implements ContentObject<T> {

    TypedContentObject(ContentType<T> type, T value) {
        this(type, value, MutableLists.empty());
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
    public ContentObject<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        if (FMLEnvironment.dist.isDedicatedServer()) return this;
        this.tooltipConfigurers.add(configurator);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;

        TypedContentObject<?> that = (TypedContentObject<?>) object;
        return type.equals(that.type) && type.matches(value, (T) that.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + type.hash(value);
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentValue{" +
               "type=" + type +
               ", value=" + value +
               '}';
    }
}


final class EmptyContentObject<T> implements ContentObject<T> {

    @SuppressWarnings("rawtypes")
    static final ContentObject instance = new EmptyContentObject<>();

    @Override
    public @Nullable ResourceLocation getLocation() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public @Nullable String getNamespace() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean notEmpty() {
        return false;
    }

    @Override
    public boolean matches(ContentObject<?> other, MatchStrategy strategy) {
        return this == other;
    }

    @Override
    public ContentObject<T> withTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public ContentType<T> type() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public T value() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public ContentObject<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {
        throw new UnsupportedOperationException("This operation is not supported for an empty content value.");
    }

    @Override
    public @Nullable RichTooltip getTooltip(TooltipContext context) {
        return null;
    }
}