package dev.vfyjxf.cirrus.ui.workbench.value;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ValueTypeVisualRegistry {

    private static final Map<String, ValueTypeVisual> registered = new ConcurrentHashMap<>();
    private static final Map<String, ValueTypeVisual> fallbackCache = new ConcurrentHashMap<>();

    static {
        registerBuiltins();
    }

    private ValueTypeVisualRegistry() {}

    public static void register(Namespace typeId, ValueTypeVisual visual) {
        Objects.requireNonNull(typeId, "typeId");
        register(typeId.toString(), visual);
    }

    public static void register(String typeId, ValueTypeVisual visual) {
        String key = normalize(typeId);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("typeId must not be empty");
        }
        registered.put(key, Objects.requireNonNull(visual, "visual"));
        fallbackCache.remove(key);
    }

    public static ValueTypeVisual resolve(@Nullable ValueType<?> type) {
        return type == null ? resolve("") : resolve(type.typeId().toString());
    }

    public static ValueTypeVisual resolve(@Nullable String typeId) {
        String key = normalize(typeId);
        if (key.isEmpty()) {
            return fallbackCache.computeIfAbsent("unknown", ValueTypeVisualRegistry::buildFallback);
        }
        ValueTypeVisual exact = registered.get(key);
        if (exact != null) {
            return exact;
        }
        String path = pathOf(key);
        ValueTypeVisual byPath = registered.get(path);
        if (byPath != null) {
            return byPath;
        }
        return fallbackCache.computeIfAbsent(key, ValueTypeVisualRegistry::buildFallback);
    }

    public static boolean isValueResourceKey(@Nullable ResourceKey key) {
        if (key == null) {
            return false;
        }
        String type = normalize(key.contentTypeId());
        return !"minecraft:item".equals(type) && !"minecraft:fluid".equals(type);
    }

    private static void registerBuiltins() {
        ValueTypeVisual energy = ValueTypeVisual.builder("calculator_cirrus:energy").unit("FE")
                .displayName(Component.translatable("cirrus.ui.value_type.energy")).accent(CirrusPalette::costH)
                .icon(() -> CirrusTextures.Icon.Sign.Energy.fe).fallbackGlyph(ValueTypeVisual.GlyphKind.bolt)
                .referenceScale(100_000L).compactFormatter(amount -> NumberUtils.toSuffix(amount) + " FE")
                .detailedFormatter(amount -> NumberUtils.toSuffix(amount) + " FE").build();
        register("calculator_cirrus:energy", energy);
        register("minecraft:energy", energy);
        register("forge:energy", energy);
        register("neoforge:energy", energy);
        register("energy", energy);
        register("fe", energy);

        ValueTypeVisual time = ValueTypeVisual.builder("calculator_cirrus:time").unit("t")
                .displayName(Component.translatable("cirrus.ui.value_type.time")).accent(CirrusPalette::costP)
                .icon(() -> CirrusTextures.Icon.Sign.Energy.time).fallbackGlyph(ValueTypeVisual.GlyphKind.clock)
                .referenceScale(1_200L).compactFormatter(ValueTypeVisualRegistry::formatTimeCompact)
                .detailedFormatter(ValueTypeVisualRegistry::formatTimeDetailed).build();
        register("calculator_cirrus:time", time);
        register("minecraft:time", time);
        register("time", time);
        register("ticks", time);

        ValueTypeVisual eu = ValueTypeVisual.builder("cirrus:eu").unit("EU")
                .displayName(Component.translatable("cirrus.ui.value_type.eu")).accent(CirrusPalette::costK)
                .icon(() -> CirrusTextures.Icon.Sign.Energy.eu).fallbackGlyph(ValueTypeVisual.GlyphKind.bolt)
                .referenceScale(100_000L).build();
        register("eu", eu);

        ValueTypeVisual ae = ValueTypeVisual.builder("cirrus:ae").unit("AE")
                .displayName(Component.translatable("cirrus.ui.value_type.ae")).accent(CirrusPalette::focusRing)
                .icon(() -> CirrusTextures.Icon.Sign.Energy.ae).fallbackGlyph(ValueTypeVisual.GlyphKind.crystal)
                .referenceScale(100_000L).build();
        register("ae", ae);

        ValueTypeVisual mana = ValueTypeVisual.builder("cirrus:mana").unit("Mana")
                .displayName(Component.translatable("cirrus.ui.value_type.mana")).accent(CirrusPalette::costC)
                .icon(() -> CirrusTextures.Icon.Sign.Energy.mana).fallbackGlyph(ValueTypeVisual.GlyphKind.crystal)
                .referenceScale(100_000L).build();
        register("mana", mana);

        ValueTypeVisual vis = ValueTypeVisual.builder("cirrus:vis").unit("Vis")
                .displayName(Component.translatable("cirrus.ui.value_type.vis")).accent(CirrusPalette::costS)
                .icon(() -> CirrusTextures.Icon.Sign.Energy.vis).fallbackGlyph(ValueTypeVisual.GlyphKind.crystal)
                .referenceScale(1_000L).build();
        register("vis", vis);
    }

    private static ValueTypeVisual buildFallback(String key) {
        String path = pathOf(key);
        String lower = path.toLowerCase(Locale.ROOT);
        ValueTypeVisual.GlyphKind glyph = ValueTypeVisual.GlyphKind.gauge;
        Function<CirrusPalette, Integer> accent = deterministicAccent(key);
        String unit = deriveUnit(path);
        long scale = 10_000L;

        if (lower.contains("heat") || lower.contains("temp") || lower.contains("thermal") || lower.contains("burn")) {
            glyph = ValueTypeVisual.GlyphKind.flame;
            accent = CirrusPalette::costK;
            unit = lower.contains("temp") ? "K" : "HU";
        } else
            if (lower.contains("stress")
                    || lower.contains("kinetic")
                    || lower.contains("torque")
                    || lower.contains("rpm")) {
                        glyph = ValueTypeVisual.GlyphKind.gear;
                        accent = CirrusPalette::costH;
                        unit = lower.contains("rpm") ? "RPM" : "SU";
                    } else
                if (lower.contains("mana")
                        || lower.contains("aura")
                        || lower.contains("essence")
                        || lower.contains("soul")
                        || lower.contains("xp")
                        || lower.contains("exp")) {
                            glyph = ValueTypeVisual.GlyphKind.crystal;
                            accent = CirrusPalette::costC;
                        } else
                    if (lower.contains("power")
                            || lower.contains("volt")
                            || lower.contains("rf")
                            || lower.contains("ef")) {
                                glyph = ValueTypeVisual.GlyphKind.bolt;
                                accent = CirrusPalette::costH;
                            } else
                        if (lower.contains("time") || lower.contains("duration") || lower.contains("tick")) {
                            glyph = ValueTypeVisual.GlyphKind.clock;
                            accent = CirrusPalette::costP;
                            scale = 1_200L;
                        } else
                            if (lower.contains("radiation")
                                    || lower.contains("pollution")
                                    || lower.contains("flux")
                                    || lower.contains("wave")) {
                                        glyph = ValueTypeVisual.GlyphKind.wave;
                                        accent = CirrusPalette::ruleHardBan;
                                    }

        String finalUnit = unit;
        return ValueTypeVisual.builder(key).unit(finalUnit).displayName(Component.literal(humanize(path)))
                .accent(accent).fallbackGlyph(glyph).referenceScale(scale)
                .compactFormatter(amount -> NumberUtils.toSuffix(amount) + " " + finalUnit)
                .detailedFormatter(amount -> NumberUtils.toSuffix(amount) + " " + finalUnit).build();
    }

    private static String formatTimeCompact(long ticks) {
        if (ticks <= 0L) {
            return "0s";
        }
        if (ticks < 20L) {
            return ticks + "t";
        }
        return WorkbenchFormatters.formatSeconds(ticks / 20.0);
    }

    private static String formatTimeDetailed(long ticks) {
        if (ticks <= 0L) {
            return "0t";
        }
        return NumberUtils.toSuffix(ticks) + "t (" + WorkbenchFormatters.formatSeconds(ticks / 20.0) + ")";
    }

    private static Function<CirrusPalette, Integer> deterministicAccent(String key) {
        int bucket = Math.floorMod(key.hashCode(), 5);
        return switch (bucket) {
            case 0 -> CirrusPalette::focusRing;
            case 1 -> CirrusPalette::costH;
            case 2 -> CirrusPalette::costP;
            case 3 -> CirrusPalette::costS;
            default -> CirrusPalette::costC;
        };
    }

    private static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String pathOf(String typeId) {
        int colon = typeId.indexOf(':');
        String path = colon >= 0 && colon + 1 < typeId.length() ? typeId.substring(colon + 1) : typeId;
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
    }

    private static String deriveUnit(String path) {
        if (path.isEmpty()) {
            return "VAL";
        }
        String cleaned = path.replaceAll("[^a-zA-Z0-9]", "");
        if (cleaned.isEmpty()) {
            return "VAL";
        }
        if (cleaned.length() <= 4) {
            return cleaned.toUpperCase(Locale.ROOT);
        }
        return cleaned.substring(0, 3).toUpperCase(Locale.ROOT);
    }

    private static String humanize(String path) {
        if (path.isEmpty()) {
            return "Value";
        }
        String[] parts = path.split("[_\\-./]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? path : sb.toString();
    }
}
