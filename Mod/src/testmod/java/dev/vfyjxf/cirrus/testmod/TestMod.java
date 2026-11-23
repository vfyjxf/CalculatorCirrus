package dev.vfyjxf.cirrus.testmod;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureRegistry;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dummy content mod for watching the calculator's screening in a live game. Two kinds of content:
 *
 * <p><b>Showcase content</b> — a handful of hand-registered items plus one
 * fluid, wired into hand-designed demo recipes (recolor loop, recycling pair,
 * tag exchanges, greedy-vs-exact chain) by the generated JSONs and by
 * {@link TestDataPlugin}.
 *
 * <p><b>Mimic content</b> — for every item/fluid id found in the replayed
 * export pack (see {@code data/cirrus_test/mimic_manifest.json}, written by
 * {@code datagen/TestDataGenerator}) an own item/fluid is registered under a
 * flattened name: {@code minecraft:iron_ingot} becomes
 * {@code cirrus_test:minecraft__iron_ingot}. Recipes reference the mimics
 * 1:1, so the calculator sees the pack's real topology — real item fan-in, real
 * tags, real systems — without shipping any real mod content. Mimics are
 * browsed through JEI; the creative tab shows the showcase items only.
 */
@Mod(TestMod.MOD_ID)
public final class TestMod {

