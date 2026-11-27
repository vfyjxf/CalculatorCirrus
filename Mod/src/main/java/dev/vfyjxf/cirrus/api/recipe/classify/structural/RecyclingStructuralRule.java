package dev.vfyjxf.cirrus.api.recipe.classify.structural;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Tags recycling recipes: a base material (ingot/nugget/dust/gem) recovered
 * from a product made out of that same material (arc furnace
 * {@code from_plate}/{@code from_gear}, salvaging, melting down tools).
 *
 * <p>Detection: an output whose path ends in a base-material suffix yields a
 * material token; an input whose path contains that token together with a
 * product token (plate/gear/tool/armor/...) marks the recipe as
 * {@link ClassificationTags#RECYCLING}. Raw ore smelting and block
 * decompression deliberately do not match ({@code ore}/{@code raw}/
 * {@code block} are not product tokens).
 */
public final class RecyclingStructuralRule implements ClassificationRule {

    public static final RecyclingStructuralRule instance = new RecyclingStructuralRule();
    public static final String id = "cirrus:structural/recycling";

    private static final Set<String> baseSuffixes = Set.of("ingot", "nugget", "dust", "gem", "scrap", "shard");
    private static final List<String> productTokens = List.of(
        "plate",
        "gear",
        "rod",
        "wire",
        "stick",
        "bolt",
        "ring",
        "spring",
        "foil",
        "sword",
        "pickaxe",
        "axe",
        "shovel",
        "hoe",
        "hammer",
        "paxel",
        "saw",
        "helmet",
        "chestplate",
        "leggings",
        "boots",
        "armor",
        "shield",
        "gloves",
        "pendant",
        "can",
        "pipe",
        "casing",
        "tank",
        "cell"
    );

    private RecyclingStructuralRule() {}

    @Override
    public String id() {
        return id;
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        if (recipe.inputs().isEmpty()) {
            return;
        }
        for (ClassifyContent output : recipe.outputs()) {
            if (output.kind() != ClassifyContent.Kind.stack) {
                continue;
            }
            for (String outputId : output.ids()) {
                String material = baseMaterialOf(outputId);
                if (material == null) {
                    continue;
                }
                if (consumesProductOf(recipe, material)) {
                    sink.tag(ClassificationTags.recycling);
                    return;
                }
            }
        }
    }

    /**
     * Extracts the material token from a base-material id:
     * {@code alltheores:tin_ingot -> tin}, {@code minecraft:iron_nugget -> iron}.
     */
    static @Nullable String baseMaterialOf(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        int underscore = path.lastIndexOf('_');
        if (underscore <= 0) {
            return null;
        }
        String suffix = path.substring(underscore + 1);
        if (!baseSuffixes.contains(suffix)) {
            return null;
        }
        return path.substring(0, underscore);
    }

    private static boolean consumesProductOf(RecipeClassifyView recipe, String material) {
        for (ClassifyContent input : recipe.inputs()) {
            if (input.kind() == ClassifyContent.Kind.value || input.kind() == ClassifyContent.Kind.empty) {
                continue;
            }
            for (String inputId : input.ids()) {
                if (isProductOf(inputId, material)) {
                    return true;
                }
            }
            String tagId = input.tagId();
            if (tagId != null && isProductOf(tagId, material)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProductOf(String id, String material) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        if (!path.contains(material)) {
            return false;
        }
        for (String token : productTokens) {
            if (path.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
