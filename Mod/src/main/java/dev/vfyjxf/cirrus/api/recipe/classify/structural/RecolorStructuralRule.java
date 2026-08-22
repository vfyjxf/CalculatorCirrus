package dev.vfyjxf.cirrus.api.recipe.classify.structural;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Tags recolor recipes: an already-colored family member plus (usually) a dye
 * becomes another color of the same family
 * ({@code red_concrete_powder + white dye -> white_concrete_powder}).
 *
 * <p>Detection is lexical: an output id containing a color token is compared
 * against input stack ids; when stripping the color token from both yields the
 * same base path, the recipe is a recolor. Tag inputs whose members are the
 * color family itself are covered by {@link ExchangeStructuralRule} instead.
 *
 * <p>Normal dyeing (uncolored base + dye -> colored result) is intentionally
 * NOT matched: it is the primary production path for colored items.
 */
public final class RecolorStructuralRule implements ClassificationRule {

    public static final RecolorStructuralRule instance = new RecolorStructuralRule();
    public static final String ID = "cirrus:structural/recolor";

    /**
     * The sixteen vanilla dye colors; two-word colors first so token scanning
     * prefers the longest match.
     */
    private static final List<String> COLORS = List.of(
        "light_blue", "light_gray",
        "white", "orange", "magenta", "yellow", "lime", "pink", "gray",
        "cyan", "purple", "blue", "brown", "green", "red", "black"
    );

    /** {@link #COLORS} pre-split on {@code _}; aligned by index. */
    private static final List<String[]> COLOR_TOKENS = COLORS.stream()
        .map(color -> color.split("_"))
        .toList();

    /** Every token appearing in any color name; fast reject for uncolored ids. */
    private static final Set<String> COLOR_WORDS = COLOR_TOKENS.stream()
        .flatMap(java.util.Arrays::stream)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private RecolorStructuralRule() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        for (ClassifyContent output : recipe.outputs()) {
            if (output.kind() != ClassifyContent.Kind.stack) {
                continue;
            }
            for (String outputId : output.ids()) {
                ColoredPath out = ColoredPath.parse(outputId);
                if (out == null) {
                    continue;
                }
                if (hasColoredFamilyInput(recipe, out)) {
                    sink.tag(ClassificationTags.RECOLOR);
                    return;
                }
            }
        }
    }

    private static boolean hasColoredFamilyInput(RecipeClassifyView recipe, ColoredPath output) {
        for (ClassifyContent input : recipe.inputs()) {
            if (input.kind() != ClassifyContent.Kind.stack) {
                continue;
            }
            for (String inputId : input.ids()) {
                ColoredPath in = ColoredPath.parse(inputId);
                if (in != null && !in.color().equals(output.color()) && in.base().equals(output.base())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An id path split into its color token and the remaining base path
     * ({@code minecraft/light_blue_concrete_powder} → ({@code light_blue},
     * {@code minecraft:concrete_powder})).
     */
    record ColoredPath(String color, String base) {

        static @Nullable ColoredPath parse(String id) {
            int colon = id.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String namespace = id.substring(0, colon);
            String path = id.substring(colon + 1);
            String[] tokens = path.split("_");
            boolean maybeColored = false;
            for (String token : tokens) {
                if (COLOR_WORDS.contains(token)) {
                    maybeColored = true;
                    break;
                }
            }
            if (!maybeColored) {
                return null;
            }
            for (int c = 0; c < COLORS.size(); c++) {
                String color = COLORS.get(c);
                String[] colorTokens = COLOR_TOKENS.get(c);
                int at = indexOfColor(tokens, colorTokens);
                if (at < 0) {
                    continue;
                }
                StringBuilder base = new StringBuilder(namespace).append(':');
                boolean first = true;
                for (int i = 0; i < tokens.length; i++) {
                    if (i >= at && i < at + colorTokens.length) {
                        continue;
                    }
                    if (!first) {
                        base.append('_');
                    }
                    base.append(tokens[i]);
                    first = false;
                }
                return new ColoredPath(color, base.toString());
            }
            return null;
        }

        private static int indexOfColor(String[] tokens, String[] colorTokens) {
            outer:
            for (int i = 0; i + colorTokens.length <= tokens.length; i++) {
                for (int j = 0; j < colorTokens.length; j++) {
                    if (!tokens[i + j].equals(colorTokens[j])) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }
}
