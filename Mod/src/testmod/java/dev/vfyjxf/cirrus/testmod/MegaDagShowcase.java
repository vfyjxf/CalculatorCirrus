package dev.vfyjxf.cirrus.testmod;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Self-contained 48-item, 58-recipe cross-linked Mega-DAG showcase for stress-testing large complex DAGs.
 */
final class MegaDagShowcase {

    private static final Map<String, DeferredItem<Item>> registry = new LinkedHashMap<>();
    private static boolean itemsRegistered;

    private MegaDagShowcase() {}

    static synchronized void registerItems() {
        if (itemsRegistered) {
            return;
        }
        itemsRegistered = true;
        // Tier 0: Raw materials (10)
        reg("mega_iron_ore");
        reg("mega_copper_ore");
        reg("mega_titanium_ore");
        reg("mega_quartz_crystal");
        reg("mega_silicon_sand");
        reg("mega_coal_dust");
        reg("mega_sulfur_dust");
        reg("mega_uranium_ore");
        reg("mega_platinum_ore");
        reg("mega_void_shard");
        // Tier 1: Processed materials (12)
        reg("mega_steel_ingot");
        reg("mega_copper_wire");
        reg("mega_titanium_plate");
        reg("mega_refined_silicon");
        reg("mega_silicon_wafer");
        reg("mega_sulfuric_acid_cell");
        reg("mega_carbon_fiber");
        reg("mega_enriched_uranium");
        reg("mega_platinum_ingot");
        reg("mega_superconductor_wire");
        reg("mega_ceramic_insulator");
        reg("mega_void_essence");
        // Tier 2: Cross-linked sub-assemblies (12)
        reg("mega_circuit_board");
        reg("mega_logic_chip");
        reg("mega_power_capacitor");
        reg("mega_heat_exchanger");
        reg("mega_magnetic_coil");
        reg("mega_laser_diode");
        reg("mega_titanium_frame");
        reg("mega_coolant_core");
        reg("mega_plasma_injector");
        reg("mega_shield_emitter");
        reg("mega_quantum_bus");
        reg("mega_flux_battery");
        // Tier 3: Diamond-DAG major modules (8)
        reg("mega_compute_rack");
        reg("mega_fusion_coil");
        reg("mega_reactor_chamber");
        reg("mega_field_stabilizer");
        reg("mega_antimatter_cell");
        reg("mega_neural_matrix");
        reg("mega_warp_condenser");
        reg("mega_hyper_frame");
        // Tier 4: Apex subsystems (4)
        reg("mega_quantum_core");
        reg("mega_fusion_reactor");
        reg("mega_spatial_anchor");
        reg("mega_chronos_relay");
        // Tier 5: Ultimate stress-test targets (2)
        reg("mega_singularity_engine");
        reg("mega_omniverse_core");
    }

    static void registerRecipeSystems(RecipeSystemRegister register) {
        register.registerSystem(
            new MegaRecipeSystem(Namespace.of(TestMod.modId, "mega_smelter"), cached(MegaDagShowcase::smelterRecipes))
        );
        register.registerSystem(
            new MegaRecipeSystem(Namespace.of(TestMod.modId, "mega_chemical"), cached(MegaDagShowcase::chemicalRecipes))
        );
        register.registerSystem(
            new MegaRecipeSystem(
                Namespace.of(TestMod.modId, "mega_assembler"),
                cached(MegaDagShowcase::assemblerRecipes)
            )
        );
        register.registerSystem(
            new MegaRecipeSystem(Namespace.of(TestMod.modId, "mega_collider"), cached(MegaDagShowcase::colliderRecipes))
        );
    }

    private static void reg(String name) {
        registry.put(name, TestMod.registerShowcaseItem(name));
    }

    private static Item item(String name) {
        DeferredItem<Item> holder = registry.get(name);
        if (holder == null) {
            throw new IllegalStateException("Unknown MegaDagShowcase item: " + name);
        }
        return holder.get();
    }

    private static InputContent in(String name, int amount) {
        return InputContent.fromStack(ContentStack.fromContent(item(name), amount));
    }

    private static OutputContent out(String name, int amount) {
        return OutputContent.fromStack(ContentStack.fromContent(item(name), amount));
    }

