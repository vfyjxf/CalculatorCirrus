package dev.vfyjxf.cirrus.planning.core.trust;

import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeSemanticKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic SHA-256 semantic key builder (M8 §1, Pure JDK 21):
 * invariant to JEI page enumeration order and commutative slot permutation,
 * while strictly distinguishing machines, gates, opaque constraints, and slot match semantics.
 */
public final class RecipeSemanticKeyBuilder {

    private RecipeSemanticKeyBuilder() {}

    public static RecipeSemanticKey build(RawRecipeRecord record) {
        return build(record, true);
    }

    public static RecipeSemanticKey build(RawRecipeRecord record, boolean commutativeInputSlots) {
        Objects.requireNonNull(record, "record");
        List<String> inputTokens = new ArrayList<>(record.inputs().size());
        for (RawIngredientSlot in : record.inputs()) {
            List<String> cands = new ArrayList<>(in.candidates().size());
            for (ResourceKey rk : in.candidates()) {
                cands.add(rk.canonicalString());
            }
            Collections.sort(cands);
            String token = (in.catalyst() ? "CAT:" : "IN:")
                    + in.amount()
                    + ":" + in.matchMode().name()
                    + ":" + in.tagId().orElse("-")
                    + ":" + String.join(",", cands);
            inputTokens.add(token);
        }
        if (commutativeInputSlots) {
            Collections.sort(inputTokens);
        }

        List<String> outputTokens = new ArrayList<>(record.outputs().size());
        for (RawOutputSlot out : record.outputs()) {
            outputTokens.add("OUT:" + out.amount() + ":" + out.resource().canonicalString());
        }
        Collections.sort(outputTokens);

        List<String> machines = new ArrayList<>(record.machineIds());
        Collections.sort(machines);

        String descriptor = "sys=" + record.systemId()
                + "|mach=" + String.join(",", machines)
                + "|opaque=" + (record.hasOpaqueConstraint() ? "1" : "0")
                + "|in=[" + String.join(";", inputTokens) + "]"
                + "|out=[" + String.join(";", outputTokens) + "]";

        String lineage = record.systemId()
                + "|inCount=" + record.inputs().size()
                + "|outCount=" + record.outputs().size()
                + "|mach=" + (machines.isEmpty() ? "none" : machines.get(0));

        return RecipeSemanticKey.of(record.systemId(), descriptor, lineage);
    }

    /** Cross-system I/O fingerprint (ignores systemId so JEI mirrors of native systems deduplicate). */
    public static String ioFingerprint(RawRecipeRecord record) {
        List<String> ins = new ArrayList<>(record.inputs().size());
        for (RawIngredientSlot in : record.inputs()) {
            List<String> cands = new ArrayList<>(in.candidates().size());
            for (ResourceKey rk : in.candidates()) {
                cands.add(rk.canonicalString());
            }
            Collections.sort(cands);
            ins.add((in.catalyst() ? "C:" : "I:") + in.amount() + ":" + String.join(",", cands));
        }
        Collections.sort(ins);

        List<String> outs = new ArrayList<>(record.outputs().size());
        for (RawOutputSlot out : record.outputs()) {
            outs.add("O:" + out.amount() + ":" + out.resource().canonicalString());
        }
        Collections.sort(outs);

        return RecipeSemanticKey.sha256Hex("I=[" + String.join(";", ins) + "]|O=[" + String.join(";", outs) + "]");
    }
}
