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

public interface ITooltip {

    static Entry entry(Component text) {
        return new TooltipEntry(text);
    }

    static Entry entry(TooltipComponent text) {
        return new TooltipEntry(text);
    }

    static ITooltip create(@Nullable Point mousePos, List<Component> texts) {
        return from(mousePos, texts.stream().map(ITooltip::entry).toList());
    }

    static ITooltip create(@Nullable Point mousePos, Component... texts) {
        return create(mousePos, List.of(texts));
    }

    static ITooltip create(List<Component> texts) {
        return create(null, texts);
    }

    static ITooltip create(Component... texts) {
        return create(List.of(texts));
    }

    static ITooltip from(@Nullable Point mousePos, List<Entry> entries) {
        return new ListTooltip(mousePos, entries);
    }

    static ITooltip from(@Nullable Point mousePos, Entry... entries) {
        return from(mousePos, List.of(entries));
    }

    static ITooltip from(List<Entry> entries) {
        return from(null, entries);
    }

    static ITooltip from(Entry... entries) {
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

    ITooltip add(Component text);

    ITooltip add(TooltipComponent component);

    ITooltip add(Supplier<Component> supplier);

    default ITooltip addAll(TooltipComponent... components) {
        for (TooltipComponent component : components) {
            add(component);
        }
        return this;
    }

    default ITooltip addAll(Component... text) {
        for (Component component : text) {
            add(component);
        }
        return this;
    }

    default ITooltip addAllTooltipComponents(Iterable<TooltipComponent> text) {
        for (TooltipComponent component : text) {
            add(component);
        }
        return this;
    }

    default ITooltip addAllTexts(Iterable<Component> text) {
        for (Component component : text) {
            add(component);
        }
        return this;
    }

    ITooltip ofAll(ITooltip other);

    ITooltip copy();

    @Nullable
    TooltipStack<?> stack();

    @Contract("_ -> this")
    ITooltip setContextStack(@Nullable TooltipStack<?> stack);

    interface Entry {
        boolean isText();

        boolean isComponent();

        Component asText();

        TooltipComponent asComponent();
    }


}
