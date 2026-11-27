package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
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
 * Native vanilla furnace fuel system backed by {@link NeoForgeDataMaps#FURNACE_FUELS}
 * and {@link ItemStack#getBurnTime(RecipeType)}.
 */
public final class FuelRecipeSystem extends BasicRecipeSystem<FuelRecipeSystem.VanillaFuelRecipe> {

    public static final Namespace systemId = Namespace.ofMc("fueling");

    private volatile @Nullable CachedSnapshot cachedSnapshot;

    public FuelRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return VanillaFuelRecipe.class;
    }

    @Override
    public ResourceLocation getId(VanillaFuelRecipe recipe) {
        return recipe.id();
    }

    @Override
    public @Nullable VanillaFuelRecipe findRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.tryParse(uniqueId);
        return id == null ? null : findRecipe(id);
    }

    @Override
    public @Nullable VanillaFuelRecipe findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(VanillaFuelRecipe recipe) {
        return recipe.id().hashCode();
    }

    @Override
    public boolean sameRecipe(VanillaFuelRecipe first, VanillaFuelRecipe second) {
        return Objects.equals(first.id(), second.id());
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, VanillaFuelRecipe recipe) {
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
    public @Nullable RoutingRecipeView routingView(ResolveContext context, VanillaFuelRecipe recipe) {
        InputContent fuel = InputContent.fromItemStack(new ItemStack(recipe.fuelItem()), 1);
        if (fuel.isEmpty() || recipe.burnTicks() <= 0) {
            return null;
        }
        List<OutputContent> outputs = new ArrayList<>(2);
        outputs.add(OutputContent.fromTimeTick(recipe.burnTicks()));
        if (!recipe.remainingContainer().isEmpty()) {
            OutputContent remainder = OutputContent.fromItemStack(recipe.remainingContainer());
            if (!remainder.isEmpty()) {
                outputs.add(remainder);
            }
        }
        return new RoutingRecipeView(List.of(fuel), outputs);
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.FURNACE));
    }

    @Override
    public @Unmodifiable RichIterable<VanillaFuelRecipe> allRecipes() {
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
        List<VanillaFuelRecipe> recipes = new ArrayList<>();
        Map<ResourceLocation, VanillaFuelRecipe> byId = new LinkedHashMap<>();

        BuiltInRegistries.ITEM.holders().forEach((Holder.Reference<Item> holder) -> {
            Item item = holder.value();
            if (item == Items.AIR) {
                return;
            }
            ItemStack stack = new ItemStack(item);
            int burnTicks = resolveBurnTime(holder, stack);
            if (burnTicks <= 0) {
                return;
            }
            ItemStack remainder = stack.getCraftingRemainingItem();
            ResourceLocation id = VanillaWorldAccess.vanillaId(
                "fueling",
                VanillaWorldAccess.sanitizePathSegment(holder.key().location())
            );
            VanillaFuelRecipe recipe = new VanillaFuelRecipe(id, item, burnTicks, remainder.copy());
            recipes.add(recipe);
            byId.put(id, recipe);
        });

        return new CachedSnapshot(version, List.copyOf(recipes), Map.copyOf(byId));
    }

    private static int resolveBurnTime(Holder.Reference<Item> holder, ItemStack stack) {
        try {
            int burnTime = stack.getBurnTime(RecipeType.SMELTING);
            if (burnTime > 0) {
                return burnTime;
            }
        } catch (RuntimeException ignored) {}
        FurnaceFuel fuel = holder.getData(NeoForgeDataMaps.FURNACE_FUELS);
        return fuel == null ? 0 : Math.max(0, fuel.burnTime());
    }

    public record VanillaFuelRecipe(
        ResourceLocation id,
        Item fuelItem,
        int burnTicks,
        ItemStack remainingContainer
    ) {
        public VanillaFuelRecipe {
            remainingContainer = remainingContainer.copy();
        }
    }

    private record CachedSnapshot(
        long version,
        List<VanillaFuelRecipe> recipes,
        Map<ResourceLocation, VanillaFuelRecipe> byId
    ) {}

    @Override
    public String toString() {
        return "FuelRecipeSystem";
    }
}