    private static List<MegaRecipe> smelterRecipes() {
        return List.of(
            r(
                "steel_standard",
                List.of(in("mega_iron_ore", 2), in("mega_coal_dust", 2)),
                List.of(out("mega_steel_ingot", 2))
            ),
            r(
                "steel_rich_alt",
                List.of(in("mega_iron_ore", 3), in("mega_coal_dust", 1), in("mega_sulfur_dust", 1)),
                List.of(out("mega_steel_ingot", 4))
            ),
            r("copper_wire", List.of(in("mega_copper_ore", 2)), List.of(out("mega_copper_wire", 4))),
            r(
                "titanium_plate",
                List.of(in("mega_titanium_ore", 2), in("mega_coal_dust", 1)),
                List.of(out("mega_titanium_plate", 2))
            ),
            r(
                "refined_silicon",
                List.of(in("mega_silicon_sand", 4), in("mega_coal_dust", 1)),
                List.of(out("mega_refined_silicon", 2))
            ),
            r(
                "carbon_fiber",
                List.of(in("mega_coal_dust", 4), in("mega_steel_ingot", 1)),
                List.of(out("mega_carbon_fiber", 2))
            ),
            r(
                "platinum_ingot",
                List.of(in("mega_platinum_ore", 2), in("mega_coal_dust", 2)),
                List.of(out("mega_platinum_ingot", 2))
            ),
            r(
                "ceramic_insulator",
                List.of(in("mega_silicon_sand", 2), in("mega_quartz_crystal", 2)),
                List.of(out("mega_ceramic_insulator", 4))
            ),
            r(
                "superconductor_wire_smelt",
                List.of(in("mega_copper_wire", 2), in("mega_platinum_ingot", 1), in("mega_quartz_crystal", 2)),
                List.of(out("mega_superconductor_wire", 2))
            )
        );
    }

    private static List<MegaRecipe> chemicalRecipes() {
        return List.of(
            r(
                "sulfuric_acid",
                List.of(in("mega_sulfur_dust", 3), in("mega_quartz_crystal", 1)),
                List.of(out("mega_sulfuric_acid_cell", 2))
            ),
            r(
                "silicon_wafer",
                List.of(in("mega_refined_silicon", 2), in("mega_sulfuric_acid_cell", 1)),
                List.of(out("mega_silicon_wafer", 2))
            ),
            r(
                "enriched_uranium",
                List.of(in("mega_uranium_ore", 3), in("mega_sulfuric_acid_cell", 2)),
                List.of(out("mega_enriched_uranium", 1))
            ),
            r(
                "void_essence",
                List.of(in("mega_void_shard", 2), in("mega_sulfuric_acid_cell", 1), in("mega_quartz_crystal", 2)),
                List.of(out("mega_void_essence", 2))
            ),
            r(
                "superconductor_chem_alt",
                List.of(in("mega_copper_wire", 4), in("mega_platinum_ingot", 1), in("mega_sulfuric_acid_cell", 1)),
                List.of(out("mega_superconductor_wire", 4))
            ),
            r(
                "coolant_core",
                List.of(
                    in("mega_sulfuric_acid_cell", 2),
                    in("mega_ceramic_insulator", 2),
                    in("mega_titanium_plate", 1)
                ),
                List.of(out("mega_coolant_core", 1))
            ),
            r(
                "flux_battery_chem",
                List.of(
                    in("mega_power_capacitor", 2),
                    in("mega_enriched_uranium", 1),
                    in("mega_sulfuric_acid_cell", 2)
                ),
                List.of(out("mega_flux_battery", 1))
            )
        );
    }

