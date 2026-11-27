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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
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
 * Native vanilla anvil recipe system covering Enchanted Book level-combining
 * ({@code Lv N + Lv N -> Lv N+1}) and material tool/armor repair.
 */
public final class AnvilRecipeSystem extends BasicRecipeSystem<AnvilRecipeSystem.VanillaAnvilRecipe> {

    public static final Namespace systemId = Namespace.ofMc("anvil");

    private static final RecipeScreeningHints repairHints = RecipeScreeningHints.builder()
            .targetOnly()
            .penalty(1000)
            .reasonKey("cirrus.screening.anvil_repair")
            .build();

    private volatile @Nullable CachedSnapshot cachedSnapshot;

    public AnvilRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return VanillaAnvilRecipe.class;
    }

    @Override
    public ResourceLocation getId(VanillaAnvilRecipe recipe) {
        return recipe.id();
    }

    @Override
    public @Nullable VanillaAnvilRecipe findRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.tryParse(uniqueId);
        return id == null ? null : findRecipe(id);
    }

    @Override
    public @Nullable VanillaAnvilRecipe findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(VanillaAnvilRecipe recipe) {
        return recipe.id().hashCode();
    }

    @Override
    public boolean sameRecipe(VanillaAnvilRecipe first, VanillaAnvilRecipe second) {
        return Objects.equals(first.id(), second.id());
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, VanillaAnvilRecipe recipe) {
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
    public @Nullable RoutingRecipeView routingView(ResolveContext context, VanillaAnvilRecipe recipe) {
        MatchStrategy strategy = recipe.kind() == Kind.ENCHANTED_BOOK_UPGRADE
                ? MatchStrategy.identityAndComponent
                : MatchStrategy.identity;
        ContentStack<Item, ItemStack> leftCs = ContentStack.fromStack(recipe.leftInput());
        ContentStack<Item, ItemStack> outCs = ContentStack.fromStack(recipe.output());
        if (leftCs.isEmpty() || outCs.isEmpty() || recipe.rightInput().isEmpty()) {
            return null;
        }
        InputContent left = InputContent.fromStack(leftCs, RecipeContent.MAX_CHANCE, strategy);
        OutputContent out = OutputContent.fromStack(outCs, strategy);
        if (left.isEmpty() || out.isEmpty()) {
            return null;
        }
        return new RoutingRecipeView(List.of(left, recipe.rightInput()), List.of(out));
    }

    @Override
    public RecipeScreeningHints routingHints(VanillaAnvilRecipe recipe) {
        return recipe.kind() == Kind.MATERIAL_REPAIR ? repairHints : RecipeScreeningHints.empty();
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.ANVIL));
    }

    @Override
    public @Unmodifiable RichIterable<VanillaAnvilRecipe> allRecipes() {
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
        List<VanillaAnvilRecipe> recipes = new ArrayList<>();
        Map<ResourceLocation, VanillaAnvilRecipe> byId = new LinkedHashMap<>();

        // 1. Enchanted Book combining: Lv N + Lv N -> Lv N+1
        RegistryAccess access = VanillaWorldAccess.registryAccessOrNull();
        if (access != null) {
            access.lookup(Registries.ENCHANTMENT).ifPresent(registry -> registry.listElements().forEach(holder -> {
                Enchantment enchantment = holder.value();
                int minLevel = enchantment.getMinLevel();
                int maxLevel = enchantment.getMaxLevel();
                if (maxLevel <= minLevel) {
                    return;
                }
                String enchSegment = VanillaWorldAccess.sanitizePathSegment(holder.key().location());
                for (int level = minLevel; level < maxLevel; level++) {
                    ItemStack inputBook = EnchantedBookItem
                            .createForEnchantment(new EnchantmentInstance(holder, level));
                    ItemStack outputBook = EnchantedBookItem
                            .createForEnchantment(new EnchantmentInstance(holder, level + 1));
                    ContentStack<Item, ItemStack> rightCs = ContentStack.fromStack(inputBook);
                    if (rightCs.isEmpty()) {
                        continue;
                    }
                    InputContent right = InputContent
                            .fromStack(rightCs, RecipeContent.MAX_CHANCE, MatchStrategy.identityAndComponent);
                    ResourceLocation id = VanillaWorldAccess.vanillaId(
                        "anvil/book_combine",
                        enchSegment,
                        level + "_to_" + (level + 1)
                    );
                    VanillaAnvilRecipe recipe = new VanillaAnvilRecipe(
                        id,
                        Kind.ENCHANTED_BOOK_UPGRADE,
                        inputBook,
                        right,
                        outputBook
                    );
                    recipes.add(recipe);
                    byId.put(id, recipe);
                }
            }));
        }

        // 2. Material repair for tiered tools, armor, and special vanilla repairables
        BuiltInRegistries.ITEM.holders().forEach((Holder.Reference<Item> holder) -> {
            Item item = holder.value();
            if (item == Items.AIR) {
                return;
            }
            Ingredient repairMaterial = repairIngredient(item);
            if (repairMaterial.isEmpty()) {
                return;
            }
            InputContent right = InputContent.fromItemIngredient(repairMaterial);
            if (right.isEmpty()) {
                return;
            }
            ItemStack gear = new ItemStack(item);
            ResourceLocation id = VanillaWorldAccess.vanillaId(
                "anvil/repair",
                VanillaWorldAccess.sanitizePathSegment(holder.key().location())
            );
            VanillaAnvilRecipe recipe = new VanillaAnvilRecipe(id, Kind.MATERIAL_REPAIR, gear, right, gear.copy());
            recipes.add(recipe);
            byId.put(id, recipe);
        });

        return new CachedSnapshot(version, List.copyOf(recipes), Map.copyOf(byId));
    }

    private static Ingredient repairIngredient(Item item) {
        try {
            if (item instanceof TieredItem tiered) {
                return tiered.getTier().getRepairIngredient();
            }
            if (item instanceof ArmorItem armor) {
                return armor.getMaterial().value().repairIngredient().get();
            }
            if (item == Items.ELYTRA) {
                return Ingredient.of(Items.PHANTOM_MEMBRANE);
            }
            if (item == Items.SHIELD) {
                return Ingredient.of(ItemTags.PLANKS);
            }
            if (item == Items.MACE) {
                return Ingredient.of(Items.BREEZE_ROD);
            }
        } catch (RuntimeException ignored) {}
        return Ingredient.EMPTY;
    }

    public enum Kind {
        ENCHANTED_BOOK_UPGRADE,
        MATERIAL_REPAIR
    }

    public record VanillaAnvilRecipe(
        ResourceLocation id,
        Kind kind,
        ItemStack leftInput,
        InputContent rightInput,
        ItemStack output
    ) {
        public VanillaAnvilRecipe {
            leftInput = leftInput.copy();
            output = output.copy();
        }
    }

    private record CachedSnapshot(
        long version,
        List<VanillaAnvilRecipe> recipes,
        Map<ResourceLocation, VanillaAnvilRecipe> byId
    ) {}

    @Override
    public String toString() {
        return "AnvilRecipeSystem";
    }
}
