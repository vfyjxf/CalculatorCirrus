package dev.vfyjxf.cirrus.api.recipe.classify.structural;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;

/**
 * Global rule separating the two faces of MineColonies teachable views.
 *
 * <p>A colony view whose primary output has at least one other producer outside
 * the colony-view systems is a true mirror of a real craft and is tagged
 * {@link ClassificationTags#DUPLICATE} (excluded — the real system wins). A
 * colony view that is the <b>only</b> producer of its output is the item's real
 * production path in this catalog and must never disappear: it is tagged
 * {@link ClassificationTags#EXCHANGE} (advisory, discouraged) instead.
 *
 * <p>This replaces the unconditional duplicate tagging that made 400+ mod items
 * (minecolonies foods/scrolls in atm10) unproducible under the default source
 * policy.
 */
public final class ColonyViewMirrorRule implements ClassificationRule {

    public static final ColonyViewMirrorRule instance = new ColonyViewMirrorRule();
    public static final String id = "cirrus:structural/colony_view_mirror";

    private ColonyViewMirrorRule() {}

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean global() {
        return true;
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        if (!isColonyViewSystem(recipe.systemId())) {
            return;
        }
        String primary = recipe.primaryOutputId();
        if (primary == null) {
            return;
        }
        boolean hasOtherProducer = false;
        for (int index : context.producersOf(primary)) {
            if (index == recipe.index()) {
                continue;
            }
            RecipeClassifyView other = context.recipes().get(index);
            if (other != null && !isColonyViewSystem(other.systemId())) {
                hasOtherProducer = true;
                break;
            }
        }
        if (hasOtherProducer) {
            sink.tag(ClassificationTags.duplicate);
        } else {
            sink.tag(ClassificationTags.exchange);
        }
    }

    private static boolean isColonyViewSystem(String systemId) {
        return systemId.startsWith("jei:category/minecolonies/")
                || systemId.startsWith("cirrus_test:jei__category/minecolonies/");
    }
}
