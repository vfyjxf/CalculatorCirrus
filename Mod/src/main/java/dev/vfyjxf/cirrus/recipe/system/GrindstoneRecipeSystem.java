package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
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
 * Native vanilla grindstone system covering enchanted book disenchanting and
 * damageable gear repair combining.
 */
public final class GrindstoneRecipeSystem extends BasicRecipeSystem<GrindstoneRecipeSystem.VanillaGrindstoneRecipe> {

    public static final Namespace systemId = Namespace.ofMc("grindstone");

    private static final RecipeScreeningHints disenchantHints = RecipeScreeningHints.builder()
            .lastResort()
            .recycling()
            .penalty(600)
            .reasonKey("cirrus.screening.grindstone_disenchant")
            .build();

    private static final RecipeScreeningHints repairCombineHints = RecipeScreeningHints.builder()
            .targetOnly()
            .recycling()
            .penalty(1000)
            .reasonKey("cirrus.screening.grindstone_repair")
            .build();

    private volatile @Nullable CachedSnapshot cachedSnapshot;

    public GrindstoneRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return VanillaGrindstoneRecipe.class;
    }

    @Override
    public ResourceLocation getId(VanillaGrindstoneRecipe recipe) {
        return recipe.id();
    }

    @Override
    public @Nullable VanillaGrindstoneRecipe findRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.tryParse(uniqueId);
        return id == null ? null : findRecipe(id);
    }

    @Override
    public @Nullable VanillaGrindstoneRecipe findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(VanillaGrindstoneRecipe recipe) {
        return recipe.id().hashCode();
    }

    @Override
    public boolean sameRecipe(VanillaGrindstoneRecipe first, VanillaGrindstoneRecipe second) {
        return Objects.equals(first.id(), second.id());
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, VanillaGrindstoneRecipe recipe) {
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
    public @Nullable RoutingRecipeView routingView(ResolveContext context, VanillaGrindstoneRecipe recipe) {
        ContentStack<Item, ItemStack> inputStack = ContentStack.fromStack(recipe.input(), recipe.inputCount());
        OutputContent output = OutputContent.fromItemStack(recipe.output());
        if (inputStack.isEmpty() || output.isEmpty()) {
            return null;
        }
        InputContent input = InputContent.fromStack(inputStack, RecipeContent.MAX_CHANCE, MatchStrategy.identity);
        if (input.isEmpty()) {
            return null;
        }
        return new RoutingRecipeView(List.of(input), List.of(output));
    }

    @Override
    public RecipeScreeningHints routingHints(VanillaGrindstoneRecipe recipe) {
        return recipe.kind() == Kind.disenchantBook ? disenchantHints : repairCombineHints;
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.GRINDSTONE));
    }

    @Override
    public @Unmodifiable RichIterable<VanillaGrindstoneRecipe> allRecipes() {
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
        List<VanillaGrindstoneRecipe> recipes = new ArrayList<>();
        Map<ResourceLocation, VanillaGrindstoneRecipe> byId = new LinkedHashMap<>();

        // 1. Disenchant Enchanted Book -> Book
        ResourceLocation disenchantId = VanillaWorldAccess.vanillaId("grindstone", "disenchant_enchanted_book");
        VanillaGrindstoneRecipe disenchant = new VanillaGrindstoneRecipe(
            disenchantId,
            Kind.disenchantBook,
            new ItemStack(Items.ENCHANTED_BOOK),
            1,
            new ItemStack(Items.BOOK)
        );
        recipes.add(disenchant);
        byId.put(disenchantId, disenchant);

        // 2. Combine 2 damageable items -> 1 repaired item
        BuiltInRegistries.ITEM.holders().forEach((Holder.Reference<Item> holder) -> {
            Item item = holder.value();
            if (item == Items.AIR) {
                return;
            }
            ItemStack sample = new ItemStack(item);
            if (!sample.isDamageableItem()) {
                return;
            }
            ResourceLocation id = VanillaWorldAccess.vanillaId(
                "grindstone/repair",
                VanillaWorldAccess.sanitizePathSegment(holder.key().location())
            );
            VanillaGrindstoneRecipe recipe = new VanillaGrindstoneRecipe(
                id,
                Kind.repairCombine,
                sample,
                2,
                sample.copy()
            );
            recipes.add(recipe);
            byId.put(id, recipe);
        });

        return new CachedSnapshot(version, List.copyOf(recipes), Map.copyOf(byId));
    }

    public enum Kind {
        disenchantBook,
        repairCombine
    }

    public record VanillaGrindstoneRecipe(
        ResourceLocation id,
        Kind kind,
        ItemStack input,
        int inputCount,
        ItemStack output
    ) {
        public VanillaGrindstoneRecipe {
            input = input.copy();
            output = output.copy();
            inputCount = Math.max(1, inputCount);
        }
    }

    private record CachedSnapshot(
        long version,
        List<VanillaGrindstoneRecipe> recipes,
        Map<ResourceLocation, VanillaGrindstoneRecipe> byId
    ) {}

    @Override
    public String toString() {
        return "GrindstoneRecipeSystem";
    }
}
