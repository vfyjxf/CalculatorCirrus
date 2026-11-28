package dev.vfyjxf.cirrus.planning.normalize;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeTagAssignment;
import dev.vfyjxf.cirrus.api.planning.tag.TagProvenance;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dual-layer tag classifier producing the 10 human-intuitive {@link CanonicalRecipeTag}s
 * with provenance from Cirrus {@link RecipeSystemTags} + structural topology.
 */
public final class DualTagClassifier {

    private DualTagClassifier() {}

    public static List<RecipeTagAssignment> classify(RawRecipeRecord record, boolean mirrorDuplicate, boolean suspectJei) {
        return classify(record, mirrorDuplicate, suspectJei, false);
    }

    public static List<RecipeTagAssignment> classify(
        RawRecipeRecord record,
        boolean mirrorDuplicate,
        boolean suspectJei,
        boolean hasReversePackingPartner
    ) {
        List<RecipeTagAssignment> out = new ArrayList<>(4);
        Set<Namespace> sysTags = record.systemTags();

        if (mirrorDuplicate
                || sysTags.contains(RecipeSystemTags.displayOnly)
                || sysTags.contains(RecipeSystemTags.mirrorDuplicate)) {
            out.add(
                new RecipeTagAssignment(
                    CanonicalRecipeTag.junkOrDuplicate,
                    mirrorDuplicate ? TagProvenance.jeiAudit : TagProvenance.systemTag,
                    1.0,
                    mirrorDuplicate ? "cirrus.tag.reason.native_mirror" : "cirrus.tag.reason.display_system"
                )
            );
        }

        if (suspectJei) {
            out.add(
                new RecipeTagAssignment(
                    CanonicalRecipeTag.suspiciousJei,
                    TagProvenance.jeiAudit,
                    0.85,
                    "cirrus.tag.reason.jei_low_fidelity"
                )
            );
        }

        if (sysTags.contains(RecipeSystemTags.resourceGen)) {
            out.add(
                new RecipeTagAssignment(
                    CanonicalRecipeTag.resourceGeneration,
                    TagProvenance.systemTag,
                    0.95,
                    "cirrus.tag.reason.resource_gen_system"
                )
            );
        }

        // Structural catalytic/self-multiplying check: a consumed input also appears in outputs.
        // Non-consumed tool/durability catalysts do not make a normal recipe a catalytic loop.
        Set<ComponentStackKey> outKeys = new HashSet<>();
        for (RawOutputSlot o : record.outputs()) {
            outKeys.add(o.resource());
        }
        boolean hasConsumed = false;
        boolean catalyticOverlap = false;
        for (RawIngredientSlot in : record.inputs()) {
            if (!in.catalyst()) {
                hasConsumed = true;
                if (in.candidates().size() == 1 && outKeys.contains(in.candidates().get(0))) {
                    catalyticOverlap = true;
                }
            }
        }
        if (catalyticOverlap) {
            out.add(
                new RecipeTagAssignment(
                    CanonicalRecipeTag.catalyticLoop,
                    TagProvenance.structural,
                    0.90,
                    "cirrus.tag.reason.catalytic_overlap"
                )
            );
        } else if (!hasConsumed && !record.outputs().isEmpty()) {
            out.add(
                new RecipeTagAssignment(
                    CanonicalRecipeTag.resourceGeneration,
                    TagProvenance.structural,
                    0.80,
                    "cirrus.tag.reason.zero_consumed_input"
                )
            );
        }

        // Structural packing/unpacking: only when a true reverse recipe exists in the catalog
        // (prevents one-way 1 log -> 4 planks from being misclassified as unpacking).
        if (hasReversePackingPartner && record.inputs().size() == 1 && record.outputs().size() == 1) {
            RawIngredientSlot in = record.inputs().get(0);
            RawOutputSlot o = record.outputs().get(0);
            if (!in.catalyst()
                    && in.candidates().size() == 1
                    && !in.candidates().get(0).equals(o.resource())) {
                long a = in.amount();
                long b = o.amount();
                if ((a == 9L && b == 1L) || (a == 1L && b == 9L) || (a == 4L && b == 1L) || (a == 1L && b == 4L)) {
                    out.add(
                        new RecipeTagAssignment(
                            CanonicalRecipeTag.packingUnpacking,
                            TagProvenance.structural,
                            0.75,
                            "cirrus.tag.reason.scaling_ratio"
                        )
                    );
                }
            }
        }

        // Merge any pre-existing canonical tags on the record.
        EnumSet<CanonicalRecipeTag> seen = EnumSet.noneOf(CanonicalRecipeTag.class);
        for (RecipeTagAssignment a : out) {
            seen.add(a.tag());
        }
        for (CanonicalRecipeTag existing : record.canonicalTags()) {
            if (seen.add(existing)) {
                out.add(new RecipeTagAssignment(existing, TagProvenance.rule, 0.90, "cirrus.tag.reason.upstream"));
            }
        }

        if (seen.isEmpty() || (seen.size() == 1 && seen.contains(CanonicalRecipeTag.resourceGeneration))) {
            out.add(
                new RecipeTagAssignment(
                    CanonicalRecipeTag.normalProduction,
                    TagProvenance.structural,
                    0.90,
                    "cirrus.tag.reason.default_production"
                )
            );
        }
        return List.copyOf(out);
    }

    public static Set<CanonicalRecipeTag> tagSet(List<RecipeTagAssignment> assignments) {
        EnumSet<CanonicalRecipeTag> set = EnumSet.noneOf(CanonicalRecipeTag.class);
        for (RecipeTagAssignment a : assignments) {
            set.add(a.tag());
        }
        return set;
    }
}
