package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class CalculatorSyntheticScenario {
    private CalculatorSyntheticScenario() {
    }

    public static Scenario create(int competingGearRecipes) {
        MaterialSet materials = MaterialSet.create(Math.max(0, competingGearRecipes));
        MutableRecipeList recipes = new MutableRecipeList();
        recipes.add("ore_to_crushed", List.of(input(materials.ore, 1)), List.of(output(materials.crushedOre, 2)));
        recipes.add("crushed_to_ingot", List.of(input(materials.crushedOre, 1)), List.of(output(materials.ingot, 1)));
        recipes.add("ingot_to_plate", List.of(input(materials.ingot, 2)), List.of(output(materials.plate, 1)));
        recipes.add("make_circuit", List.of(input(materials.plate, 2), input(materials.crystal, 1)), List.of(output(materials.circuit, 1)));
        recipes.add("make_gear", List.of(input(materials.plate, 4)), List.of(output(materials.gear, 1)));
        recipes.add("make_machine", List.of(input(materials.gear, 2), input(materials.circuit, 1), input(materials.casing, 1)), List.of(output(materials.machine, 1)));
        recipes.add("make_target", List.of(input(materials.machine, 1), input(materials.fuel, 4)), List.of(output(materials.target, 1)));
        for (int i = 0; i < competingGearRecipes; i++) {
            recipes.add(
                "zz_alt_gear_" + i,
                List.of(input(materials.noiseInputs.get(i), 1)),
                List.of(output(materials.gear, 1))
            );
        }
        SyntheticRecipeSystem system = new SyntheticRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<SyntheticRecipe> target = RecipeEntry.of(system, recipes.require("make_target"));
        return new Scenario("wide:" + competingGearRecipes, system, entries, target, outputResource(materials.target, 1));
    }

    public static Scenario createDeepChain(int depth, int competingRecipesPerStep) {
        int safeDepth = Math.max(2, depth);
        int safeCompeting = Math.max(0, competingRecipesPerStep);
        List<SyntheticMaterialType> stages = new ArrayList<>(safeDepth + 1);
        for (int i = 0; i <= safeDepth; i++) {
            stages.add(material("deep_stage_" + i));
        }
        SyntheticMaterialType result = material("deep_result");

        MutableRecipeList recipes = new MutableRecipeList();
        for (int i = 1; i <= safeDepth; i++) {
            recipes.add(
                "deep_step_" + i,
                List.of(input(stages.get(i - 1), i % 3 + 1L)),
                List.of(output(stages.get(i), 1))
            );
            for (int j = 0; j < safeCompeting; j++) {
                recipes.add(
                    "deep_step_" + i + "_alt_" + j,
                    List.of(input(material("deep_noise_" + i + "_" + j), 1)),
                    List.of(output(stages.get(i), 1))
                );
            }
        }
        recipes.add(
            "deep_target",
            List.of(input(stages.get(safeDepth), 1), input(material("deep_fuel"), 3)),
            List.of(output(result, 1))
        );

        SyntheticRecipeSystem system = new SyntheticRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<SyntheticRecipe> target = RecipeEntry.of(system, recipes.require("deep_target"));
        return new Scenario(
            "deep:" + safeDepth + ":" + safeCompeting,
            system,
            entries,
            target,
            outputResource(result, 1)
        );
    }

    public static Scenario createWorkbenchLike(int competingAlternatives) {
        int safeCompeting = Math.max(0, competingAlternatives);
        SyntheticMaterialType log = material("workbench_log");
        SyntheticMaterialType planks = material("workbench_planks");
        SyntheticMaterialType sticks = material("workbench_sticks");
        SyntheticMaterialType cobblestone = material("workbench_cobblestone");
        SyntheticMaterialType furnace = material("workbench_furnace");
        SyntheticMaterialType smoker = material("workbench_smoker");
        SyntheticMaterialType iron = material("workbench_iron");
        SyntheticMaterialType redstone = material("workbench_redstone");
        SyntheticMaterialType piston = material("workbench_piston");
        SyntheticMaterialType slime = material("workbench_slime");
        SyntheticMaterialType stickyPiston = material("workbench_sticky_piston");
        SyntheticMaterialType station = material("workbench_station");

        MutableRecipeList recipes = new MutableRecipeList();
        recipes.add("workbench_log_to_planks", List.of(input(log, 1)), List.of(output(planks, 4)));
        recipes.add("workbench_planks_to_sticks", List.of(input(planks, 2)), List.of(output(sticks, 4)));
        recipes.add("workbench_make_furnace", List.of(input(cobblestone, 8)), List.of(output(furnace, 1)));
        recipes.add("workbench_make_smoker", List.of(input(furnace, 1), input(log, 4)), List.of(output(smoker, 1)));
        recipes.add(
            "workbench_make_piston",
            List.of(input(planks, 3), input(cobblestone, 4), input(iron, 1), input(redstone, 1)),
            List.of(output(piston, 1))
        );
        recipes.add(
            "workbench_make_sticky_piston",
            List.of(input(piston, 1), input(slime, 1)),
            List.of(output(stickyPiston, 1))
        );
        recipes.add(
            "workbench_make_station",
            List.of(input(smoker, 1), input(stickyPiston, 1), input(sticks, 8)),
            List.of(output(station, 1))
        );
        for (int i = 0; i < safeCompeting; i++) {
            SyntheticMaterialType noise = material("workbench_noise_" + i);
            if ((i & 1) == 0) {
                recipes.add("workbench_alt_planks_" + i, List.of(input(noise, 1)), List.of(output(planks, 4)));
            } else {
                recipes.add("workbench_alt_sticks_" + i, List.of(input(noise, 1)), List.of(output(sticks, 4)));
            }
        }

        SyntheticRecipeSystem system = new SyntheticRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<SyntheticRecipe> target = RecipeEntry.of(system, recipes.require("workbench_make_station"));
        return new Scenario(
            "workbench:" + safeCompeting,
            system,
            entries,
            target,
            outputResource(station, 1)
        );
    }

    public static Scenario createMachineLineLike(int componentFamilies, int competingAlternatives) {
        int safeFamilies = Math.max(1, componentFamilies);
        int safeCompeting = Math.max(0, competingAlternatives);
        SyntheticMaterialType ore = material("machine_ore");
        SyntheticMaterialType crushed = material("machine_crushed");
        SyntheticMaterialType ingot = material("machine_ingot");
        SyntheticMaterialType plate = material("machine_plate");
        SyntheticMaterialType wire = material("machine_wire");
        SyntheticMaterialType rod = material("machine_rod");
        SyntheticMaterialType gear = material("machine_gear");
        SyntheticMaterialType motor = material("machine_motor");
        SyntheticMaterialType circuit = material("machine_circuit");
        SyntheticMaterialType casing = material("machine_casing");
        SyntheticMaterialType pump = material("machine_pump");
        SyntheticMaterialType assembler = material("machine_assembler");
        SyntheticMaterialType target = material("machine_target");

        MutableRecipeList recipes = new MutableRecipeList();
        recipes.add("machine_ore_to_crushed", List.of(input(ore, 1)), List.of(output(crushed, 2)));
        recipes.add("machine_crushed_to_ingot", List.of(input(crushed, 1)), List.of(output(ingot, 1)));
        recipes.add("machine_ingot_to_plate", List.of(input(ingot, 2)), List.of(output(plate, 1)));
        recipes.add("machine_ingot_to_wire", List.of(input(ingot, 1)), List.of(output(wire, 2)));
        recipes.add("machine_ingot_to_rod", List.of(input(ingot, 1)), List.of(output(rod, 2)));
        recipes.add("machine_make_gear", List.of(input(plate, 4)), List.of(output(gear, 1)));
        recipes.add("machine_make_motor", List.of(input(plate, 2), input(wire, 4), input(rod, 2)), List.of(output(motor, 1)));
        recipes.add("machine_make_circuit", List.of(input(plate, 1), input(wire, 6)), List.of(output(circuit, 1)));
        recipes.add("machine_make_casing", List.of(input(plate, 8)), List.of(output(casing, 1)));
        recipes.add("machine_make_pump", List.of(input(motor, 1), input(plate, 2), input(wire, 2)), List.of(output(pump, 1)));
        recipes.add(
            "machine_make_assembler",
            List.of(input(casing, 1), input(circuit, 2), input(gear, 2), input(motor, 1)),
            List.of(output(assembler, 1))
        );
        recipes.add(
            "machine_make_target",
            List.of(input(assembler, 1), input(pump, 2), input(circuit, safeFamilies)),
            List.of(output(target, 1))
        );
        for (int i = 0; i < safeCompeting; i++) {
            SyntheticMaterialType noise = material("machine_noise_" + i);
            switch (i % 4) {
                case 0 -> recipes.add("machine_alt_gear_" + i, List.of(input(noise, 1)), List.of(output(gear, 1)));
                case 1 -> recipes.add("machine_alt_motor_" + i, List.of(input(noise, 1)), List.of(output(motor, 1)));
                case 2 -> recipes.add("machine_alt_circuit_" + i, List.of(input(noise, 1)), List.of(output(circuit, 1)));
                default -> recipes.add("machine_alt_plate_" + i, List.of(input(noise, 1)), List.of(output(plate, 1)));
            }
        }

        SyntheticRecipeSystem system = new SyntheticRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<SyntheticRecipe> recipeTarget = RecipeEntry.of(system, recipes.require("machine_make_target"));
        return new Scenario(
            "machine:" + safeFamilies + ":" + safeCompeting,
            system,
            entries,
            recipeTarget,
            outputResource(target, 1)
        );
    }

    public static Scenario createRandomComplexCatalog(
        int recipeCount,
        int pathDepth,
        int maxInputs,
        int noiseFanout,
        long seed
    ) {
        int safeDepth = Math.max(1, pathDepth);
        int safeMaxInputs = Math.max(1, maxInputs);
        int safeNoiseFanout = Math.max(1, noiseFanout);
        SplittableRandom random = new SplittableRandom(seed);
        String prefix = "random_" + Long.toUnsignedString(seed, 36) + "_";

        MutableRecipeList recipes = new MutableRecipeList();
        List<SyntheticMaterialType> pathStages = new ArrayList<>(safeDepth + 1);
        for (int i = 0; i <= safeDepth; i++) {
            pathStages.add(material(prefix + "path_stage_" + i));
        }
        recipes.add(prefix + "path_base", List.of(), List.of(output(pathStages.getFirst(), 1)));

        for (int step = 1; step <= safeDepth; step++) {
            int inputCount = 1 + random.nextInt(safeMaxInputs);
            List<InputContent> inputs = new ArrayList<>(inputCount);
            inputs.add(input(pathStages.get(step - 1), 1 + random.nextInt(3)));
            for (int inputIndex = 1; inputIndex < inputCount; inputIndex++) {
                SyntheticMaterialType leaf = material(prefix + "path_leaf_" + step + "_" + inputIndex);
                recipes.add(prefix + "path_leaf_recipe_" + step + "_" + inputIndex, List.of(), List.of(output(leaf, 1 + random.nextInt(4))));
                inputs.add(input(leaf, 1 + random.nextInt(2)));
            }
            recipes.add(prefix + "path_step_" + step, inputs, List.of(output(pathStages.get(step), 1)));
        }

        SyntheticMaterialType target = material(prefix + "target");
        List<InputContent> targetInputs = new ArrayList<>();
        targetInputs.add(input(pathStages.getLast(), 1));
        int extraTargetInputs = random.nextInt(safeMaxInputs);
        for (int i = 0; i < extraTargetInputs; i++) {
            SyntheticMaterialType leaf = material(prefix + "target_leaf_" + i);
            recipes.add(prefix + "target_leaf_recipe_" + i, List.of(), List.of(output(leaf, 1 + random.nextInt(4))));
            targetInputs.add(input(leaf, 1));
        }
        String targetRecipeId = prefix + "target_recipe";
        recipes.add(targetRecipeId, targetInputs, List.of(output(target, 1)));

        int targetRecipeCount = Math.max(recipeCount, recipes.size());
        List<SyntheticMaterialType> noiseOutputs = new ArrayList<>(Math.max(0, targetRecipeCount - recipes.size()));
        while (recipes.size() < targetRecipeCount) {
            int noiseIndex = noiseOutputs.size();
            int inputCount = random.nextInt(safeMaxInputs + 1);
            List<InputContent> inputs = new ArrayList<>(inputCount);
            for (int inputIndex = 0; inputIndex < inputCount; inputIndex++) {
                boolean linkExisting = !noiseOutputs.isEmpty() && random.nextInt(100) < 70;
                SyntheticMaterialType inputType;
                if (linkExisting) {
                    int window = Math.min(noiseOutputs.size(), safeNoiseFanout);
                    inputType = noiseOutputs.get(noiseOutputs.size() - 1 - random.nextInt(window));
                } else {
                    inputType = material(prefix + "noise_raw_" + noiseIndex + "_" + inputIndex);
                }
                inputs.add(input(inputType, 1 + random.nextInt(4)));
            }

            int outputCount = 1 + (safeNoiseFanout > 1 && random.nextInt(8) == 0 ? 1 : 0);
            List<OutputContent> outputs = new ArrayList<>(outputCount);
            for (int outputIndex = 0; outputIndex < outputCount; outputIndex++) {
                SyntheticMaterialType outputType = material(prefix + "noise_output_" + noiseIndex + "_" + outputIndex);
                noiseOutputs.add(outputType);
                outputs.add(output(outputType, 1 + random.nextInt(4)));
            }
            recipes.add(prefix + "noise_recipe_" + noiseIndex, inputs, outputs);
        }

        SyntheticRecipeSystem system = new SyntheticRecipeSystem(recipes.toImmutable());
        ImmutableList<RecipeEntry<?>> entries = recipes.entries(system);
        RecipeEntry<SyntheticRecipe> recipeTarget = RecipeEntry.of(system, recipes.require(targetRecipeId));
        return new Scenario(
            "random:" + entries.size() + ":" + safeDepth + ":" + safeMaxInputs + ":" + safeNoiseFanout + ":" + seed,
            system,
            entries,
            recipeTarget,
            outputResource(target, 1)
        );
    }

    public static InputContent input(SyntheticMaterialType type, long amount) {
        return InputContent.fromValue(type, amount);
    }

    public static OutputContent output(SyntheticMaterialType type, long amount) {
        return OutputContent.fromValue(type, amount);
    }

    public static ContentResource outputResource(SyntheticMaterialType type, long amount) {
        return ContentValue.fromValue(type, type.fromValue(amount));
    }

    public static @Nullable RecipeApiImpl attachRecipeApi(SyntheticRecipeSystem system) {
        RecipeApi current = RecipeApiImpl.instance();
        RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    public static void restoreRecipeApi(@Nullable RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

    public record Scenario(
        String name,
        SyntheticRecipeSystem system,
        ImmutableList<RecipeEntry<?>> entries,
        RecipeEntry<SyntheticRecipe> target,
        ContentResource targetOutput
    ) {
        // TODO(planning-bridge): 恢复场景图/规划基准入口（旧路由已删除，待 bridge 接线）
    }

    public record SyntheticRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    public static final class SyntheticRecipeSystem implements RecipeSystem<SyntheticRecipe> {
        private final RichIterable<SyntheticRecipe> recipes;
        private final Map<String, SyntheticRecipe> byId = new ConcurrentHashMap<>();

        private SyntheticRecipeSystem(RichIterable<SyntheticRecipe> recipes) {
            this.recipes = recipes;
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("bench", "calculator");
        }

        @Override
        public Class<?> basicType() {
            return SyntheticRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(SyntheticRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("bench", recipe.id());
        }

        @Override
        public @Nullable SyntheticRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable SyntheticRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(SyntheticRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(SyntheticRecipe left, SyntheticRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, SyntheticRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RoutingRecipeView routingView(ResolveContext context, SyntheticRecipe recipe) {
            return new RoutingRecipeView(recipe.inputs(), recipe.outputs());
        }

        @Override
        public RecipeScreeningHints routingHints(SyntheticRecipe recipe) {
            if (recipe.id().startsWith("deep_step_") && recipe.id().contains("_alt_")) {
                return RecipeScreeningHints.builder()
                    .lastResort()
                    .penalty(100_000)
                    .reasonKey("synthetic_noise")
                    .build();
            }
            if (recipe.id().contains("_noise_recipe_")) {
                return RecipeScreeningHints.builder()
                    .lastResort()
                    .penalty(100_000)
                    .reasonKey("synthetic_noise")
                    .build();
            }
            return RecipeScreeningHints.empty();
        }

        @Override
        public RichIterable<SyntheticRecipe> allRecipes() {
            return recipes;
        }
    }

    public static final class SyntheticMaterialType implements ValueType<Long> {
        private final Namespace id;
        private final String displayName;

        private SyntheticMaterialType(String name) {
            this.id = Namespace.of("bench", "material/" + name);
            this.displayName = name;
        }

        @Override
        public Namespace typeId() {
            return id;
        }

        @Override
        public long getValue(Long content) {
            return Math.max(0L, content == null ? 0L : content);
        }

        @Override
        public boolean matches(Long left, Long right) {
            return true;
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }

        @Override
        public String toString(Long content) {
            return displayName + " x" + getValue(content);
        }
    }

    private static final class MaterialSet {
        private final SyntheticMaterialType ore = material("ore");
        private final SyntheticMaterialType crushedOre = material("crushed_ore");
        private final SyntheticMaterialType ingot = material("ingot");
        private final SyntheticMaterialType plate = material("plate");
        private final SyntheticMaterialType crystal = material("crystal");
        private final SyntheticMaterialType circuit = material("circuit");
        private final SyntheticMaterialType gear = material("gear");
        private final SyntheticMaterialType casing = material("casing");
        private final SyntheticMaterialType machine = material("machine");
        private final SyntheticMaterialType fuel = material("fuel");
        private final SyntheticMaterialType target = material("target");
        private final List<SyntheticMaterialType> noiseInputs;

        private MaterialSet(List<SyntheticMaterialType> noiseInputs) {
            this.noiseInputs = noiseInputs;
        }

        private static MaterialSet create(int noiseCount) {
            List<SyntheticMaterialType> noiseInputs = new ArrayList<>(noiseCount);
            for (int i = 0; i < noiseCount; i++) {
                noiseInputs.add(material("noise_" + i));
            }
            return new MaterialSet(noiseInputs);
        }
    }

    private static SyntheticMaterialType material(String name) {
        return new SyntheticMaterialType(name);
    }

    private static final class MutableRecipeList {
        private final List<SyntheticRecipe> recipes = new ArrayList<>();

        private void add(String id, Collection<InputContent> inputs, Collection<OutputContent> outputs) {
            recipes.add(new SyntheticRecipe(id, List.copyOf(inputs), List.copyOf(outputs)));
        }

        private int size() {
            return recipes.size();
        }

        private SyntheticRecipe require(String id) {
            for (SyntheticRecipe recipe : recipes) {
                if (recipe.id().equals(id)) {
                    return recipe;
                }
            }
            throw new IllegalArgumentException("Missing synthetic recipe: " + id);
        }

        private RichIterable<SyntheticRecipe> toImmutable() {
            return Lists.immutable.ofAll(recipes);
        }

        private ImmutableList<RecipeEntry<?>> entries(SyntheticRecipeSystem system) {
            MutableList<RecipeEntry<?>> entries = Lists.mutable.empty();
            for (SyntheticRecipe recipe : recipes) {
                entries.add(RecipeEntry.of(system, recipe));
            }
            return entries.toImmutable();
        }
    }
}
