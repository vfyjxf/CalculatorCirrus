package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionReport;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.recipe.system.BasicRecipeSystem;
import dev.vfyjxf.cirrus.ui.widget.flowchart.RecipeGraphBuilder;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialType;
import dev.vfyjxf.cloudlib.api.annotation.NotNullByDefault;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Temporary in-game recipe system for verifying {@link TargetSummaryCard} layout.
 * <p>
 * Provides a pre-built "Stargate Controller" style recipe with mixed content types:
 * <ul>
 *   <li>Item &amp; fluid inputs with varying stack sizes</li>
 *   <li>Non-consumed catalyst (chance&nbsp;=&nbsp;0)</li>
 *   <li>Probabilistic by-product output (chance&nbsp;&lt;&nbsp;1)</li>
 *   <li>Value-type outputs: processing time &amp; energy cost</li>
 * </ul>
 * <p>
 * <b>This class is for development-time testing only and should be removed before release.</b>
 *
 * @see TargetSummaryCard
 */
public final class DebugRecipeProvider {

    private DebugRecipeProvider() {}

    //region system info

    private static final Namespace SYSTEM_ID = Namespace.of(CirrusConstants.MOD_ID, "debug_layout");

    //endregion

    //region recipe factory

    /**
     * Creates a representative test recipe that exercises every section of the summary card.
     * <pre>
     *   Inputs (consumed):
     *     4× Nether Star          128× Gold Ingot
     *     16× Copper Ingot        2× Diamond
     *     64× Iron Ingot          256× Redstone
     *     32× Lapis Lazuli        48× Quartz
     *     24× Glowstone Dust      12× Blaze Powder
     *     8× Ender Pearl
     *     50 000 mB Lava          24 000 mB Water
     *     10 000 mB Lava
     *   Catalyst (not consumed):
     *     1× Emerald              1× Netherite Ingot
     *   Outputs:
     *     1× Beacon               16× Charcoal      (40%)
     *     32× Gold Nugget (25%)   48× Iron Nugget   (60%)
     *     1× Diamond (5%)         8× Bottle o' Ench.
     *     4× Glowstone
     *     32 000 mB Lava          8 000 mB Water
     *   Value outputs:
     *     12 800 ticks  processing time
     *     16 000 000    energy cost
     * </pre>
     */
    public static RecipeProjection createDebugRecipe() {
        MutableList<ClausedContent<InputContent>> inputs = MutableLists.empty();
        MutableList<ClausedContent<OutputContent>> outputs = MutableLists.empty();

        //region inputs — consumed materials

        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.NETHER_STAR, 4))));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.GOLD_INGOT), 128)));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT), 16)));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.DIAMOND, 2))));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT), 64)));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.REDSTONE), 256)));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.LAPIS_LAZULI), 32)));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.QUARTZ), 48)));
        // multi-variant input — any of these items is accepted, cycles in the slot
        inputs.add(faceted(InputContent.fromStackList(
                java.util.List.of(
                        ContentStack.fromStack(new ItemStack(Items.GLOWSTONE_DUST)),
                        ContentStack.fromStack(new ItemStack(Items.BLAZE_POWDER)),
                        ContentStack.fromStack(new ItemStack(Items.ENDER_PEARL))
                ), 24
        )));
        // typed multi-variant input
        inputs.add(faceted(InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                java.util.List.of(
                        new ItemStack(Items.OAK_LOG),
                        new ItemStack(Items.BIRCH_LOG),
                        new ItemStack(Items.SPRUCE_LOG),
                        new ItemStack(Items.DARK_OAK_LOG)
                ), 64
        )));
        inputs.add(faceted(InputContent.fromFluidStack(new FluidStack(Fluids.LAVA, 50_000))));
        inputs.add(faceted(InputContent.fromFluidStack(new FluidStack(Fluids.WATER, 24_000))));
        inputs.add(faceted(InputContent.fromFluidStack(new FluidStack(Fluids.LAVA, 10_000))));

        //endregion

        //region catalyst — not consumed (chance = 0)

        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.EMERALD), 1, 0f)));
        inputs.add(faceted(InputContent.fromItemStack(new ItemStack(Items.NETHERITE_INGOT), 1, 0f)));

        //endregion

        //region outputs — stack products

        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.BEACON), 1)));
        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.CHARCOAL), 16, 0.4f)));
        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.GOLD_NUGGET), 32, 0.25f)));
        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.IRON_NUGGET), 48, 0.6f)));
        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.05f)));
        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.EXPERIENCE_BOTTLE), 8)));
        outputs.add(faceted(OutputContent.fromItemStack(new ItemStack(Items.GLOWSTONE), 4)));
        outputs.add(faceted(OutputContent.fromFluidStack(new FluidStack(Fluids.LAVA, 32_000))));
        outputs.add(faceted(OutputContent.fromFluidStack(new FluidStack(Fluids.WATER, 8_000))));

        //endregion

        //region outputs — value specs

        outputs.add(faceted(OutputContent.fromTimeTick(12_800)));
        outputs.add(faceted(OutputContent.fromEnergy(16_000_000L)));

        //endregion

        var recipe = new DebugRecipe(inputs, outputs);
        return recipe.toRecipeProjection();
    }

    /**
     * Convenience: build a {@link ProjectedRecipe} ready for the summary card.
     */
    public static ProjectedRecipe createDebugView() {
        return createDebugReport().recipe();
    }

    /**
     * Convenience: build a {@link ProjectionReport} with detailed clause effects.
     */
    public static ProjectionReport createDebugReport() {
        RecipeProjection raw = createDebugRecipe();
        var pipeline = new ProjectionEngine();
        return pipeline.projectReport(new ExecutionContext(), raw);
    }

    //endregion

    //region ────── Complex production chain (ProcessGraph) ──────

    /**
     * Builds a large-scale production chain as a {@link ProcessGraph}.
     * <p>
     * Architecture designed for genuinely deep serial dependency chains:
     * <pre>
     *   Element chains (5×12 = 60 nodes, parallel with cross-links)
     *   Catalyst chains (3×8 = 24 nodes, cross-ref elements)
     *   Alloy + Plate utility (10+10 = 20 nodes, merge element chains)
     *   Circuit chain (50 serial steps, each needs previous + element/catalyst/plate)
     *   Superconductor (10 nodes, merge circuit + plate + catalyst)
     *   Machine chain (30 serial steps, each needs previous + circuit + superconductor)
     *   Module chain (20 serial steps, each needs previous + machine + circuit)
     *   System chain (15 serial steps, each needs previous + module + machine)
     *   Final cascade (10 serial steps, each needs previous + system + circuit)
     * </pre>
     * Critical path: Element(12) → Circuit(50) → Machine(30) → Module(20) → System(15) → Final(10) = 137 steps.
     * Total nodes: ~239. Extensive cross-dependencies between chains.
     */
    public static ProcessGraph createDebugProcessGraph() {
        var builder = new RecipeGraphBuilder.ChainBuilder();

        // ── Icon pools ──
        ItemStack[] oreIcons = {
                new ItemStack(Items.RAW_IRON), new ItemStack(Items.RAW_COPPER),
                new ItemStack(Items.RAW_GOLD), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.EMERALD), new ItemStack(Items.LAPIS_LAZULI),
                new ItemStack(Items.REDSTONE), new ItemStack(Items.QUARTZ),
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.COAL)
        };
        ItemStack[] dustIcons = {
                new ItemStack(Items.IRON_NUGGET), new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.BONE_MEAL), new ItemStack(Items.GUNPOWDER),
                new ItemStack(Items.SUGAR), new ItemStack(Items.BLAZE_POWDER)
        };
        ItemStack[] ingotIcons = {
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.NETHERITE_INGOT)
        };
        ItemStack[] machineIcons = {
                new ItemStack(Items.FURNACE), new ItemStack(Items.BLAST_FURNACE),
                new ItemStack(Items.SMOKER), new ItemStack(Items.STONECUTTER),
                new ItemStack(Items.ANVIL), new ItemStack(Items.GRINDSTONE),
                new ItemStack(Items.BREWING_STAND), new ItemStack(Items.ENCHANTING_TABLE),
                new ItemStack(Items.SMITHING_TABLE), new ItemStack(Items.CRAFTING_TABLE),
                new ItemStack(Items.PISTON), new ItemStack(Items.DROPPER)
        };
        ItemStack[] circuitIcons = {
                new ItemStack(Items.REPEATER), new ItemStack(Items.COMPARATOR),
                new ItemStack(Items.DAYLIGHT_DETECTOR), new ItemStack(Items.OBSERVER)
        };

        // ═══════════════════════════════════════════════════
        //  PHASE 1: Element Chains — 5 chains × 12 serial steps = 60 nodes
        //  Each chain: elem-X Grade 0 → Grade 1 → ... → Grade 12
        //  Cross-link: every 4th step also needs output from adjacent chain
        // ═══════════════════════════════════════════════════
        String[] elements = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon"};
        int ELEM_STEPS = 12;
        for (int c = 0; c < elements.length; c++) {
            for (int s = 0; s < ELEM_STEPS; s++) {
                List<MaterialSlot> inputs = new java.util.ArrayList<>();
                if (s == 0) {
                    inputs.add(mat("Raw " + elements[c] + " Ore", 64,
                            MaterialType.ITEM, oreIcons[c * 2]));
                } else {
                    inputs.add(mat(elements[c] + " Grade " + s, 32,
                            MaterialType.ITEM, dustIcons[(c + s) % dustIcons.length]));
                }
                // Cross-dep every 4 steps: need output from adjacent chain (earlier step)
                if (s > 0 && s % 4 == 0) {
                    int crossC = (c + 1) % elements.length;
                    inputs.add(mat(elements[crossC] + " Grade " + (s - 2), 8,
                            MaterialType.ITEM, dustIcons[crossC]));
                }
                // Some steps need fluid
                if (s % 6 == 3) {
                    inputs.add(new MaterialSlot("Solvent", 4000, MaterialType.FLUID, 1.0, false,
                            null, new FluidStack(Fluids.WATER, 4000)));
                }

                builder.addNode(new ProcessNode(
                        "elem_" + c + "_" + s,
                        elements[c] + " Refining Step " + (s + 1), "Refinery",
                        inputs,
                        List.of(
                                mat(elements[c] + " Grade " + (s + 1), 24,
                                        MaterialType.ITEM, dustIcons[(c + s + 1) % dustIcons.length]),
                                matChance(elements[c] + " Waste", 4, MaterialType.ITEM, 0.2,
                                        new ItemStack(Items.GRAVEL, 4))
                        ),
                        80 + s * 15, 16 * (s / 3 + 1), s / 4,
                        List.of(new MachineInfo("Refinery T" + (s / 4 + 1),
                                machineIcons[s % machineIcons.length]))
                ), "Element " + elements[c]);
            }
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 2: Catalyst Chains — 3 chains × 8 serial steps = 24 nodes
        //  Cross-ref: every 3rd step uses an element chain output
        // ═══════════════════════════════════════════════════
        String[] catalysts = {"Flux", "Reagent", "Enzyme"};
        int CAT_STEPS = 8;
        for (int c = 0; c < catalysts.length; c++) {
            for (int s = 0; s < CAT_STEPS; s++) {
                List<MaterialSlot> inputs = new java.util.ArrayList<>();
                if (s == 0) {
                    inputs.add(mat("Raw " + catalysts[c], 32,
                            MaterialType.ITEM, oreIcons[c + 5]));
                } else {
                    inputs.add(mat(catalysts[c] + " Grade " + s, 16,
                            MaterialType.ITEM, dustIcons[(c + s) % dustIcons.length]));
                }
                // Cross-ref element chain output
                if (s % 3 == 2) {
                    int elemC = (c + s) % elements.length;
                    int elemG = Math.min(s + 2, ELEM_STEPS);
                    inputs.add(mat(elements[elemC] + " Grade " + elemG, 4,
                            MaterialType.ITEM, dustIcons[elemC]));
                }

                builder.addNode(new ProcessNode(
                        "cat_" + c + "_" + s,
                        catalysts[c] + " Synthesis Step " + (s + 1), "Catalyst Plant",
                        inputs,
                        List.of(mat(catalysts[c] + " Grade " + (s + 1), 12,
                                MaterialType.ITEM, dustIcons[(c + s + 1) % dustIcons.length])),
                        100 + s * 20, 32 * (c + 1), s / 3,
                        List.of(new MachineInfo("Catalyst Plant T" + (s / 3 + 1),
                                machineIcons[(s + 6) % machineIcons.length]))
                ), "Catalyst " + catalysts[c]);
            }
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 3: Alloy + Plate utility — 10 + 10 = 20 nodes
        //  Alloys merge two element chains; Plates consume alloys
        // ═══════════════════════════════════════════════════
        for (int i = 0; i < 10; i++) {
            int eA = i % elements.length;
            int eB = (i + 2) % elements.length;
            int grade = Math.min(i + 3, ELEM_STEPS);
            builder.addNode(new ProcessNode(
                    "alloy_" + i,
                    "Compound Alloy " + (i + 1) + " Mixing", "Industrial Mixer",
                    List.of(
                            mat(elements[eA] + " Grade " + grade, 16,
                                    MaterialType.ITEM, ingotIcons[eA % ingotIcons.length]),
                            mat(elements[eB] + " Grade " + grade, 16,
                                    MaterialType.ITEM, ingotIcons[eB % ingotIcons.length])
                    ),
                    List.of(mat("Compound Alloy " + (i + 1), 24,
                            MaterialType.ITEM, ingotIcons[i % ingotIcons.length])),
                    200, 96, 2,
                    List.of(new MachineInfo("Industrial Mixer", machineIcons[4]))
            ), "Alloys");
        }
        for (int i = 0; i < 10; i++) {
            builder.addNode(new ProcessNode(
                    "cplate_" + i,
                    "Compound Plate " + (i + 1) + " Rolling", "Plate Bender",
                    List.of(mat("Compound Alloy " + (i + 1), 8,
                            MaterialType.ITEM, ingotIcons[i % ingotIcons.length])),
                    List.of(mat("Compound Plate " + (i + 1), 8,
                            MaterialType.ITEM, new ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, 8))),
                    80, 32, 2,
                    List.of(new MachineInfo("Plate Bender", machineIcons[4]))
            ), "Alloys");
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 4: Circuit Chain — 50 SERIAL steps
        //  Each: previous circuit + element + sometimes catalyst/plate
        //  Critical path contribution: 50 steps (serial)
        // ═══════════════════════════════════════════════════
        int CIRCUIT_STEPS = 50;
        for (int s = 0; s < CIRCUIT_STEPS; s++) {
            List<MaterialSlot> inputs = new java.util.ArrayList<>();
            // Serial dependency: previous circuit
            if (s > 0) {
                inputs.add(mat("Circuit Lv" + s, 2,
                        MaterialType.ITEM, circuitIcons[(s - 1) % circuitIcons.length]));
            }
            // Element input (pulls from various element chains)
            int elemC = s % elements.length;
            int elemG = Math.min(s / 5 + 1, ELEM_STEPS);
            inputs.add(mat(elements[elemC] + " Grade " + elemG, 4,
                    MaterialType.ITEM, dustIcons[(elemC + s) % dustIcons.length]));
            // Catalyst (every 7th step)
            if (s % 7 == 3) {
                int catC = s % catalysts.length;
                int catG = Math.min(s / 8 + 1, CAT_STEPS);
                inputs.add(mat(catalysts[catC] + " Grade " + catG, 2,
                        MaterialType.ITEM, dustIcons[catC]));
            }
            // Compound Plate (every 5th step, starting at step 4)
            if (s % 5 == 4) {
                int plateIdx = s / 5 + 1; // 1..10
                inputs.add(mat("Compound Plate " + plateIdx, 2,
                        MaterialType.ITEM, new ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, 2)));
            }
            // Fluid (every 10th step)
            if (s % 10 == 5) {
                inputs.add(new MaterialSlot("Etching Acid", 2000 * (s / 10 + 1),
                        MaterialType.FLUID, 1.0, false, null, new FluidStack(Fluids.LAVA, 2000)));
            }

            builder.addNode(new ProcessNode(
                    "circuit_" + s,
                    "Circuit Assembly Lv" + (s + 1), "Circuit Assembler",
                    inputs,
                    List.of(
                            mat("Circuit Lv" + (s + 1), 2,
                                    MaterialType.ITEM, circuitIcons[s % circuitIcons.length]),
                            matChance("Solder Waste", 2, MaterialType.ITEM, 0.3,
                                    new ItemStack(Items.IRON_NUGGET, 2))
                    ),
                    100 + s * 20, 32 + s * 8, s / 10 + 1,
                    List.of(new MachineInfo("Circuit Assembler T" + (s / 10 + 1),
                            machineIcons[(s + 8) % machineIcons.length]))
            ), "Circuits");
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 5: Superconductors — 10 nodes
        //  Merge circuit + compound plate + catalyst (cross-dep hub)
        // ═══════════════════════════════════════════════════
        for (int i = 0; i < 10; i++) {
            int circLv = Math.min(i * 5 + 5, CIRCUIT_STEPS);
            builder.addNode(new ProcessNode(
                    "supercon_" + i,
                    "Superconductor Lv" + (i + 1) + " Fabrication", "Cryogenic Chamber",
                    List.of(
                            mat("Circuit Lv" + circLv, 2,
                                    MaterialType.ITEM, circuitIcons[i % circuitIcons.length]),
                            mat("Compound Plate " + (i + 1), 4,
                                    MaterialType.ITEM, new ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, 4)),
                            mat(catalysts[i % catalysts.length] + " Grade " + CAT_STEPS, 2,
                                    MaterialType.ITEM, dustIcons[i % dustIcons.length]),
                            new MaterialSlot("Liquid Nitrogen", 8000, MaterialType.FLUID, 1.0, false,
                                    null, new FluidStack(Fluids.WATER, 8000))
                    ),
                    List.of(mat("Superconductor Lv" + (i + 1), 2,
                            MaterialType.ITEM, new ItemStack(Items.LIGHTNING_ROD, 2))),
                    300 + i * 30, 128 + i * 16, 3,
                    List.of(new MachineInfo("Cryogenic Chamber",
                            machineIcons[(i + 10) % machineIcons.length]))
            ), "Superconductors");
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 6: Machine Chain — 30 SERIAL steps
        //  Each: previous machine + circuit + element
        //  + superconductor every 3rd step
        //  Critical path contribution: 30 steps (serial)
        // ═══════════════════════════════════════════════════
        int MACHINE_STEPS = 30;
        for (int s = 0; s < MACHINE_STEPS; s++) {
            List<MaterialSlot> inputs = new java.util.ArrayList<>();
            // Serial dependency: previous machine
            if (s > 0) {
                inputs.add(mat("Machine Lv" + s, 1,
                        MaterialType.ITEM, machineIcons[(s - 1) % machineIcons.length]));
            }
            // Circuit input (progressively higher tiers)
            int circLv = Math.min(s * 2 + 1, CIRCUIT_STEPS);
            inputs.add(mat("Circuit Lv" + circLv, 4,
                    MaterialType.ITEM, circuitIcons[s % circuitIcons.length]));
            // Element (highest grade)
            int elemC = s % elements.length;
            inputs.add(mat(elements[elemC] + " Grade " + ELEM_STEPS, 8,
                    MaterialType.ITEM, ingotIcons[elemC % ingotIcons.length]));
            // Superconductor every 3rd step
            if (s % 3 == 0) {
                int scIdx = s / 3 + 1; // 1..10
                if (scIdx <= 10) {
                    inputs.add(mat("Superconductor Lv" + scIdx, 2,
                            MaterialType.ITEM, new ItemStack(Items.LIGHTNING_ROD, 2)));
                }
            }
            // Catalyst cross-dep
            if (s % 5 == 2) {
                int catC = s % catalysts.length;
                inputs.add(mat(catalysts[catC] + " Grade " + CAT_STEPS, 2,
                        MaterialType.ITEM, dustIcons[catC]));
            }

            builder.addNode(new ProcessNode(
                    "machine_" + s,
                    "Machine Assembly Lv" + (s + 1), "Assembly Line",
                    inputs,
                    List.of(mat("Machine Lv" + (s + 1), 1,
                            MaterialType.ITEM, machineIcons[s % machineIcons.length])),
                    200 + s * 30, 64 + s * 16, s / 6 + 2,
                    List.of(new MachineInfo("Assembly Line T" + (s / 6 + 1),
                            machineIcons[(s + 4) % machineIcons.length]))
            ), "Machines");
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 7: Module Chain — 20 SERIAL steps
        //  Each: previous module + machine + circuit + element
        //  Critical path contribution: 20 steps (serial)
        // ═══════════════════════════════════════════════════
        int MODULE_STEPS = 20;
        for (int s = 0; s < MODULE_STEPS; s++) {
            List<MaterialSlot> inputs = new java.util.ArrayList<>();
            // Serial dependency
            if (s > 0) {
                inputs.add(mat("Module Lv" + s, 1,
                        MaterialType.ITEM, machineIcons[(s - 1 + 6) % machineIcons.length]));
            }
            // Machine input
            int machineLv = Math.min(s + 10, MACHINE_STEPS);
            inputs.add(mat("Machine Lv" + machineLv, 2,
                    MaterialType.ITEM, machineIcons[s % machineIcons.length]));
            // Circuit input
            int circLv = Math.min(s * 3 + 10, CIRCUIT_STEPS);
            inputs.add(mat("Circuit Lv" + circLv, 8,
                    MaterialType.ITEM, circuitIcons[s % circuitIcons.length]));
            // Element cross-dep
            int elemC = s % elements.length;
            inputs.add(mat(elements[elemC] + " Grade " + ELEM_STEPS, 16,
                    MaterialType.ITEM, ingotIcons[elemC % ingotIcons.length]));
            // Compound alloy cross-dep (every 4th step)
            if (s % 4 == 2) {
                int alloyIdx = s / 4 + 1;
                if (alloyIdx <= 10) {
                    inputs.add(mat("Compound Alloy " + alloyIdx, 8,
                            MaterialType.ITEM, ingotIcons[alloyIdx % ingotIcons.length]));
                }
            }

            builder.addNode(new ProcessNode(
                    "module_" + s,
                    "Module Integration Lv" + (s + 1), "Modular Factory",
                    inputs,
                    List.of(mat("Module Lv" + (s + 1), 1,
                            MaterialType.ITEM, machineIcons[(s + 6) % machineIcons.length])),
                    400 + s * 40, 128 + s * 32, s / 5 + 3,
                    List.of(new MachineInfo("Modular Factory T" + (s / 5 + 1),
                            machineIcons[(s + 7) % machineIcons.length]))
            ), "Modules");
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 8: System Chain — 15 SERIAL steps
        //  Each: previous system + module + machine + circuit
        //  Critical path contribution: 15 steps (serial)
        // ═══════════════════════════════════════════════════
        int SYSTEM_STEPS = 15;
        for (int s = 0; s < SYSTEM_STEPS; s++) {
            List<MaterialSlot> inputs = new java.util.ArrayList<>();
            // Serial dependency
            if (s > 0) {
                inputs.add(mat("System Lv" + s, 1,
                        MaterialType.ITEM, new ItemStack(Items.BEACON)));
            }
            // Module
            int moduleLv = Math.min(s + 5, MODULE_STEPS);
            inputs.add(mat("Module Lv" + moduleLv, 2,
                    MaterialType.ITEM, machineIcons[(s + 6) % machineIcons.length]));
            // Machine (cross-dep)
            int machineLv = Math.min(s * 2 + 15, MACHINE_STEPS);
            inputs.add(mat("Machine Lv" + machineLv, 1,
                    MaterialType.ITEM, machineIcons[s % machineIcons.length]));
            // Circuit
            int circLv = Math.min(s * 3 + 30, CIRCUIT_STEPS);
            inputs.add(mat("Circuit Lv" + circLv, 16,
                    MaterialType.ITEM, circuitIcons[s % circuitIcons.length]));
            // Superconductor cross-dep (every 3rd step)
            if (s % 3 == 0) {
                int scIdx = Math.min(s / 3 + 5, 10);
                inputs.add(mat("Superconductor Lv" + scIdx, 4,
                        MaterialType.ITEM, new ItemStack(Items.LIGHTNING_ROD, 4)));
            }
            // Catalyst cross-dep
            if (s % 5 == 0) {
                int catC = s % catalysts.length;
                inputs.add(new MaterialSlot(catalysts[catC] + " Grade " + CAT_STEPS, 4,
                        MaterialType.CATALYST, 1.0, true, dustIcons[catC], null));
            }

            builder.addNode(new ProcessNode(
                    "system_" + s,
                    "System Assembly Lv" + (s + 1), "System Factory",
                    inputs,
                    List.of(
                            mat("System Lv" + (s + 1), 1,
                                    MaterialType.ITEM, new ItemStack(Items.BEACON)),
                            new MaterialSlot("Exhaust Gas", 2000 * (s + 1),
                                    MaterialType.FLUID, 0.8, false,
                                    null, new FluidStack(Fluids.LAVA, 2000))
                    ),
                    600 + s * 60, 256 + s * 64, s / 3 + 4,
                    List.of(new MachineInfo("System Factory T" + (s / 3 + 1),
                            machineIcons[(s + 9) % machineIcons.length]))
            ), "Systems");
        }

        // ═══════════════════════════════════════════════════
        //  PHASE 9: Final Cascade — 10 SERIAL steps
        //  Each: previous component + system + circuit + elements
        //  Critical path contribution: 10 steps (serial)
        //  Total critical path: 12+50+30+20+15+10 = 137 steps
        // ═══════════════════════════════════════════════════
        int FINAL_STEPS = 10;
        for (int s = 0; s < FINAL_STEPS; s++) {
            List<MaterialSlot> inputs = new java.util.ArrayList<>();
            // Serial dependency: previous component
            if (s > 0) {
                inputs.add(mat("Stargate Component " + s, 1,
                        MaterialType.ITEM, new ItemStack(Items.NETHER_STAR)));
            }
            // System
            int sysLv = Math.min(s + 5, SYSTEM_STEPS);
            inputs.add(mat("System Lv" + sysLv, 2,
                    MaterialType.ITEM, new ItemStack(Items.BEACON)));
            // Highest-tier circuit
            inputs.add(mat("Circuit Lv" + CIRCUIT_STEPS, 32 * (s + 1),
                    MaterialType.ITEM, circuitIcons[3]));
            // Multiple elements (creates fan-in from element chains)
            for (int e = 0; e < elements.length; e++) {
                if ((e + s) % 3 == 0) {
                    inputs.add(mat(elements[e] + " Grade " + ELEM_STEPS, 32,
                            MaterialType.ITEM, ingotIcons[e % ingotIcons.length]));
                }
            }
            // Superconductor
            int scIdx = Math.min(s + 1, 10);
            inputs.add(mat("Superconductor Lv" + scIdx, 4,
                    MaterialType.ITEM, new ItemStack(Items.LIGHTNING_ROD, 4)));
            // Fluid
            inputs.add(new MaterialSlot("Cryo-Coolant", 16000 * (s + 1),
                    MaterialType.FLUID, 1.0, false,
                    null, new FluidStack(Fluids.WATER, 16000)));
            // Catalyst
            inputs.add(new MaterialSlot("Nether Star Catalyst", 1,
                    MaterialType.CATALYST, 1.0, true,
                    new ItemStack(Items.NETHER_STAR), null));

            String outName = s == FINAL_STEPS - 1
                    ? "Stargate Controller" : "Stargate Component " + (s + 1);
            ItemStack outIcon = s == FINAL_STEPS - 1
                    ? new ItemStack(Items.BEACON) : new ItemStack(Items.NETHER_STAR);

            builder.addNode(new ProcessNode(
                    "final_" + s,
                    outName + " Assembly", "Mega Assembly Line",
                    inputs,
                    List.of(
                            mat(outName, 1, MaterialType.ITEM, outIcon),
                            matChance("Scrap", 4 * (s + 1), MaterialType.ITEM, 0.2,
                                    new ItemStack(Items.IRON_NUGGET, 4))
                    ),
                    2000 + s * 300, 1024 + s * 128, 5,
                    List.of(new MachineInfo("Mega Assembly Line", machineIcons[7]))
            ), "Final Assembly");
        }

        return builder.build();
    }

    /** Convenience: MaterialSlot with item stack. */
    private static MaterialSlot mat(String name, int amount, MaterialType type, ItemStack icon) {
        return new MaterialSlot(name, amount, type, 1.0, false, icon, null);
    }

    /** Convenience: MaterialSlot with chance. */
    private static MaterialSlot matChance(String name, int amount, MaterialType type,
                                          double chance, ItemStack icon) {
        return new MaterialSlot(name, amount, type, chance, false, icon, null);
    }

    //region helpers

    private static <T extends dev.vfyjxf.cirrus.api.recipe.content.RecipeContent>
    ClausedContent<T> faceted(T content) {
        return ClausedContent.from(content);
    }

    //endregion

    //region DebugRecipe — minimal record

    record DebugRecipe(
            MutableList<ClausedContent<InputContent>> inputs,
            MutableList<ClausedContent<OutputContent>> outputs
    ) {
        RecipeProjection toRecipeProjection() {
            return new RecipeProjection(
                    RecipeEntry.of(DebugRecipeSystem.instance, this),
                    inputs.toImmutable(),
                    outputs.toImmutable()
            );
        }
    }

    //endregion

    //region DebugRecipeSystem — stub system

    @NotNullByDefault
    static final class DebugRecipeSystem extends BasicRecipeSystem<DebugRecipe> {

        static final DebugRecipeSystem instance = new DebugRecipeSystem();

        private DebugRecipeSystem() {
            super(SYSTEM_ID);
        }

        @Override
        public Class<?> basicType() {
            return DebugRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(DebugRecipe recipe) {
            return null;
        }

        @Override
        public @Nullable DebugRecipe findRecipe(String uniqueId) {
            return null;
        }

        @Override
        public @Nullable DebugRecipe findRecipe(ResourceLocation registryName) {
            return null;
        }

        @Override
        public int hash(DebugRecipe recipe) {
            return recipe.hashCode();
        }

        @Override
        public boolean sameRecipe(DebugRecipe r1, DebugRecipe r2) {
            return r1.equals(r2);
        }

        @Override
        public RecipeProjection resolveRecipe(DebugRecipe recipe) {
            return recipe.toRecipeProjection();
        }

        @Override
        public @Unmodifiable RichIterable<DebugRecipe> allRecipes() {
            return MutableLists.empty();
        }
    }

    //endregion

    //region plugin — auto-registration

    @PluginMarker
    public static final class DebugRecipePlugin implements CirrusPlugin {

        @Override
        public @NotNull Namespace pluginId() {
            return Namespace.of(CirrusConstants.MOD_ID, "debug_layout");
        }

        @Override
        public void registerRecipeSystem(RecipeSystemRegister register) {
            register.registerSystem(DebugRecipeSystem.instance);
        }
    }

    //endregion

}
