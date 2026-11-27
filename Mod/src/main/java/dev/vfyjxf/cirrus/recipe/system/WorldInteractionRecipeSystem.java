package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.mixin.AxeItemAccessor;
import dev.vfyjxf.cirrus.mixin.ConcretePowderBlockAccessor;
import dev.vfyjxf.cirrus.mixin.ShovelItemAccessor;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.Oxidizable;
import net.neoforged.neoforge.registries.datamaps.builtin.Strippable;
import net.neoforged.neoforge.registries.datamaps.builtin.Waxable;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Native vanilla in-world interaction recipes covering data-driven block and item conversions:
 * log stripping, copper waxing/dewaxing/oxidation/scraping, concrete hardening, dirt-to-mud,
 * water bottle filling, and shovel path flattening.
 */
public final class WorldInteractionRecipeSystem
        extends BasicRecipeSystem<WorldInteractionRecipeSystem.VanillaWorldInteractionRecipe> {

    public static final Namespace systemId = Namespace.ofMc("world_interaction");

    private static final RecipeScreeningHints dewaxHints = RecipeScreeningHints.builder()
            .discouraged()
            .penalty(200)
            .reasonKey("cirrus.screening.world_dewax")
            .build();

    private static final RecipeScreeningHints scrapeHints = RecipeScreeningHints.builder()
            .discouraged()
            .penalty(250)
            .reasonKey("cirrus.screening.world_scrape")
            .build();

    private static final RecipeScreeningHints oxidationHints = RecipeScreeningHints.builder()
            .discouraged()
            .penalty(400)
            .reasonKey("cirrus.screening.world_oxidation")
            .build();

    private static final RecipeScreeningHints flattenHints = RecipeScreeningHints.builder()
            .discouraged()
            .penalty(200)
            .reasonKey("cirrus.screening.world_flatten")
            .build();

    private volatile @Nullable CachedSnapshot cachedSnapshot;

    public WorldInteractionRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return VanillaWorldInteractionRecipe.class;
    }

    @Override
    public ResourceLocation getId(VanillaWorldInteractionRecipe recipe) {
        return recipe.id();
    }

    @Override
    public @Nullable VanillaWorldInteractionRecipe findRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.tryParse(uniqueId);
        return id == null ? null : findRecipe(id);
    }

    @Override
    public @Nullable VanillaWorldInteractionRecipe findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(VanillaWorldInteractionRecipe recipe) {
        return recipe.id().hashCode();
    }

    @Override
    public boolean sameRecipe(VanillaWorldInteractionRecipe first, VanillaWorldInteractionRecipe second) {
        return Objects.equals(first.id(), second.id());
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, VanillaWorldInteractionRecipe recipe) {
        RoutingRecipeView view = routingView(context, recipe);
        if (view == null) {
            return ResolveResult.failed(ResolveError.invalidRecipe);
        }
        return ResolveResult.resolved(RecipeProjection.create(this, recipe, recipe.inputs(), recipe.outputs()));
    }

    @Override
    public @Nullable RoutingRecipeView routingView(ResolveContext context, VanillaWorldInteractionRecipe recipe) {
        if (recipe.inputs().isEmpty() || recipe.outputs().isEmpty()) {
            return null;
        }
        return new RoutingRecipeView(recipe.inputs(), recipe.outputs());
    }

    @Override
    public RecipeScreeningHints routingHints(VanillaWorldInteractionRecipe recipe) {
        return recipe.hints();
    }

    @Override
    public @Unmodifiable RichIterable<VanillaWorldInteractionRecipe> allRecipes() {
        return Lists.immutable.withAll(snapshot().recipes());
    }

    @Override
    public long lookupVersion() {
        return snapshot().version();
    }

    private CachedSnapshot snapshot() {
        long version = VanillaWorldAccess.worldEpoch();
        CachedSnapshot current = cachedSnapshot;
        if (current != null && current.version == version) {
            return current;
        }
        CachedSnapshot built = buildSnapshot(version);
        cachedSnapshot = built;
        return built;
    }

    private static CachedSnapshot buildSnapshot(long version) {
        List<VanillaWorldInteractionRecipe> recipes = new ArrayList<>();
        Map<ResourceLocation, VanillaWorldInteractionRecipe> byId = new LinkedHashMap<>();

        InputContent axeCatalyst = InputContent.fromItemTag(ItemTags.AXES, 1, 0.0f);
        InputContent shovelCatalyst = InputContent.fromItemTag(ItemTags.SHOVELS, 1, 0.0f);
        InputContent waterBucketCatalyst = InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET), 1, 0.0f);
        ItemStack waterBottleStack = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        ContentStack<Item, ItemStack> waterBottleCs = ContentStack.fromStack(waterBottleStack);

        // 1. Log/Wood Stripping (NeoForgeDataMaps.STRIPPABLES + AxeItem.STRIPPABLES)
        Map<Block, Block> strippables = collectStrippables();
        for (Map.Entry<Block, Block> entry : strippables.entrySet()) {
            Item fromItem = entry.getKey().asItem();
            Item toItem = entry.getValue().asItem();
            if (fromItem == Items.AIR || toItem == Items.AIR) {
                continue;
            }
            addIfValid(
                recipes,
                byId,
                VanillaWorldAccess.vanillaId("world_interaction/strip", itemPath(fromItem)),
                Kind.STRIPPING,
                nonEmptyInputs(InputContent.fromItemStack(new ItemStack(fromItem), 1), axeCatalyst),
                List.of(OutputContent.fromItemStack(new ItemStack(toItem))),
                RecipeScreeningHints.empty()
            );
        }

        // 2. Copper Waxing & Dewaxing (NeoForgeDataMaps.WAXABLES + HoneycombItem.WAXABLES)
        Map<Block, Block> waxables = collectWaxables();
        for (Map.Entry<Block, Block> entry : waxables.entrySet()) {
            Item fromItem = entry.getKey().asItem();
            Item toItem = entry.getValue().asItem();
            if (fromItem == Items.AIR || toItem == Items.AIR) {
                continue;
            }
            addIfValid(
                recipes,
                byId,
                VanillaWorldAccess.vanillaId("world_interaction/wax", itemPath(fromItem)),
                Kind.WAXING,
                List.of(
                    InputContent.fromItemStack(new ItemStack(fromItem), 1),
                    InputContent.fromItemStack(new ItemStack(Items.HONEYCOMB), 1)
                ),
                List.of(OutputContent.fromItemStack(new ItemStack(toItem))),
                RecipeScreeningHints.empty()
            );
            addIfValid(
                recipes,
                byId,
                VanillaWorldAccess.vanillaId("world_interaction/dewax", itemPath(toItem)),
                Kind.DEWAXING,
                nonEmptyInputs(InputContent.fromItemStack(new ItemStack(toItem), 1), axeCatalyst),
                List.of(OutputContent.fromItemStack(new ItemStack(fromItem))),
                dewaxHints
            );
        }

        // 3. Copper Oxidation Aging & Scraping (NeoForgeDataMaps.OXIDIZABLES + WeatheringCopper.NEXT_BY_BLOCK)
        Map<Block, Block> oxidizables = collectOxidizables();
        for (Map.Entry<Block, Block> entry : oxidizables.entrySet()) {
            Item fromItem = entry.getKey().asItem();
            Item toItem = entry.getValue().asItem();
            if (fromItem == Items.AIR || toItem == Items.AIR) {
                continue;
            }
            addIfValid(
                recipes,
                byId,
                VanillaWorldAccess.vanillaId("world_interaction/oxidize", itemPath(fromItem)),
                Kind.OXIDATION_AGING,
                List.of(InputContent.fromItemStack(new ItemStack(fromItem), 1)),
                List.of(OutputContent.fromItemStack(new ItemStack(toItem))),
                oxidationHints
            );
            addIfValid(
                recipes,
                byId,
                VanillaWorldAccess.vanillaId("world_interaction/scrape", itemPath(toItem)),
                Kind.SCRAPING_OXIDATION,
                nonEmptyInputs(InputContent.fromItemStack(new ItemStack(toItem), 1), axeCatalyst),
                List.of(OutputContent.fromItemStack(new ItemStack(fromItem))),
                scrapeHints
            );
        }

        // 4. Concrete Powder -> Concrete (water catalyst)
        BuiltInRegistries.BLOCK.holders().forEach((Holder.Reference<Block> holder) -> {
            if (holder.value() instanceof ConcretePowderBlock powder) {
                Block concrete = ((ConcretePowderBlockAccessor) powder).cirrus$concrete();
                Item powderItem = powder.asItem();
                Item concreteItem = concrete.asItem();
                if (powderItem == Items.AIR || concreteItem == Items.AIR) {
                    return;
                }
                addIfValid(
                    recipes,
                    byId,
                    VanillaWorldAccess.vanillaId("world_interaction/harden", itemPath(powderItem)),
                    Kind.CONCRETE_HARDENING,
                    nonEmptyInputs(InputContent.fromItemStack(new ItemStack(powderItem), 1), waterBucketCatalyst),
                    List.of(OutputContent.fromItemStack(new ItemStack(concreteItem))),
                    RecipeScreeningHints.empty()
                );
            }
        });

        // 5. Glass Bottle + Water Bucket (catalyst) -> Water Bottle
        if (waterBottleCs.notEmpty()) {
            addIfValid(
                recipes,
                byId,
                VanillaWorldAccess.vanillaId("world_interaction", "fill_water_bottle"),
                Kind.BOTTLE_WATER_FILL,
                nonEmptyInputs(InputContent.fromItemStack(new ItemStack(Items.GLASS_BOTTLE), 1), waterBucketCatalyst),
                List.of(OutputContent.fromStack(waterBottleCs, MatchStrategy.identityAndComponent)),
                RecipeScreeningHints.empty()
            );

            // 6. Dirt / Coarse Dirt / Rooted Dirt + Water Bottle -> Mud + Glass Bottle
            InputContent waterBottleInput = InputContent
                    .fromStack(waterBottleCs, RecipeContent.MAX_CHANCE, MatchStrategy.identityAndComponent);
            for (Item dirt : List.of(Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT)) {
                addIfValid(
                    recipes,
                    byId,
                    VanillaWorldAccess.vanillaId("world_interaction/mud", itemPath(dirt)),
                    Kind.DIRT_TO_MUD,
                    List.of(InputContent.fromItemStack(new ItemStack(dirt), 1), waterBottleInput),
                    List.of(
                        OutputContent.fromItemStack(new ItemStack(Items.MUD)),
                        OutputContent.fromItemStack(new ItemStack(Items.GLASS_BOTTLE))
                    ),
                    RecipeScreeningHints.empty()
                );
            }
        }

        // 7. Shovel Flattening (e.g. Grass/Dirt -> Dirt Path)
        try {
            Map<Block, BlockState> flattenables = ShovelItemAccessor.cirrus$flattenables();
            for (Map.Entry<Block, BlockState> entry : flattenables.entrySet()) {
                Item fromItem = entry.getKey().asItem();
                Item toItem = entry.getValue().getBlock().asItem();
                if (fromItem == Items.AIR || toItem == Items.AIR || fromItem == toItem) {
                    continue;
                }
                addIfValid(
                    recipes,
                    byId,
                    VanillaWorldAccess.vanillaId("world_interaction/flatten", itemPath(fromItem)),
                    Kind.SHOVEL_FLATTENING,
                    nonEmptyInputs(InputContent.fromItemStack(new ItemStack(fromItem), 1), shovelCatalyst),
                    List.of(OutputContent.fromItemStack(new ItemStack(toItem))),
                    flattenHints
                );
            }
        } catch (RuntimeException ignored) {}

        return new CachedSnapshot(version, List.copyOf(recipes), Map.copyOf(byId));
    }

    private static Map<Block, Block> collectStrippables() {
        Map<Block, Block> out = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.holders().forEach(holder -> {
            Strippable data = holder.getData(NeoForgeDataMaps.STRIPPABLES);
            if (data != null && data.strippedBlock() != null) {
                out.put(holder.value(), data.strippedBlock());
            }
        });
        try {
            Map<Block, Block> vanilla = AxeItemAccessor.cirrus$strippables();
            for (Map.Entry<Block, Block> entry : vanilla.entrySet()) {
                out.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    private static Map<Block, Block> collectWaxables() {
        Map<Block, Block> out = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.holders().forEach(holder -> {
            Waxable data = holder.getData(NeoForgeDataMaps.WAXABLES);
            if (data != null && data.waxed() != null) {
                out.put(holder.value(), data.waxed());
            }
        });
        try {
            Map<Block, Block> vanilla = HoneycombItem.WAXABLES.get();
            for (Map.Entry<Block, Block> entry : vanilla.entrySet()) {
                out.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    private static Map<Block, Block> collectOxidizables() {
        Map<Block, Block> out = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.holders().forEach(holder -> {
            Oxidizable data = holder.getData(NeoForgeDataMaps.OXIDIZABLES);
            if (data != null && data.nextOxidationStage() != null) {
                out.put(holder.value(), data.nextOxidationStage());
            }
        });
        try {
            Map<Block, Block> vanilla = WeatheringCopper.NEXT_BY_BLOCK.get();
            for (Map.Entry<Block, Block> entry : vanilla.entrySet()) {
                out.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    private static List<InputContent> nonEmptyInputs(InputContent... contents) {
        List<InputContent> out = new ArrayList<>(contents.length);
        for (InputContent content : contents) {
            if (content != null && !content.isEmpty()) {
                out.add(content);
            }
        }
        return List.copyOf(out);
    }

    private static void addIfValid(
        List<VanillaWorldInteractionRecipe> recipes,
        Map<ResourceLocation, VanillaWorldInteractionRecipe> byId,
        ResourceLocation id,
        Kind kind,
        List<InputContent> inputs,
        List<OutputContent> outputs,
        RecipeScreeningHints hints
    ) {
        if (inputs.isEmpty() || outputs.isEmpty() || byId.containsKey(id)) {
            return;
        }
        for (OutputContent output : outputs) {
            if (output.isEmpty()) {
                return;
            }
        }
        VanillaWorldInteractionRecipe recipe = new VanillaWorldInteractionRecipe(
            id,
            kind,
            List.copyOf(inputs),
            List.copyOf(outputs),
            hints
        );
        recipes.add(recipe);
        byId.put(id, recipe);
    }

    private static String itemPath(Item item) {
        return VanillaWorldAccess.sanitizePathSegment(BuiltInRegistries.ITEM.getKey(item));
    }

    public enum Kind {
        STRIPPING,
        WAXING,
        DEWAXING,
        OXIDATION_AGING,
        SCRAPING_OXIDATION,
        CONCRETE_HARDENING,
        BOTTLE_WATER_FILL,
        DIRT_TO_MUD,
        SHOVEL_FLATTENING
    }

    public record VanillaWorldInteractionRecipe(
        ResourceLocation id,
        Kind kind,
        List<InputContent> inputs,
        List<OutputContent> outputs,
        RecipeScreeningHints hints
    ) {}

    private record CachedSnapshot(
        long version,
        List<VanillaWorldInteractionRecipe> recipes,
        Map<ResourceLocation, VanillaWorldInteractionRecipe> byId
    ) {}

    @Override
    public String toString() {
        return "WorldInteractionRecipeSystem";
    }
}
