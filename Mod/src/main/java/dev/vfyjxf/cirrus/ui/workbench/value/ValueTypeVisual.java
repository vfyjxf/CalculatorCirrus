package dev.vfyjxf.cirrus.ui.workbench.value;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

public final class ValueTypeVisual {

    public enum GlyphKind {
        bolt, clock, flame, gear, crystal, wave, gauge
    }

    private final String typeId;
    private final String unit;
    private final Supplier<Component> displayName;
    private final Function<CirrusPalette, Integer> accentResolver;
    private final Supplier<@Nullable ImageTexture> iconSupplier;
    private final GlyphKind fallbackGlyph;
    private final long referenceScale;
    private final LongFunction<String> compactFormatter;
    private final LongFunction<String> detailedFormatter;

    private ValueTypeVisual(Builder builder) {
        this.typeId = builder.typeId;
        this.unit = builder.unit;
        this.displayName = builder.displayName;
        this.accentResolver = builder.accentResolver;
        this.iconSupplier = builder.iconSupplier;
        this.fallbackGlyph = builder.fallbackGlyph;
        this.referenceScale = Math.max(1L, builder.referenceScale);
        this.compactFormatter = builder.compactFormatter;
        this.detailedFormatter = builder.detailedFormatter;
    }

    public static Builder builder(String typeId) {
        return new Builder(typeId);
    }

    public String typeId() {
        return typeId;
    }

    public String unit() {
        return unit;
    }

    public Component displayName() {
        return displayName.get();
    }

    public int accentColor(CirrusPalette palette) {
        return accentResolver.apply(palette);
    }

    public @Nullable ImageTexture icon() {
        return iconSupplier.get();
    }

    public boolean hasIconSupplier() {
        return iconSupplier != emptyIconSupplier;
    }

    public GlyphKind fallbackGlyph() {
        return fallbackGlyph;
    }

    public long referenceScale() {
        return referenceScale;
    }

    public String formatCompact(long amount) {
        return compactFormatter.apply(Math.max(0L, amount));
    }

    public String formatDetailed(long amount) {
        return detailedFormatter.apply(Math.max(0L, amount));
    }

    public float gaugeFraction(long amount) {
        if (amount <= 0L) {
            return 0.0f;
        }
        double ratio = Math.log10(amount + 1.0) / Math.log10(referenceScale + 1.0);
        return (float) Math.max(0.16, Math.min(1.0, ratio));
    }

    private static final Supplier<@Nullable ImageTexture> emptyIconSupplier = () -> null;

    public static final class Builder {
        private final String typeId;
        private String unit = "VAL";
        private Supplier<Component> displayName;
        private Function<CirrusPalette, Integer> accentResolver = CirrusPalette::focusRing;
        private Supplier<@Nullable ImageTexture> iconSupplier = emptyIconSupplier;
        private GlyphKind fallbackGlyph = GlyphKind.gauge;
        private long referenceScale = 10_000L;
        private LongFunction<String> compactFormatter;
        private LongFunction<String> detailedFormatter;

        private Builder(String typeId) {
            this.typeId = Objects.requireNonNull(typeId, "typeId");
            this.displayName = () -> Component.literal(typeId);
            this.compactFormatter = amount -> NumberUtils.toSuffix(amount) + " " + this.unit;
            this.detailedFormatter = amount -> NumberUtils.toSuffix(amount) + " " + this.unit;
        }

        public Builder unit(String unit) {
            this.unit = Objects.requireNonNull(unit, "unit");
            return this;
        }

        public Builder displayName(Supplier<Component> displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder displayName(Component displayName) {
            Objects.requireNonNull(displayName, "displayName");
            this.displayName = () -> displayName;
            return this;
        }

        public Builder accent(Function<CirrusPalette, Integer> accentResolver) {
            this.accentResolver = Objects.requireNonNull(accentResolver, "accentResolver");
            return this;
        }

        public Builder icon(@Nullable ImageTexture icon) {
            this.iconSupplier = icon == null ? emptyIconSupplier : () -> icon;
            return this;
        }

        public Builder icon(Supplier<@Nullable ImageTexture> iconSupplier) {
            this.iconSupplier = iconSupplier == null ? emptyIconSupplier : iconSupplier;
            return this;
        }

        public Builder fallbackGlyph(GlyphKind fallbackGlyph) {
            this.fallbackGlyph = Objects.requireNonNull(fallbackGlyph, "fallbackGlyph");
            return this;
        }

        public Builder referenceScale(long referenceScale) {
            this.referenceScale = Math.max(1L, referenceScale);
            return this;
        }

        public Builder compactFormatter(LongFunction<String> compactFormatter) {
            this.compactFormatter = Objects.requireNonNull(compactFormatter, "compactFormatter");
            return this;
        }

        public Builder detailedFormatter(LongFunction<String> detailedFormatter) {
            this.detailedFormatter = Objects.requireNonNull(detailedFormatter, "detailedFormatter");
            return this;
        }

        public ValueTypeVisual build() {
            return new ValueTypeVisual(this);
        }
    }
}
