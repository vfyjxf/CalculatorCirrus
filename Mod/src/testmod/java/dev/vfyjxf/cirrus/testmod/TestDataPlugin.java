package dev.vfyjxf.cirrus.testmod;

import com.google.gson.stream.JsonReader;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registers the in-memory recipe systems of the test mod:
 *
 * <ul>
 *   <li>{@code cirrus_test:dummy} — recipes vanilla crafting cannot express
 *   (fluid inputs), e.g. {@code dummy_ingot + dummy_fluid -> dummy_crystal}
 *   versus a pricier item-only crystal path.</li>
 *   <li>{@code cirrus_test:fake/archaeology} — pseudo "archaeology" recipes
 *   whose system id matches the built-in junk pattern; the planner must
 *   exclude them and the browser must badge them.</li>
 *   <li>one system per recipe system of the replayed export pack, named by
 *   flattening the source id ({@code minecraft:crafting} ->
 *   {@code cirrus_test:minecraft__crafting}). Recipes reference the mimic
 *   items/fluids 1:1 (see {@code datagen/TestDataGenerator}), including real
 *   tag and multi-choice inputs; a system with machines in the export reports
 *   its first machine as {@code conventionMachine}.</li>
 * </ul>
 *
 * <p>Recipe lists are built lazily on first {@code allRecipes()} call: the
 * plugin hook runs before registry freeze, so the registries must not be
 * touched during registration.
 */
@PluginMarker
public final class TestDataPlugin implements CirrusPlugin {

    private static final Namespace PLUGIN_ID = Namespace.of(TestMod.MOD_ID, "test_data");

    /** raw system id -> recipes; parsed once from bulk_recipes.json on first use */
    private static volatile Map<String, List<DummyRecipe>> bulkCache;

    @Override
    public Namespace pluginId() {
        return PLUGIN_ID;
    }

    @Override
    public Set<PluginDependency> dependencies() {
        return Set.of(CirrusPlugin.afterBuiltin);
    }

    @Override
    public void registerRecipeSystem(RecipeSystemRegister register) {
        if (!TestMod.testEnabled()) {
            return; // clean client: no mimic recipe systems, no bulk replay
        }
        register.registerSystem(new DummyRecipeSystem(
            Namespace.of(TestMod.MOD_ID, "dummy"),
            cached(TestDataPlugin::dummyRecipes)
        ));
        register.registerSystem(new DummyRecipeSystem(
            Namespace.of(TestMod.MOD_ID, "fake/archaeology"),
            cached(TestDataPlugin::archaeologyRecipes)
        ));
        for (MimicManifest.MimicSystem system : MimicManifest.load().systemList()) {
            register.registerSystem(new DummyRecipeSystem(
                Namespace.of(TestMod.MOD_ID, TestMod.mimicName(system.id())),
                cached(() -> bulkRecipes(system.id())),
                system.machine()
            ));
        }
    }

    //region showcase recipe lists

    private static List<DummyRecipe> dummyRecipes() {
        return List.of(
            new DummyRecipe(
                "crystal_from_fluid",
                List.of(
                    in(TestMod.DUMMY_INGOT.get(), 1),
                    inFluid(new FluidStack(TestMod.DUMMY_FLUID.get(), 250))
                ),
                List.of(out(TestMod.DUMMY_CRYSTAL.get(), 1))
            ),
            new DummyRecipe(
                "crystal_expensive",
                List.of(in(TestMod.DUMMY_INGOT.get(), 4)),
                List.of(out(TestMod.DUMMY_CRYSTAL.get(), 1))
            )
        );
    }

    private static List<DummyRecipe> archaeologyRecipes() {
        return List.of(
            new DummyRecipe(
                "relic_from_white_block",
                List.of(in(TestMod.DUMMY_BLOCK_WHITE.get(), 1)),
                List.of(out(TestMod.DUMMY_RELIC.get(), 1))
            )
        );
    }

    //endregion

    //region bulk replay

