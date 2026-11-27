package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
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
 * Native vanilla composter recipe system backed by {@link NeoForgeDataMaps#COMPOSTABLES}
 * with fallback to {@link ComposterBlock#COMPOSTABLES}.
 */
public final class CompostingRecipeSystem extends BasicRecipeSystem<CompostingRecipeSystem.VanillaCompostingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("composting");

    private static final RecipeScreeningHints compostingHints = RecipeScreeningHints.builder()
            .recycling()
            .reasonKey("cirrus.screening.composting")
            .build();

    private volatile @Nullable CachedSnapshot cachedSnapshot;

    public CompostingRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return VanillaCompostingRecipe.class;
    }

    @Override
    public ResourceLocation getId(VanillaCompostingRecipe recipe) {
        return recipe.id();
    }

    @Override
    public @Nullable VanillaCompostingRecipe findRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.tryParse(uniqueId);
        return id == null ? null : findRecipe(id);
    }

    @Override
    public @Nullable VanillaCompostingRecipe findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(VanillaCompostingRecipe recipe) {
        return recipe.id().hashCode();
    }

    @Override
    public boolean sameRecipe(VanillaCompostingRecipe first, VanillaCompostingRecipe second) {
        return Objects.equals(first.id(), second.id());
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, VanillaCompostingRecipe recipe) {
        RoutingRecipeView view = routingView(context, recipe);
        if (view == null) {
            return ResolveResult.failed(ResolveError.invalidRecipe);
        }
        List<InputContent> inputs = new ArrayList<>();
        view.inputs().forEach(inputs::add);
        List<OutputContent> outputs = new ArrayList<>();
        view.outputs().forEach(outputs::add);
        return ResolveResult.resolved(RecipeProjection.create(this, recipe, inputs, outputs));
    }

    @Override
    public @Nullable RoutingRecipeView routingView(ResolveContext context, VanillaCompostingRecipe recipe) {
        InputContent input = InputContent.fromItemStack(new ItemStack(recipe.inputItem()), recipe.expectedInputCount());
        OutputContent output = OutputContent.fromItemStack(new ItemStack(Items.BONE_MEAL));
        if (input.isEmpty() || output.isEmpty()) {
            return null;
        }
        return new RoutingRecipeView(List.of(input), List.of(output));
    }

    @Override
    public RecipeScreeningHints routingHints(VanillaCompostingRecipe recipe) {
        return compostingHints;
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.COMPOSTER));
    }

    @Override
    public @Unmodifiable RichIterable<VanillaCompostingRecipe> allRecipes() {
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
        List<VanillaCompostingRecipe> recipes = new ArrayList<>();
        Map<ResourceLocation, VanillaCompostingRecipe> byId = new LinkedHashMap<>();

        BuiltInRegistries.ITEM.holders().forEach((Holder.Reference<Item> holder) -> {
            Item item = holder.value();
            if (item == Items.AIR) {
                return;
            }
            float chance = resolveChance(holder, item);
            if (chance <= 0.0f) {
                return;
            }
            int expectedCount = Math.max(1, (int) Math.ceil(7.0 / chance));
            ResourceLocation itemKey = holder.key().location();
            ResourceLocation id = VanillaWorldAccess.vanillaId(
                "composting",
                VanillaWorldAccess.sanitizePathSegment(itemKey)
            );
            VanillaCompostingRecipe recipe = new VanillaCompostingRecipe(id, item, chance, expectedCount);
            recipes.add(recipe);
            byId.put(id, recipe);
        });

        return new CachedSnapshot(version, List.copyOf(recipes), Map.copyOf(byId));
    }

    @SuppressWarnings("deprecation")
    private static float resolveChance(Holder.Reference<Item> holder, Item item) {
        Compostable data = holder.getData(NeoForgeDataMaps.COMPOSTABLES);
        if (data != null && data.chance() > 0.0f) {
            return data.chance();
        }
        return ComposterBlock.COMPOSTABLES.getFloat(item);
    }

    public record VanillaCompostingRecipe(
        ResourceLocation id,
        Item inputItem,
        float chance,
        int expectedInputCount
    ) {}

    private record CachedSnapshot(
        long version,
        List<VanillaCompostingRecipe> recipes,
        Map<ResourceLocation, VanillaCompostingRecipe> byId
    ) {}

    @Override
    public String toString() {
        return "CompostingRecipeSystem";
    }
}
