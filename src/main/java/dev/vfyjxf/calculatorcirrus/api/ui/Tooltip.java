package dev.vfyjxf.calculatorcirrus.api.ui;

import com.mojang.datafixers.util.Either;
import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.ui.ListTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public interface Tooltip {

    static Entry entry(Component text) {
        return new TooltipEntry(text);
    }

    static Entry entry(TooltipComponent text) {
        return new TooltipEntry(text);
    }

    static Tooltip create(@Nullable Point mousePos, List<Component> texts) {
        return from(mousePos, texts.stream().map(Tooltip::entry).toList());
    }

    static Tooltip create(@Nullable Point mousePos, Component... texts) {
        return create(mousePos, List.of(texts));
    }

    static Tooltip create(List<Component> texts) {
        return create(null, texts);
    }

    static Tooltip create(Component... texts) {
        return create(List.of(texts));
    }

    static Tooltip from(@Nullable Point mousePos, List<Entry> entries) {
        return new ListTooltip(mousePos, entries);
    }

    static Tooltip from(@Nullable Point mousePos, Entry... entries) {
        return from(mousePos, List.of(entries));
    }

    static Tooltip from(List<Entry> entries) {
        return from(null, entries);
    }

    static Tooltip from(Entry... entries) {
        return from(List.of(entries));
    }

    int getPosX();

    int getPosY();

    MutableList<Entry> entries();

    default MutableList<Either<FormattedText, TooltipComponent>> guiEntries() {
        MutableList<Either<FormattedText, TooltipComponent>> result = Lists.mutable.empty();
        for (Entry entry : entries()) {
            if (entry.isText()) result.add(Either.left(entry.asText()));
            else result.add(Either.right(entry.asComponent()));
        }
        return result;
    }

    Tooltip add(Component text);

    Tooltip add(TooltipComponent component);

    Tooltip add(Supplier<Component> supplier);

    default Tooltip addAll(TooltipComponent... components) {
        for (TooltipComponent component : components) {
            add(component);
        }
        return this;
    }

    default Tooltip addAll(Component... text) {
        for (Component component : text) {
            add(component);
        }
        return this;
    }

    default Tooltip addAllTooltipComponents(Iterable<TooltipComponent> text) {
        for (TooltipComponent component : text) {
            add(component);
        }
        return this;
    }

    default Tooltip addAllTexts(Iterable<Component> text) {
        for (Component component : text) {
            add(component);
        }
        return this;
    }

    Tooltip ofAll(Tooltip other);

    Tooltip copy();

    @Nullable
    TooltipStack<?> stack();

    @Contract("_ -> this")
    Tooltip setContextStack(@Nullable TooltipStack<?> stack);

    interface Entry {
        boolean isText();

        boolean isComponent();

        Component asText();

        TooltipComponent asComponent();
    }


}