    private static List<DummyRecipe> bulkRecipes(String rawSystemId) {
        Map<String, List<DummyRecipe>> all = bulkCache;
        if (all == null) {
            synchronized (TestDataPlugin.class) {
                all = bulkCache;
                if (all == null) {
                    all = parseBulkRecipes();
                    bulkCache = all;
                }
            }
        }
        return all.getOrDefault(rawSystemId, List.of());
    }

    /**
     * Parses {@code data/cirrus_test/bulk_recipes.json} from the mod jar.
     * Slot encoding (arrays):
     * <ul>
     *   <li>{@code ["i", rawItemId, amount, chance]} — item stack</li>
     *   <li>{@code ["f", rawFluidId, amount, chance]} — fluid stack</li>
     *   <li>{@code ["t", rawTagId, amount, chance]} — item tag (inputs)</li>
     *   <li>{@code ["ft", rawTagId, amount, chance]} — fluid tag (inputs)</li>
     *   <li>{@code ["a", amount, chance, [rawItemId, ...]]} — multi-choice</li>
     * </ul>
     */
    private static Map<String, List<DummyRecipe>> parseBulkRecipes() {
        try (InputStream stream = TestDataPlugin.class.getResourceAsStream("/data/cirrus_test/bulk_recipes.json")) {
            if (stream == null) {
                return Map.of();
            }
            Map<String, List<DummyRecipe>> bySystem = new HashMap<>();
            try (JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if (reader.nextName().equals("recipes")) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            DummyRecipe recipe = readBulkRecipe(reader);
                            if (recipe != null) {
                                bySystem.computeIfAbsent(recipe.system(), key -> new ArrayList<>()).add(recipe);
                            }
                        }
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
            return bySystem;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load bulk_recipes.json", exception);
        }
    }

    private static @Nullable DummyRecipe readBulkRecipe(JsonReader reader) throws IOException {
        String system = null;
        String id = "?";
        List<InputContent> inputs = List.of();
        List<OutputContent> outputs = List.of();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "sys" -> system = reader.nextString();
                case "id" -> id = reader.nextString();
                case "in" -> inputs = readBulkSlots(reader, true);
                case "out" -> outputs = readBulkSlots(reader, false);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (system == null) {
            return null;
        }
        return new DummyRecipe(system, id, inputs, outputs);
    }

