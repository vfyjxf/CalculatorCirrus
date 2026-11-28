package dev.vfyjxf.cirrus.internal.integration.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * JEI recipe representing a saved CalculatorCirrus production plan flow, serialized natively by JEI's bookmark codec.
 */
public record PlanFlowRecipe(String id, CirrusPlanBookmarkStore.PlanBookmarkEntry snapshot) {

    public static final Codec<ComponentStackKey> resourceKeyCodec = WorldFacilityRegistry.resourceKeyCodec;

    public static final Codec<CirrusPlanBookmarkStore.MaterialLine> materialLineCodec = RecordCodecBuilder.create(
        instance -> instance.group(
            resourceKeyCodec.fieldOf("resource").forGetter(CirrusPlanBookmarkStore.MaterialLine::resource),
            Codec.LONG.fieldOf("needed").forGetter(CirrusPlanBookmarkStore.MaterialLine::needed)
        ).apply(instance, CirrusPlanBookmarkStore.MaterialLine::new)
    );

    public static final Codec<CirrusPlanBookmarkStore.MachineLine> machineLineCodec = RecordCodecBuilder.create(
        instance -> instance.group(
            WorldFacilityRegistry.machineEntryCodec.fieldOf("machine").forGetter(CirrusPlanBookmarkStore.MachineLine::machine),
            Codec.INT.fieldOf("countToBuild").forGetter(CirrusPlanBookmarkStore.MachineLine::countToBuild)
        ).apply(instance, CirrusPlanBookmarkStore.MachineLine::new)
    );