    public static final String MOD_ID = "cirrus_test";
    private static final Logger LOG = LoggerFactory.getLogger("CirrusTestMod");

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MOD_ID);

    private static final Set<String> SHOWCASE_ITEMS = new HashSet<>();

    // recolor loop family: white/red/blue/green convert into each other
    public static final DeferredItem<Item> DUMMY_BLOCK_WHITE = item("dummy_block_white");
    public static final DeferredItem<Item> DUMMY_BLOCK_RED = item("dummy_block_red");
    public static final DeferredItem<Item> DUMMY_BLOCK_BLUE = item("dummy_block_blue");
    public static final DeferredItem<Item> DUMMY_BLOCK_GREEN = item("dummy_block_green");

    // recycling pair: ingot <-> plate
    public static final DeferredItem<Item> DUMMY_INGOT = item("dummy_ingot");
    public static final DeferredItem<Item> DUMMY_PLATE = item("dummy_plate");

    // exchange pair: tag-based 1:1 swaps
    public static final DeferredItem<Item> DUMMY_CHARM_A = item("dummy_charm_a");
    public static final DeferredItem<Item> DUMMY_CHARM_B = item("dummy_charm_b");

    // chain for the greedy-vs-exact comparison: final <- mid <- raw_a (good)
    // and final <- x3 <- x2 <- x1 <- raw_b (bad, alphabetically first)
    public static final DeferredItem<Item> DUMMY_FINAL = item("dummy_final");
    public static final DeferredItem<Item> CHAIN_MID = item("chain_mid");
    public static final DeferredItem<Item> CHAIN_RAW_A = item("chain_raw_a");
    public static final DeferredItem<Item> CHAIN_X1 = item("chain_x1");
    public static final DeferredItem<Item> CHAIN_X2 = item("chain_x2");
    public static final DeferredItem<Item> CHAIN_X3 = item("chain_x3");
    public static final DeferredItem<Item> CHAIN_RAW_B = item("chain_raw_b");

    // fluid-crafted product, plus the fake-archaeology junk target
    public static final DeferredItem<Item> DUMMY_CRYSTAL = item("dummy_crystal");
    public static final DeferredItem<Item> DUMMY_RELIC = item("dummy_relic");

    public static final DeferredHolder<FluidType, FluidType> DUMMY_FLUID_TYPE =
        FLUID_TYPES.register("dummy_fluid", () -> fluidType(null));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DUMMY_FLUID =
        FLUIDS.register("dummy_fluid", () -> new BaseFlowingFluid.Source(dummyFluidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> DUMMY_FLUID_FLOWING =
        FLUIDS.register("dummy_fluid_flowing", () -> new BaseFlowingFluid.Flowing(dummyFluidProperties()));

    // mimic content: one own entry per id referenced by the replayed export pack.
    // Skipped entirely on a clean runClient (-Dcirrus.test.enabled=false): no
    // 150MB manifest parse, no thousands of registry entries.
    static {
        if (testEnabled()) {
            MimicManifest manifest = MimicManifest.load();
            for (String rawId : manifest.itemIds()) {
                ITEMS.registerItem(mimicName(rawId), Item::new, new Item.Properties());
            }
            for (String rawId : manifest.fluidIds()) {
                mimicFluidRegistration(rawId);
            }
            if (manifest.pack() != null) {
                LOG.info(
                    "registered {} mimic items and {} mimic fluids replaying pack {}",
                    manifest.itemIds().size(), manifest.fluidIds().size(), manifest.pack()
                );
            }
        }
    }

    /** Master switch for the testmod: only runTestClient sets it. */
    public static boolean testEnabled() {
        return Boolean.getBoolean("cirrus.test.enabled");
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
        "main",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.cirrus_test"))
            .icon(() -> new ItemStack(DUMMY_INGOT.get()))
            // hand-crafted showcase items only; the thousands of mimics are
            // browsed through JEI, not a creative tab
            .displayItems((parameters, output) -> ITEMS.getEntries().stream()
                .filter(entry -> SHOWCASE_ITEMS.contains(entry.getId().getPath()))
                .forEach(entry -> output.accept(entry.get())))
            .build()
    );

    public TestMod(ModContainer container, IEventBus modBus) {
        if (!testEnabled()) {
            LOG.info("cirrus_test disabled (clean client); pass -Dcirrus.test.enabled=true or use runTestClient");
            return;
        }
        ITEMS.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        TABS.register(modBus);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> diagnoseCatalog());
        // on the integrated server RecipeApi initializes after ServerStartedEvent,
        // so the diagnosis retries each tick until the api becomes available
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> diagnoseCatalog());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> maybeAutoDbIndex());
        // client-only hooks live in TestModClientUi so the server dist (game
        // tests) never resolves client-only types
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TestModClientUi.register();
        }
    }

    private volatile boolean catalogDiagnosed;

    /**
     * Diagnostic: once RecipeApi is up, log how many mimic recipes each mimic
     * system registered. Any load failure is logged with its stack trace.
     */
    private void diagnoseCatalog() {
        if (catalogDiagnosed) {
            return;
        }
        RecipeApi api;
        try {
            api = RecipeApi.instance();
        } catch (RuntimeException notReady) {
            if (notReady.getMessage() != null && notReady.getMessage().contains("not initialized")) {
                return; // retried on the next tick
            }
            catalogDiagnosed = true;
            LOG.error("cirrus_test catalog diagnosis failed", notReady);
            return;
        }
        catalogDiagnosed = true;
        try {
            int mimicSystems = 0;
            long mimicRecipes = 0;
            for (RecipeSystem<?> system : api.allSystems()) {
                if (!system.systemId().root().equals(MOD_ID)) {
                    continue;
                }
                mimicSystems++;
                try {
                    int size = system.allRecipes().size();
                    mimicRecipes += size;
                } catch (RuntimeException exception) {
                    LOG.error("mimic system {} failed to load recipes", system.systemId(), exception);
                }
            }
            LOG.info("cirrus_test registered systems={} recipes={}", mimicSystems, mimicRecipes);
        } catch (RuntimeException exception) {
            LOG.error("cirrus_test catalog diagnosis failed", exception);
        }
    }

    //region auto-dbindex benchmark hook

    private static final String AUTODBINDEX_PROPERTY = "cirrus.test.autodbindex";

    private volatile boolean autoDbIndexStarted;

    /**
     * Headless benchmark hook: with {@code -Dcirrus.test.autodbindex=1} the
     * first tick after RecipeApi is up builds the system-catalog recipe
     * database index on a daemon worker thread — the same
     * {@link RecipeDatabaseCatalogCache#getOrBuild} call the Database page
     * issues — twice, and logs elapsed time and record counts.
     */
    private void maybeAutoDbIndex() {
        if (autoDbIndexStarted) {
            return;
        }
        String flag = System.getProperty(AUTODBINDEX_PROPERTY);
        if (flag == null || flag.isBlank() || flag.equals("0") || flag.equals("false")) {
            autoDbIndexStarted = true;
            return;
        }
        RecipeApi api;
        try {
            api = RecipeApi.instance();
        } catch (RuntimeException notReady) {
            if (notReady.getMessage() != null && notReady.getMessage().contains("not initialized")) {
                return; // retried on the next tick
            }
            autoDbIndexStarted = true;
            LOG.error("cirrus_test autodbindex failed", notReady);
            return;
        }
        autoDbIndexStarted = true;
        Thread worker = new Thread(() -> runAutoDbIndex(api), "cirrus-test-autodbindex");
        worker.setDaemon(true);
        worker.start();
    }

    private void runAutoDbIndex(RecipeApi api) {
        try {
            List<RecipeSystem<?>> systems = new ArrayList<>();
            Map<Namespace, List<Namespace>> systemTags = new HashMap<>();
            for (RecipeSystem<?> system : api.allSystems()) {
                systems.add(system);
                ArrayList<Namespace> tags = new ArrayList<>();
                api.getSystemTags(system.systemId()).forEach(tags::add);
                if (!tags.isEmpty()) {
                    systemTags.put(system.systemId(), List.copyOf(tags));
                }
            }
            for (int run = 1; run <= 2; run++) {
                long start = System.nanoTime();
                RecipeDatabaseIndex index = RecipeDatabaseCatalogCache.getOrBuild(
                    systems,
                    systemTags,
                    RecipeDatabaseIndex.BuildProgress.none(),
                    RecipeDatabaseFailureRegistry.shared()
                );
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                LOG.info("autodbindex run={} elapsed={} ms records={}", run, elapsedMs, index.records().size());
            }
        } catch (RuntimeException exception) {
            LOG.error("autodbindex failed", exception);
        }
    }

    //endregion

    static boolean outputsMimic(TestDataPlugin.DummyRecipe recipe, String mimicPath) {
        for (OutputContent output : recipe.outputs()) {
            if (output instanceof StackContent<?, ?> stack && stack.isItem()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey((Item) stack.content().content());
                if (MOD_ID.equals(key.getNamespace()) && key.getPath().equals(mimicPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    //endregion

    //region mimic id mapping

    /**
     * Flattens a raw content id into an own registry name:
     * {@code minecraft:iron_ingot} -> {@code minecraft__iron_ingot}. Characters
     * illegal in a registry path are replaced with {@code _}.
     *
     * <p>NOTE: {@code datagen/TestDataGenerator} keeps a local copy of this
     * mapping (calling into this class from the plain-JVM generator would
     * trigger static init of the DeferredRegisters). Keep both in sync.
     */
    public static String mimicName(String rawId) {
        int split = rawId.indexOf(':');
        String namespace = split >= 0 ? rawId.substring(0, split) : "minecraft";
        String path = split >= 0 ? rawId.substring(split + 1) : rawId;
        return sanitize(namespace) + "__" + sanitize(path);
    }

    private static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = c == '_' || c == '-' || c == '.' || c == '/'
                || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            out.append(valid ? c : '_');
        }
        return out.toString();
    }

    public static ResourceLocation mimicId(String rawId) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, mimicName(rawId));
    }

    /** The registered mimic item for a raw id; fails fast when the generator and runtime drift apart. */
    public static Item mimicItem(String rawId) {
        Item item = BuiltInRegistries.ITEM.get(mimicId(rawId));
        if (item == Items.AIR) {
            throw new IllegalStateException("mimic item not registered: " + rawId);
        }
        return item;
    }

    public static Fluid mimicFluid(String rawId) {
        Fluid fluid = BuiltInRegistries.FLUID.get(mimicId(rawId));
        if (fluid == Fluids.EMPTY) {
            throw new IllegalStateException("mimic fluid not registered: " + rawId);
        }
        return fluid;
    }

    public static TagKey<Item> mimicItemTag(String rawTagId) {
        return TagKey.create(Registries.ITEM, mimicId(rawTagId));
    }

    public static TagKey<Fluid> mimicFluidTag(String rawTagId) {
        return TagKey.create(Registries.FLUID, mimicId(rawTagId));
    }

    private static void mimicFluidRegistration(String rawId) {
        String name = mimicName(rawId);
        DeferredHolder<FluidType, FluidType> type =
            FLUID_TYPES.register(name, () -> fluidType(rawId));
        BaseFlowingFluid.Properties[] properties = new BaseFlowingFluid.Properties[1];
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source =
            FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties[0]));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing =
            FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(properties[0]));
        properties[0] = new BaseFlowingFluid.Properties(type, source, flowing);
    }

    //region fluid visuals

    // vanilla water sprites, guaranteed on the block atlas; without a real
    // texture the fluid renderer asks the atlas for `null` and crashes
    private static final ResourceLocation FLUID_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLUID_FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

    /** stable per-fluid colors so different mimic fluids read differently in the UI */
    private static final int[] FLUID_TINTS = {
        0xFF3F76E4, 0xFFE08A3C, 0xFF50C878, 0xFFB048B0,
        0xFFD4D45C, 0xFF4FD8D8, 0xFFE05A5A, 0xFF8CE06A
    };

    /**
     * A fluid type with real textures. {@code tintKey} (the raw id) picks a
     * stable tint; {@code null} uses the showcase dummy fluid's fixed color.
     */
    private static FluidType fluidType(@Nullable String tintKey) {
        int tint = tintKey == null
            ? 0xFFB048B0
            : FLUID_TINTS[Math.floorMod(tintKey.hashCode(), FLUID_TINTS.length)];
        return new FluidType(FluidType.Properties.create()) {
            @Override
            public void initializeClient(java.util.function.Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return FLUID_STILL;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return FLUID_FLOWING;
                    }

                    @Override
                    public int getTintColor() {
                        return tint;
                    }
                });
            }
        };
    }

    //endregion

    //endregion

    private static DeferredItem<Item> item(String name) {
        SHOWCASE_ITEMS.add(name);
        return ITEMS.registerItem(name, Item::new, new Item.Properties());
    }

    private static BaseFlowingFluid.Properties dummyFluidProperties() {
        return new BaseFlowingFluid.Properties(DUMMY_FLUID_TYPE, DUMMY_FLUID, DUMMY_FLUID_FLOWING);
    }
}
