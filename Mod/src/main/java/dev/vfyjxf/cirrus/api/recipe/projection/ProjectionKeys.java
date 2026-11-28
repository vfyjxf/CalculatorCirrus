package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.List;

/**
 * Standard built-in {@link ProjectionAttributeKey}s and GAS-style {@link ProjectionTags} constants.
 */
public final class ProjectionKeys {

    /** 2D layout dimensions for input grid slots (e.g., 3x3 crafting table, 3x1 smithing table). */
    public static final ProjectionAttributeKey<GridSpec> inputGrid = attr("input_grid", GridSpec.class);

    /** 2D layout dimensions for output grid slots, when applicable. */
    public static final ProjectionAttributeKey<GridSpec> outputGrid = attr("output_grid", GridSpec.class);

    /** Positive durability cost consumed per craft on a tool/equipment input slot. */
    public static final ProjectionAttributeKey<Integer> durabilityCost = attr("durability_cost", Integer.class);

    /** Recipe-level inline {@link ProjectionEffect}s executed alongside pipeline-registered effects. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final ProjectionAttributeKey<List<ProjectionEffect>> localEffects = attr(
        "local_effects",
        (Class) List.class
    );

    public static final class Tags {
        /** Marks an input slot whose item stack is a fluid container eligible for unpacking. */
        public static final Namespace fluidContainer = tag("fluid_container");

        /** Marks an input slot representing a damageable tool whose craft consumes durability. */
        public static final Namespace durabilityTool = tag("durability_tool");

        /** Marks a recipe whose non-guaranteed (chance < 1) outputs should be stripped. */
        public static final Namespace guaranteedOutput = tag("guaranteed_output");

        /** Marks a non-consumed catalyst slot. */
        public static final Namespace catalyst = tag("catalyst");

        /** Marks a recipe whose inputs occupy a 2D shaped grid. */
        public static final Namespace gridShaped = tag("grid_shaped");

        private Tags() {}
    }

    private ProjectionKeys() {}

    private static <T> ProjectionAttributeKey<T> attr(String path, Class<T> valueType) {
        return ProjectionAttributeKey.create(CirrusNamespaces.ofMod("projection/attr/" + path), valueType);
    }

    private static Namespace tag(String path) {
        return CirrusNamespaces.ofMod("projection/tag/" + path);
    }
}