    private static List<MegaRecipe> assemblerRecipes() {
        return List.of(
            // Tier 2: Heavy shared-intermediate cross-linking
            r(
                "circuit_board",
                List.of(in("mega_silicon_wafer", 1), in("mega_copper_wire", 3), in("mega_ceramic_insulator", 2)),
                List.of(out("mega_circuit_board", 2))
            ),
            r(
                "logic_chip",
                List.of(in("mega_circuit_board", 1), in("mega_refined_silicon", 2), in("mega_platinum_ingot", 1)),
                List.of(out("mega_logic_chip", 2))
            ),
            r(
                "logic_chip_bulk_alt",
                List.of(in("mega_circuit_board", 2), in("mega_silicon_wafer", 2), in("mega_copper_wire", 4)),
                List.of(out("mega_logic_chip", 4))
            ),
            r(
                "power_capacitor",
                List.of(in("mega_copper_wire", 4), in("mega_ceramic_insulator", 2), in("mega_steel_ingot", 2)),
                List.of(out("mega_power_capacitor", 2))
            ),
            r(
                "heat_exchanger",
                List.of(in("mega_titanium_plate", 3), in("mega_copper_wire", 2), in("mega_steel_ingot", 2)),
                List.of(out("mega_heat_exchanger", 1))
            ),
            r(
                "magnetic_coil",
                List.of(in("mega_superconductor_wire", 2), in("mega_steel_ingot", 2), in("mega_ceramic_insulator", 1)),
                List.of(out("mega_magnetic_coil", 2))
            ),
            r(
                "laser_diode",
                List.of(in("mega_quartz_crystal", 4), in("mega_platinum_ingot", 1), in("mega_circuit_board", 1)),
                List.of(out("mega_laser_diode", 2))
            ),
            r(
                "titanium_frame",
                List.of(in("mega_titanium_plate", 4), in("mega_steel_ingot", 4), in("mega_carbon_fiber", 2)),
                List.of(out("mega_titanium_frame", 1))
            ),
            r(
                "plasma_injector",
                List.of(in("mega_heat_exchanger", 1), in("mega_magnetic_coil", 2), in("mega_laser_diode", 1)),
                List.of(out("mega_plasma_injector", 1))
            ),
            r(
                "shield_emitter",
                List.of(in("mega_magnetic_coil", 2), in("mega_power_capacitor", 2), in("mega_void_essence", 1)),
                List.of(out("mega_shield_emitter", 1))
            ),
            r(
                "quantum_bus",
                List.of(in("mega_superconductor_wire", 3), in("mega_logic_chip", 2), in("mega_carbon_fiber", 2)),
                List.of(out("mega_quantum_bus", 2))
            ),
            r(
                "flux_battery_asm_alt",
                List.of(in("mega_power_capacitor", 3), in("mega_superconductor_wire", 2), in("mega_titanium_plate", 2)),
                List.of(out("mega_flux_battery", 1))
            ),
            // Tier 3: Diamond convergence modules
            r(
                "compute_rack",
                List.of(
                    in("mega_logic_chip", 4),
                    in("mega_quantum_bus", 2),
                    in("mega_coolant_core", 1),
                    in("mega_titanium_frame", 1)
                ),
                List.of(out("mega_compute_rack", 1))
            ),
            r(
                "fusion_coil",
                List.of(
                    in("mega_magnetic_coil", 4),
                    in("mega_superconductor_wire", 4),
                    in("mega_heat_exchanger", 2),
                    in("mega_coolant_core", 1)
                ),
                List.of(out("mega_fusion_coil", 1))
            ),
            r(
                "reactor_chamber",
                List.of(
                    in("mega_titanium_frame", 2),
                    in("mega_plasma_injector", 2),
                    in("mega_heat_exchanger", 2),
                    in("mega_enriched_uranium", 2)
                ),
                List.of(out("mega_reactor_chamber", 1))
            ),
            r(
                "field_stabilizer",
                List.of(
                    in("mega_shield_emitter", 2),
                    in("mega_quantum_bus", 2),
                    in("mega_flux_battery", 1),
                    in("mega_carbon_fiber", 4)
                ),
                List.of(out("mega_field_stabilizer", 1))
            ),
            r(
                "neural_matrix",
                List.of(
                    in("mega_compute_rack", 1),
                    in("mega_logic_chip", 4),
                    in("mega_laser_diode", 3),
                    in("mega_void_essence", 2)
                ),
                List.of(out("mega_neural_matrix", 1))
            ),
            r(
                "hyper_frame",
                List.of(
                    in("mega_titanium_frame", 3),
                    in("mega_carbon_fiber", 6),
                    in("mega_platinum_ingot", 4),
                    in("mega_shield_emitter", 1)
                ),
                List.of(out("mega_hyper_frame", 1))
            )
        );
    }