    public static final Codec<CirrusPlanBookmarkStore.StepLine> stepLineCodec = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.STRING.fieldOf("stepId").forGetter(CirrusPlanBookmarkStore.StepLine::stepId),
            Codec.STRING.fieldOf("kind").forGetter(CirrusPlanBookmarkStore.StepLine::kind),
            resourceKeyCodec.optionalFieldOf("output").forGetter(s -> Optional.ofNullable(s.output())),
            Codec.LONG.fieldOf("amount").forGetter(CirrusPlanBookmarkStore.StepLine::amount),
            Codec.LONG.optionalFieldOf("batches").forGetter(s -> Optional.of(s.batches())),
            WorldFacilityRegistry.machineEntryCodec.optionalFieldOf("machine")
                    .forGetter(s -> Optional.ofNullable(s.machine())),
            Codec.BOOL.optionalFieldOf("auto", false).forGetter(CirrusPlanBookmarkStore.StepLine::auto),
            Codec.DOUBLE.optionalFieldOf("workSeconds", 0.0).forGetter(CirrusPlanBookmarkStore.StepLine::workSeconds),
            Codec.DOUBLE.optionalFieldOf("waitSeconds", 0.0).forGetter(CirrusPlanBookmarkStore.StepLine::waitSeconds),
            Codec.STRING.listOf().optionalFieldOf("dependsOn", List.of())
                    .forGetter(CirrusPlanBookmarkStore.StepLine::dependsOn),
            resourceKeyCodec.listOf().optionalFieldOf("inputs", List.of())
                    .forGetter(CirrusPlanBookmarkStore.StepLine::inputs),
            materialLineCodec.listOf().optionalFieldOf("inputAmounts", List.of())
                    .forGetter(CirrusPlanBookmarkStore.StepLine::inputAmounts)
        ).apply(
            instance,
            (stepId, kind, output, amount, batches, machine, auto, workSec, waitSec, dependsOn, inputs, inAmts) -> new CirrusPlanBookmarkStore.StepLine(
                stepId,
                kind,
                output.orElse(null),
                amount,
                batches.orElse(amount),
                machine.orElse(null),
                auto,
                workSec,
                waitSec,
                dependsOn,
                inputs,
                inAmts
            )
        )
    );

    public static final Codec<PlanFlowRecipe> codec = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(r -> r.latestEntry().id()),
            resourceKeyCodec.fieldOf("primaryResource").forGetter(r -> r.latestEntry().primaryResource()),
            Codec.LONG.fieldOf("primaryAmount").forGetter(r -> r.latestEntry().primaryAmount()),
            Codec.STRING.fieldOf("title").forGetter(r -> r.latestEntry().title()),
            Codec.INT.fieldOf("totalSteps").forGetter(r -> r.latestEntry().totalSteps()),
            Codec.INT.fieldOf("craftSteps").forGetter(r -> r.latestEntry().craftSteps()),
            Codec.DOUBLE.fieldOf("workSeconds").forGetter(r -> r.latestEntry().workSeconds()),
            Codec.DOUBLE.fieldOf("waitSeconds").forGetter(r -> r.latestEntry().waitSeconds()),
            materialLineCodec.listOf().optionalFieldOf("rawMaterials", List.of())
                    .forGetter(r -> r.latestEntry().rawMaterials()),
            stepLineCodec.listOf().optionalFieldOf("steps", List.of()).forGetter(r -> r.latestEntry().steps()),
            Codec.STRING.listOf().optionalFieldOf("checkedStepIds", List.of())
                    .forGetter(r -> new ArrayList<>(r.latestEntry().checkedStepIds())),
            machineLineCodec.listOf().optionalFieldOf("machinesToProvision", List.of())
                    .forGetter(r -> r.latestEntry().machinesToProvision()),
            resourceKeyCodec.listOf().optionalFieldOf("catalysts", List.of())
                    .forGetter(r -> r.latestEntry().catalysts()),
            materialLineCodec.listOf().optionalFieldOf("surplusByproducts", List.of())
                    .forGetter(r -> r.latestEntry().surplusByproducts()),
            Codec.STRING.listOf().optionalFieldOf("automatedLines", List.of())
                    .forGetter(r -> r.latestEntry().automatedLines())
        ).apply(instance, PlanFlowRecipe::decode)
    );

    public PlanFlowRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(snapshot, "snapshot");
    }

    public static PlanFlowRecipe of(CirrusPlanBookmarkStore.PlanBookmarkEntry entry) {
        return new PlanFlowRecipe(entry.id(), entry);
    }

    private static PlanFlowRecipe decode(
        String id,
        ComponentStackKey primaryResource,
        long primaryAmount,
        String title,
        int totalSteps,
        int craftSteps,
        double workSeconds,
        double waitSeconds,
        List<CirrusPlanBookmarkStore.MaterialLine> rawMaterials,
        List<CirrusPlanBookmarkStore.StepLine> steps,
        List<String> checkedStepIds,
        List<CirrusPlanBookmarkStore.MachineLine> machinesToProvision,
        List<ComponentStackKey> catalysts,
        List<CirrusPlanBookmarkStore.MaterialLine> surplusByproducts,
        List<String> automatedLines
    ) {
        Set<String> checked = new LinkedHashSet<>(checkedStepIds);
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = new CirrusPlanBookmarkStore.PlanBookmarkEntry(
            id,
            primaryResource,
            Math.max(1L, primaryAmount),
            title,
            Math.max(0, totalSteps),
            Math.max(0, craftSteps),
            Math.max(0.0, workSeconds),
            Math.max(0.0, waitSeconds),
            rawMaterials,
            steps,
            checked,
            machinesToProvision,
            catalysts,
            surplusByproducts,
            automatedLines,
            null
        );
        CirrusPlanBookmarkStore.put(entry);
        return of(entry);
    }

    /** Returns the freshest stored entry so live checklist progress and materials stay in sync. */
    public CirrusPlanBookmarkStore.PlanBookmarkEntry latestEntry() {
        CirrusPlanBookmarkStore.PlanBookmarkEntry stored = CirrusPlanBookmarkStore.find(id);
        return stored != null ? stored : snapshot;
    }

    public ResourceLocation registryName() {
        StringBuilder slug = new StringBuilder();
        String lower = id.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                slug.append(c);
            } else {
                slug.append('_');
            }
        }
        String safe = slug.length() > 64 ? slug.substring(0, 64) : slug.toString();
        if (safe.isEmpty()) {
            safe = "flow_" + Integer.toHexString(id.hashCode());
        }
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "plan_flow/" + safe);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PlanFlowRecipe other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
