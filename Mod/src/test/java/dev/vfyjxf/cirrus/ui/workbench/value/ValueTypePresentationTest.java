package dev.vfyjxf.cirrus.ui.workbench.value;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyContentType;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValueType;
import dev.vfyjxf.cirrus.content.type.BasicValueType;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionRecipePreviewLayout;
import dev.vfyjxf.cirrus.ui.process.MaterialCategories;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueTypePresentationTest {

    @Test
    void registryResolvesBuiltinsHeuristicsAndCustomFutureValueTypes() {
        ValueTypeVisual energy = ValueTypeVisualRegistry.resolve("calculator_cirrus:energy");
        assertEquals("FE", energy.unit());
        assertTrue(energy.hasIconSupplier());
        assertEquals("128 FE", energy.formatCompact(128L));
        assertTrue(energy.gaugeFraction(128L) > 0.15f && energy.gaugeFraction(128L) < energy.gaugeFraction(50_000L));

        ValueTypeVisual time = ValueTypeVisualRegistry.resolve("calculator_cirrus:time");
        assertEquals("t", time.unit());
        assertTrue(time.hasIconSupplier());
        assertTrue(time.formatDetailed(20L).contains("20t"));

        ValueTypeVisual heat = ValueTypeVisualRegistry.resolve("example_mod:furnace_heat");
        assertEquals(ValueTypeVisual.GlyphKind.flame, heat.fallbackGlyph());
        assertEquals("HU", heat.unit());

        ValueTypeVisual stress = ValueTypeVisualRegistry.resolve("create:stress_units");
        assertEquals(ValueTypeVisual.GlyphKind.gear, stress.fallbackGlyph());
        assertEquals("SU", stress.unit());

        Namespace plasmaId = Namespace.of("future_tech", "plasma_flux");
        ValueTypeVisualRegistry.register(
            plasmaId,
            ValueTypeVisual.builder(plasmaId.toString()).unit("PF").displayName(Component.literal("Plasma Flux"))
                    .accent(CirrusPalette::costC).fallbackGlyph(ValueTypeVisual.GlyphKind.wave).referenceScale(5_000L)
                    .build()
        );
        ValueTypeVisual plasma = ValueTypeVisualRegistry.resolve(plasmaId.toString());
        assertEquals("PF", plasma.unit());
        assertEquals(ValueTypeVisual.GlyphKind.wave, plasma.fallbackGlyph());
        assertEquals(CirrusPalette.obsidianBlueprint.costC(), plasma.accentColor(CirrusPalette.obsidianBlueprint));

        assertTrue(
            ValueTypeVisualRegistry
                    .isValueResourceKey(new ResourceKey("calculator_cirrus:energy", "fe", Optional.empty()))
        );
        assertFalse(ValueTypeVisualRegistry.isValueResourceKey(ResourceKey.item("minecraft:iron_ingot")));
        assertFalse(ValueTypeVisualRegistry.isValueResourceKey(ResourceKey.fluid("minecraft:water")));
    }

    @Test
    void databasePreviewLayoutSeparatesValueContentsFromItemSlotsIntoGaugeRail() {
        BasicValueType<Long> customHeat = new BasicValueType<>(Namespace.of("test", "heat")) {
            @Override
            public long getValue(Long content) {
                return content;
            }

            @Override
            public boolean matches(Long first, Long second) {
                return first.equals(second);
            }

            @Override
            public Long fromValue(long value) {
                return value;
            }

            @Override
            public Long copy(Long content) {
                return content;
            }
        };

        List<MaterialSlot> inputs = List.of(
            new MaterialSlot("Iron", 1, MaterialCategories.item),
            new MaterialSlot(
                "Energy",
                128,
                MaterialCategories.item,
                1.0,
                false,
                InputContent.fromValue(EnergyContentType.instance, EnergyValue.from(128L))
            )
        );
        List<MaterialSlot> outputs = List.of(
            new MaterialSlot("Plate", 1, MaterialCategories.item),
            new MaterialSlot(
                "Time",
                20,
                MaterialCategories.item,
                1.0,
                false,
                OutputContent.fromValue(TimeValueType.instance, TimeValue.fromTick(20))
            ),
            new MaterialSlot(
                "Heat",
                400,
                MaterialCategories.item,
                1.0,
                false,
                OutputContent.fromValue(customHeat, 400L)
            )
        );

        ExecutionRecipePreviewLayout.Layout layout = ExecutionRecipePreviewLayout.build(inputs, outputs);
        assertEquals(1, layout.inputs().size());
        assertEquals(1, layout.outputs().size());
        assertEquals(3, layout.valueCount());
        assertEquals(2, layout.inputSlotCount());
        assertEquals(3, layout.outputSlotCount());
        assertTrue(layout.valueDividerY() > 0);
        for (ExecutionRecipePreviewLayout.ValueEntryBox box : layout.valueEntries()) {
            assertTrue(box.y() > layout.valueDividerY());
            assertTrue(box.width() >= ValueTypeGaugeRenderer.databaseMeterMinWidth);
            assertEquals(ValueTypeGaugeRenderer.databaseMeterHeight, box.height());
        }
    }

    @Test
    void dagStepVisualsExtractsAndScalesRecipeValues() {
        ResourceKey ore = ResourceKey.item("minecraft:iron_ore");
        ResourceKey ingot = ResourceKey.item("minecraft:iron_ingot");
        CompiledRecipeKey key = new CompiledRecipeKey("test:smelter", Optional.of("smelt_iron"), 0L);
        CompiledRecipe recipe = new CompiledRecipe(
            0,
            key,
            RecipeRole.standardProduction,
            List.of(new InputSlot(0, 1L, Optional.empty(), List.of(ore), SlotMatchMode.identity)),
            List.of(),
            List.of(new OutputSlot(ingot, 1L)),
            new MachineRequirement(Optional.empty(), List.of("minecraft:furnace"), false),
            Map.of("calculator_cirrus:energy", 128.0, "calculator_cirrus:time", 20.0)
        );
        CompiledRecipeGraph graph = singleRecipeGraph(recipe);
        CraftStep step = new CraftStep(
            "c1",
            List.of(),
            key,
            4L,
            Optional.of("minecraft:furnace"),
            false,
            Map.of(0, new SlotBinding(ore, List.of())),
            Map.of(),
            CostVector.zero
        );

        StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer.resolve(step, graph, ingot, false);
        assertEquals(2, visuals.recipeValues().size());
        StepNodeCardRenderer.ValueTelemetry energy = visuals.recipeValues().stream()
                .filter(v -> v.typeId().equals("calculator_cirrus:energy")).findFirst().orElseThrow();
        assertEquals(128L, energy.perBatchAmount());
        assertEquals(512L, energy.totalAmount());
        StepNodeCardRenderer.ValueTelemetry time = visuals.recipeValues().stream()
                .filter(v -> v.typeId().equals("calculator_cirrus:time")).findFirst().orElseThrow();
        assertEquals(20L, time.perBatchAmount());
        assertEquals(80L, time.totalAmount());
    }

    private static CompiledRecipeGraph singleRecipeGraph(CompiledRecipe recipe) {
        return new CompiledRecipeGraph() {
            @Override
            public long catalogVersion() {
                return 1L;
            }

            @Override
            public SystemAdmission admissionOf(String systemId) {
                return SystemAdmission.enabled;
            }

            @Override
            public List<CompiledRecipe> producersOf(ResourceKey output) {
                return List.of(recipe);
            }

            @Override
            public List<CompiledRecipe> consumersOf(ResourceKey input) {
                return List.of(recipe);
            }

            @Override
            public List<CompiledCycleMacro> cycleMacrosProducing(ResourceKey output) {
                return List.of();
            }

            @Override
            public Optional<CompiledRecipe> recipeByCompactId(int compactId) {
                return compactId == recipe.compactId() ? Optional.of(recipe) : Optional.empty();
            }

            @Override
            public Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key) {
                return key.equals(recipe.key()) ? Optional.of(recipe) : Optional.empty();
            }

            @Override
            public Set<ResourceKey> allProducibleResources() {
                return Set.of();
            }

            @Override
            public SccTopology sccTopology() {
                return new SccTopology() {
                    @Override
                    public int sccIdOf(ResourceKey resource) {
                        return 0;
                    }

                    @Override
                    public int sccSize(int sccId) {
                        return 0;
                    }

                    @Override
                    public boolean isInNonTrivialScc(ResourceKey resource) {
                        return false;
                    }

                    @Override
                    public Set<ResourceKey> membersOfScc(int sccId) {
                        return Set.of();
                    }

                    @Override
                    public int largestSccSize() {
                        return 0;
                    }
                };
            }

            @Override
            public GraphCompilationStats stats() {
                return new GraphCompilationStats(0, 1, 0, 0, 0, 0, 0, 0);
            }
        };
    }
}