    private static List<MegaRecipe> colliderRecipes() {
        return List.of(
            // Tier 3 collider modules
            r(
                "antimatter_cell",
                List.of(
                    in("mega_void_essence", 4),
                    in("mega_enriched_uranium", 2),
                    in("mega_shield_emitter", 1),
                    in("mega_coolant_core", 1)
                ),
                List.of(out("mega_antimatter_cell", 1))
            ),
            r(
                "warp_condenser",
                List.of(
                    in("mega_laser_diode", 4),
                    in("mega_flux_battery", 2),
                    in("mega_void_essence", 3),
                    in("mega_quantum_bus", 2)
                ),
                List.of(out("mega_warp_condenser", 1))
            ),
            // Tier 4: Apex subsystems
            r(
                "quantum_core",
                List.of(
                    in("mega_compute_rack", 2),
                    in("mega_neural_matrix", 1),
                    in("mega_quantum_bus", 4),
                    in("mega_flux_battery", 2)
                ),
                List.of(out("mega_quantum_core", 1))
            ),
            r(
                "fusion_reactor",
                List.of(
                    in("mega_fusion_coil", 2),
                    in("mega_reactor_chamber", 1),
                    in("mega_coolant_core", 2),
                    in("mega_hyper_frame", 1)
                ),
                List.of(out("mega_fusion_reactor", 1))
            ),
            r(
                "spatial_anchor",
                List.of(
                    in("mega_field_stabilizer", 2),
                    in("mega_warp_condenser", 2),
                    in("mega_hyper_frame", 1),
                    in("mega_antimatter_cell", 1)
                ),
                List.of(out("mega_spatial_anchor", 1))
            ),
            r(
                "chronos_relay",
                List.of(
                    in("mega_neural_matrix", 1),
                    in("mega_warp_condenser", 1),
                    in("mega_field_stabilizer", 1),
                    in("mega_antimatter_cell", 2)
                ),
                List.of(out("mega_chronos_relay", 1))
            ),
            // Tier 5: Ultimate mega-DAG targets
            r(
                "singularity_engine",
                List.of(
                    in("mega_quantum_core", 1),
                    in("mega_fusion_reactor", 1),
                    in("mega_spatial_anchor", 1),
                    in("mega_chronos_relay", 1)
                ),
                List.of(out("mega_singularity_engine", 1))
            ),
            r(
                "omniverse_core",
                List.of(
                    in("mega_singularity_engine", 1),
                    in("mega_quantum_core", 1),
                    in("mega_fusion_reactor", 1),
                    in("mega_antimatter_cell", 2),
                    in("mega_hyper_frame", 2)
                ),
                List.of(out("mega_omniverse_core", 1))
            )
        );
    }

    private static MegaRecipe r(String id, List<InputContent> inputs, List<OutputContent> outputs) {
        return new MegaRecipe(id, inputs, outputs);
    }

    private static Supplier<List<MegaRecipe>> cached(Supplier<List<MegaRecipe>> factory) {
        return new Supplier<>() {
            private volatile @Nullable List<MegaRecipe> value;

            @Override
            public List<MegaRecipe> get() {
                List<MegaRecipe> cached = value;
                if (cached != null) {
                    return cached;
                }
                List<MegaRecipe> built = factory.get();
                value = built;
                return built;
            }
        };
    }

    private record MegaRecipe(String id, List<InputContent> inputs, List<OutputContent> outputs) {}

    private static final class MegaRecipeSystem implements RecipeSystem<MegaRecipe> {
        private final Namespace systemId;
        private final Supplier<List<MegaRecipe>> recipes;

        private MegaRecipeSystem(Namespace systemId, Supplier<List<MegaRecipe>> recipes) {
            this.systemId = systemId;
            this.recipes = recipes;
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return MegaRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(MegaRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath(TestMod.modId, systemId.path() + "/" + recipe.id());
        }

        @Override
        public @Nullable MegaRecipe findRecipe(String uniqueId) {
            for (MegaRecipe recipe : recipes.get()) {
                ResourceLocation id = getId(recipe);
                if (id != null && id.toString().equals(uniqueId)) {
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public @Nullable MegaRecipe findRecipe(ResourceLocation registryName) {
            for (MegaRecipe recipe : recipes.get()) {
                if (Objects.equals(getId(recipe), registryName)) {
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public int hash(MegaRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(MegaRecipe r1, MegaRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, MegaRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(this, recipe, recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<MegaRecipe> allRecipes() {
            return Lists.immutable.ofAll(recipes.get());
        }
    }
}