    private static <T> List<T> readBulkSlots(JsonReader reader, boolean input) throws IOException {
        List<Object> slots = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginArray();
            String kind = reader.nextString();
            switch (kind) {
                case "i" -> {
                    ItemStack stack = new ItemStack(TestMod.mimicItem(reader.nextString()));
                    long amount = reader.nextLong();
                    float chance = (float) reader.nextDouble();
                    if (reader.peek() == com.google.gson.stream.JsonToken.STRING) {
                        reader.nextString(); // "c" component marker tag
                        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal("✦" + reader.nextString()));
                    }
                    slots.add(input
                        ? InputContent.fromItemStack(stack, amount, chance)
                        : OutputContent.fromItemStack(stack, amount, chance));
                }
                case "f" -> {
                    FluidStack stack = new FluidStack(TestMod.mimicFluid(reader.nextString()), 1);
                    long amount = reader.nextLong();
                    float chance = (float) reader.nextDouble();
                    if (reader.peek() == com.google.gson.stream.JsonToken.STRING) {
                        reader.nextString(); // "c" component marker tag
                        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal("✦" + reader.nextString()));
                    }
                    slots.add(input
                        ? InputContent.fromFluidStack(stack, amount, chance)
                        : OutputContent.fromFluidStack(stack, amount, chance));
                }
                case "t" -> {
                    TagKey<Item> tag = TestMod.mimicItemTag(reader.nextString());
                    long amount = reader.nextLong();
                    float chance = (float) reader.nextDouble();
                    slots.add(InputContent.fromItemTag(tag, amount, chance));
                }
                case "ft" -> {
                    TagKey<Fluid> tag = TestMod.mimicFluidTag(reader.nextString());
                    long amount = reader.nextLong();
                    float chance = (float) reader.nextDouble();
                    slots.add(InputContent.fromFluidTag(tag, amount, chance));
                }
                case "a" -> {
                    long amount = reader.nextLong();
                    float chance = (float) reader.nextDouble();
                    List<ContentStack<?, ?>> choices = new ArrayList<>();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        choices.add(ContentStack.fromStack(new ItemStack(TestMod.mimicItem(reader.nextString()))));
                    }
                    reader.endArray();
                    slots.add(InputContent.fromStackList(choices, amount, chance));
                }
                default -> {
                    // unknown slot kind: consume the remaining array entries
                    while (reader.hasNext()) {
                        reader.skipValue();
                    }
                }
            }
            reader.endArray();
        }
        reader.endArray();
        @SuppressWarnings("unchecked")
        List<T> typed = (List<T>) slots;
        return typed;
    }

    //endregion

    //region helpers

    static InputContent in(Item item, long amount) {
        return InputContent.fromItemStack(new ItemStack(item), amount);
    }

    static InputContent inFluid(FluidStack stack) {
        return InputContent.fromFluidStack(stack, stack.getAmount());
    }

    static OutputContent out(Item item, long amount) {
        return OutputContent.fromItemStack(new ItemStack(item), amount);
    }

    private static <T> Supplier<T> cached(Supplier<T> supplier) {
        return new Supplier<>() {
            private volatile @Nullable T cache;

            @Override
            public T get() {
                T current = cache;
                if (current == null) {
                    synchronized (this) {
                        current = cache;
                        if (current == null) {
                            current = supplier.get();
                            cache = current;
                        }
                    }
                }
                return current;
            }
        };
    }

    record DummyRecipe(String system, String id, List<InputContent> inputs, List<OutputContent> outputs) {
        DummyRecipe(String id, List<InputContent> inputs, List<OutputContent> outputs) {
            this("", id, inputs, outputs);
        }
    }

    private static final class DummyRecipeSystem implements RecipeSystem<DummyRecipe> {

        private final Namespace systemId;
        private final Supplier<List<DummyRecipe>> recipes;
        private final @Nullable String machine;
        private final ConcurrentHashMap<String, ResourceLocation> recipeIds = new ConcurrentHashMap<>();

        private DummyRecipeSystem(Namespace systemId, Supplier<List<DummyRecipe>> recipes) {
            this(systemId, recipes, null);
        }

        private DummyRecipeSystem(Namespace systemId, Supplier<List<DummyRecipe>> recipes, @Nullable String machine) {
            this.systemId = systemId;
            this.recipes = recipes;
            this.machine = machine;
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return DummyRecipe.class;
        }

        @Override
        public MachineEntry conventionMachine() {
            if (machine == null) {
                return MachineEntry.empty;
            }
            return ItemMachineEntry.fromItemStack(new ItemStack(TestMod.mimicItem(machine)));
        }

        @Override
        public @Nullable ResourceLocation getId(DummyRecipe recipe) {
            // getId sits on hot paths (database sort keys are extracted per
            // record); ResourceLocation construction must not repeat per call.
            return recipeIds.computeIfAbsent(recipe.id(), id -> ResourceLocation.fromNamespaceAndPath(systemId.root(), id));
        }

        @Override
        public @Nullable DummyRecipe findRecipe(String uniqueId) {
            for (DummyRecipe recipe : recipes.get()) {
                if (recipe.id().equals(uniqueId)) {
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public @Nullable DummyRecipe findRecipe(ResourceLocation registryName) {
            return findRecipe(registryName.getPath());
        }

        @Override
        public int hash(DummyRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(DummyRecipe first, DummyRecipe second) {
            return Objects.equals(first.id(), second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, DummyRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<DummyRecipe> allRecipes() {
            return Lists.immutable.ofAll(recipes.get());
        }
    }

    //endregion
}
