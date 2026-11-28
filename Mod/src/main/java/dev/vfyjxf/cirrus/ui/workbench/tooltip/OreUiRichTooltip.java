package dev.vfyjxf.cirrus.ui.workbench.tooltip;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OreUiRichTooltip {

    public enum Tone {
        primary, secondary, muted, accent, positive, warning, danger, costH, costP, costK, costS, costC;

        public int resolve(CirrusPalette palette) {
            return switch (this) {
                case primary -> palette.textPrimary();
                case secondary -> palette.textSecondary();
                case muted -> palette.textMuted();
                case accent -> palette.focusRing();
                case positive -> palette.ruleSoftPrefer();
                case warning -> palette.advisoryDiscouraged();
                case danger -> palette.ruleHardBan();
                case costH -> palette.costH();
                case costP -> palette.costP();
                case costK -> palette.costK();
                case costS -> palette.costS();
                case costC -> palette.costC();
            };
        }
    }

    public record Pill(Component label, Tone tone, @Nullable Integer explicitColor) {

        public Pill {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tone, "tone");
        }

        public static Pill of(Component label, Tone tone) {
            return new Pill(label, tone, null);
        }

        public static Pill of(String label, Tone tone) {
            return new Pill(Component.literal(label), tone, null);
        }

        public static Pill colored(Component label, int color) {
            return new Pill(label, Tone.accent, color);
        }

        public int color(CirrusPalette palette) {
            return explicitColor != null ? explicitColor : tone.resolve(palette);
        }
    }

    public record ItemBadge(@Nullable ContentStack<?, ?> stack, @Nullable Component countLabel) {

        public static ItemBadge of(@Nullable ComponentStackKey resource, @Nullable String countLabel) {
            return new ItemBadge(
                CoPilotPaint.stackOf(resource, 1L),
                countLabel == null ? null : Component.literal(countLabel)
            );
        }

        public static ItemBadge of(@Nullable ContentStack<?, ?> stack, @Nullable Component countLabel) {
            return new ItemBadge(stack, countLabel);
        }
    }

    public record ItemEntry(
        @Nullable ContentStack<?, ?> stack,
        Component name,
        @Nullable Component trailing,
        Tone trailingTone,
        @Nullable Integer explicitColor
    ) {

        public ItemEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(trailingTone, "trailingTone");
        }

        public static ItemEntry of(
            @Nullable ComponentStackKey resource,
            Component name,
            @Nullable Component trailing,
            Tone tone
        ) {
            return new ItemEntry(CoPilotPaint.stackOf(resource, 1L), name, trailing, tone, null);
        }

        public static ItemEntry of(
            @Nullable ContentStack<?, ?> stack,
            Component name,
            @Nullable Component trailing,
            int color
        ) {
            return new ItemEntry(stack, name, trailing, Tone.accent, color);
        }

        public int trailingColor(CirrusPalette palette) {
            return explicitColor != null ? explicitColor : trailingTone.resolve(palette);
        }
    }

    public sealed interface Block {

        record Divider() implements Block {}

        record TextLine(Component text, Tone tone, @Nullable Integer explicitColor) implements Block {

            public int color(CirrusPalette palette) {
                return explicitColor != null ? explicitColor : tone.resolve(palette);
            }
        }

        record KeyValue(Component key, Component value, Tone valueTone, @Nullable Integer explicitColor)
                implements
                    Block {

            public int valueColor(CirrusPalette palette) {
                return explicitColor != null ? explicitColor : valueTone.resolve(palette);
            }
        }

        record MetricPills(List<Pill> pills) implements Block {
            public MetricPills {
                pills = pills == null ? List.of() : List.copyOf(pills);
            }
        }

        record Meter(Component label, double fraction, Component readout, Tone tone, @Nullable Integer explicitColor)
                implements
                    Block {

            public int fillColor(CirrusPalette palette) {
                return explicitColor != null ? explicitColor : tone.resolve(palette);
            }
        }

        record ItemFlow(@Nullable Component label, List<ItemBadge> inputs, List<ItemBadge> outputs) implements Block {

            public ItemFlow {
                inputs = inputs == null ? List.of() : List.copyOf(inputs);
                outputs = outputs == null ? List.of() : List.copyOf(outputs);
            }
        }

        record ItemList(@Nullable Component heading, List<ItemEntry> entries, int maxVisible) implements Block {

            public ItemList {
                entries = entries == null ? List.of() : List.copyOf(entries);
                maxVisible = Math.max(1, maxVisible);
            }
        }

        record ItemGrid(
            @Nullable Component heading,
            List<ContentStack<?, ?>> items,
            int maxColumns,
            int maxRows,
            int activeIndex,
            @Nullable GridInteraction interaction
        ) implements Block {

            public ItemGrid {
                items = items == null ? List.of() : List.copyOf(items);
                maxColumns = Math.max(1, maxColumns);
                maxRows = Math.max(1, maxRows);
            }

            public boolean isInteractive() {
                return interaction != null && !items.isEmpty();
            }
        }

        record FooterHint(Component hint) implements Block {}
    }

    /**
     * JEI-style interaction hooks for an embedded {@link Block.ItemGrid} once the tooltip is pinned
     * via {@code Shift}: supplies per-slot rich tooltips on hover and handles slot activation.
     */
    public interface GridInteraction {

        @Nullable
        OreUiRichTooltip tooltipForItem(int index, ContentStack<?, ?> item);

        default void onItemClick(int index, ContentStack<?, ?> item) {}

        default Component pinnedFooterHint(boolean scrollable) {
            return Component.literal(
                scrollable
                        ? "Hover slot · Scroll · Click to filter"
                        : "Hover slot to inspect · Click to filter"
            );
        }
    }

    private final Component title;
    private final @Nullable Component subtitle;
    private final @Nullable ContentStack<?, ?> iconStack;
    private final @Nullable ImageTexture iconTexture;
    private final @Nullable Component badge;
    private final Tone accentTone;
    private final @Nullable Integer explicitAccent;
    private final List<Block> blocks;

    private OreUiRichTooltip(
        Component title,
        @Nullable Component subtitle,
        @Nullable ContentStack<?, ?> iconStack,
        @Nullable ImageTexture iconTexture,
        @Nullable Component badge,
        Tone accentTone,
        @Nullable Integer explicitAccent,
        List<Block> blocks
    ) {
        this.title = Objects.requireNonNull(title, "title");
        this.subtitle = subtitle;
        this.iconStack = iconStack;
        this.iconTexture = iconTexture;
        this.badge = badge;
        this.accentTone = Objects.requireNonNull(accentTone, "accentTone");
        this.explicitAccent = explicitAccent;
        this.blocks = List.copyOf(blocks);
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public static Builder builder(String title) {
        return new Builder(Component.literal(title));
    }

    public Component title() {
        return title;
    }

    public @Nullable Component subtitle() {
        return subtitle;
    }

    public @Nullable ContentStack<?, ?> iconStack() {
        return iconStack;
    }

    public @Nullable ImageTexture iconTexture() {
        return iconTexture;
    }

    public @Nullable Component badge() {
        return badge;
    }

    public int accent(CirrusPalette palette) {
        return explicitAccent != null ? explicitAccent : accentTone.resolve(palette);
    }

    public List<Block> blocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return title.getString().isEmpty() && blocks.isEmpty();
    }

    public Block.@Nullable ItemGrid interactiveGrid() {
        for (Block block : blocks) {
            if (block instanceof Block.ItemGrid grid && grid.isInteractive()) {
                return grid;
            }
        }
        return null;
    }

    public boolean hasInteractiveGrid() {
        return interactiveGrid() != null;
    }

    public static final class Builder {

        private final Component title;
        private @Nullable Component subtitle;
        private @Nullable ContentStack<?, ?> iconStack;
        private @Nullable ImageTexture iconTexture;
        private @Nullable Component badge;
        private Tone accentTone = Tone.accent;
        private @Nullable Integer explicitAccent;
        private final List<Block> blocks = new ArrayList<>();

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        public Builder subtitle(@Nullable Component subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder subtitle(@Nullable String subtitle) {
            this.subtitle = subtitle == null || subtitle.isBlank() ? null : Component.literal(subtitle);
            return this;
        }

        public Builder icon(@Nullable ComponentStackKey resource) {
            this.iconStack = CoPilotPaint.stackOf(resource, 1L);
            return this;
        }

        public Builder icon(@Nullable ContentStack<?, ?> stack) {
            this.iconStack = stack;
            return this;
        }

        public Builder icon(@Nullable ImageTexture texture) {
            this.iconTexture = texture;
            return this;
        }

        public Builder badge(@Nullable Component badge, Tone tone) {
            this.badge = badge;
            this.accentTone = tone == null ? Tone.accent : tone;
            this.explicitAccent = null;
            return this;
        }

        public Builder badge(@Nullable Component badge, int accentColor) {
            this.badge = badge;
            this.explicitAccent = accentColor;
            return this;
        }

        public Builder badge(@Nullable String badge, Tone tone) {
            return badge(badge == null || badge.isBlank() ? null : Component.literal(badge), tone);
        }

        public Builder badge(@Nullable String badge, int accentColor) {
            return badge(badge == null || badge.isBlank() ? null : Component.literal(badge), accentColor);
        }

        public Builder accent(Tone tone) {
            this.accentTone = tone == null ? Tone.accent : tone;
            this.explicitAccent = null;
            return this;
        }

        public Builder accent(int color) {
            this.explicitAccent = color;
            return this;
        }

        public Builder divider() {
            blocks.add(new Block.Divider());
            return this;
        }

        public Builder text(Component text, Tone tone) {
            if (text != null && !text.getString().isEmpty()) {
                blocks.add(new Block.TextLine(text, tone == null ? Tone.secondary : tone, null));
            }
            return this;
        }

        public Builder text(String text, Tone tone) {
            return text == null || text.isEmpty() ? this : text(Component.literal(text), tone);
        }

        public Builder keyValue(Component key, Component value, Tone tone) {
            blocks.add(new Block.KeyValue(key, value, tone == null ? Tone.primary : tone, null));
            return this;
        }

        public Builder keyValue(String key, String value, Tone tone) {
            return keyValue(Component.literal(key), Component.literal(value), tone);
        }

        public Builder keyValue(Component key, Component value, int color) {
            blocks.add(new Block.KeyValue(key, value, Tone.primary, color));
            return this;
        }

        public Builder pills(List<Pill> pills) {
            if (pills != null && !pills.isEmpty()) {
                blocks.add(new Block.MetricPills(pills));
            }
            return this;
        }

        public Builder pills(Pill... pills) {
            if (pills != null && pills.length > 0) {
                blocks.add(new Block.MetricPills(List.of(pills)));
            }
            return this;
        }

        public Builder meter(Component label, double fraction, Component readout, Tone tone) {
            blocks.add(new Block.Meter(label, fraction, readout, tone == null ? Tone.accent : tone, null));
            return this;
        }

        public Builder meter(String label, double fraction, String readout, Tone tone) {
            return meter(Component.literal(label), fraction, Component.literal(readout), tone);
        }

        public Builder meter(Component label, double fraction, Component readout, int color) {
            blocks.add(new Block.Meter(label, fraction, readout, Tone.accent, color));
            return this;
        }

        public Builder itemFlow(@Nullable Component label, List<ItemBadge> inputs, List<ItemBadge> outputs) {
            if ((inputs != null && !inputs.isEmpty()) || (outputs != null && !outputs.isEmpty())) {
                blocks.add(new Block.ItemFlow(label, inputs, outputs));
            }
            return this;
        }

        public Builder itemList(@Nullable Component heading, List<ItemEntry> entries, int maxVisible) {
            if (entries != null && !entries.isEmpty()) {
                blocks.add(new Block.ItemList(heading, entries, maxVisible));
            }
            return this;
        }

        public Builder itemGrid(
            @Nullable Component heading,
            List<? extends ContentStack<?, ?>> items,
            int maxColumns,
            int maxRows,
            int activeIndex,
            @Nullable GridInteraction interaction
        ) {
            if (items != null && !items.isEmpty()) {
                List<ContentStack<?, ?>> copy = new ArrayList<>(items.size());
                for (ContentStack<?, ?> item : items) {
                    if (item != null && !item.isEmpty()) {
                        copy.add(item);
                    }
                }
                if (!copy.isEmpty()) {
                    blocks.add(new Block.ItemGrid(heading, copy, maxColumns, maxRows, activeIndex, interaction));
                }
            }
            return this;
        }

        public Builder itemGrid(
            @Nullable Component heading,
            List<? extends ContentStack<?, ?>> items,
            @Nullable GridInteraction interaction
        ) {
            return itemGrid(heading, items, 10, 4, -1, interaction);
        }

        public Builder footer(Component hint) {
            if (hint != null && !hint.getString().isEmpty()) {
                blocks.add(new Block.FooterHint(hint));
            }
            return this;
        }

        public Builder footer(String hint) {
            return hint == null || hint.isEmpty() ? this : footer(Component.literal(hint));
        }

        public OreUiRichTooltip build() {
            return new OreUiRichTooltip(
                title,
                subtitle,
                iconStack,
                iconTexture,
                badge,
                accentTone,
                explicitAccent,
                blocks
            );
        }
    }
}
