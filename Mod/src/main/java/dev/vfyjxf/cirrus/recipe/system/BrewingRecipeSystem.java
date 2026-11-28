package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.mixin.PotionBrewingAccessor;
import dev.vfyjxf.cirrus.mixin.PotionBrewingMixAccessor;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Native vanilla brewing recipe system backed directly by Minecraft's {@link PotionBrewing}
 * (no JEI runtime dependency). Works on server, client, and headless test environments.
 */
public final class BrewingRecipeSystem extends BasicRecipeSystem<VanillaBrewingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("brewing");

    private volatile @Nullable CachedSnapshot cachedSnapshot;

    public BrewingRecipeSystem() {
        super(systemId);
    }

    @Override
    public Class<?> basicType() {
        return VanillaBrewingRecipe.class;
    }

    @Override
    public ResourceLocation getId(VanillaBrewingRecipe recipe) {
        return recipe.id();
    }

    @Override
    public @Nullable VanillaBrewingRecipe findRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.tryParse(uniqueId);
        return id == null ? null : findRecipe(id);
    }

    @Override
    public @Nullable VanillaBrewingRecipe findRecipe(ResourceLocation registryName) {
        return snapshot().byId().get(registryName);
    }

    @Override
    public int hash(VanillaBrewingRecipe recipe) {
        return recipe.id().hashCode();
    }

    @Override
    public boolean sameRecipe(VanillaBrewingRecipe first, VanillaBrewingRecipe second) {
        return Objects.equals(first.id(), second.id());
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, VanillaBrewingRecipe recipe) {
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
    public @Nullable RoutingRecipeView routingView(ResolveContext context, VanillaBrewingRecipe recipe) {
        InputContent potionInput = potionInputContent(recipe.potionInputs());
        InputContent reagentInput = InputContent.fromItemIngredient(recipe.reagent());
        ContentStack<Item, ItemStack> outputStack = ContentStack.fromStack(recipe.output());
        if (potionInput.isEmpty() || reagentInput.isEmpty() || outputStack.isEmpty()) {
            return null;
        }
        OutputContent output = OutputContent.fromStack(outputStack, MatchStrategy.identityAndComponent);
        if (output.isEmpty()) {
            return null;
        }
        InputContent time = InputContent.fromTimeTick(recipe.brewingTicks());
        return new RoutingRecipeView(List.of(potionInput, reagentInput, time), List.of(output));
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.BREWING_STAND));
    }

    @Override
    public @Unmodifiable RichIterable<VanillaBrewingRecipe> allRecipes() {
        return Lists.immutable.withAll(snapshot().recipes());
    }

    @Override
    public long lookupVersion() {
        return snapshot().version();
    }

    /**
     * Bridges a JEI {@link IJeiBrewingRecipe} (e.g. clicked in the JEI GUI) to the corresponding
     * native {@link VanillaBrewingRecipe}, if present.
     */
    public @Nullable VanillaBrewingRecipe matchJeiRecipe(IJeiBrewingRecipe jeiRecipe) {
        ItemStack jeiOutput = jeiRecipe.getPotionOutput();
        if (jeiOutput.isEmpty()) {
            return null;
        }
        List<ItemStack> jeiInputs = jeiRecipe.getPotionInputs();
        List<ItemStack> jeiReagents = jeiRecipe.getIngredients();
        for (VanillaBrewingRecipe candidate : snapshot().recipes()) {
            if (!ItemStack.isSameItemSameComponents(candidate.output(), jeiOutput)) {
                continue;
            }
            if (!matchesAnyStack(candidate.potionInputs(), jeiInputs)) {
                continue;
            }
            if (!matchesReagent(candidate.reagent(), jeiReagents)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean matchesAnyStack(List<ItemStack> nativeInputs, List<ItemStack> jeiInputs) {
        if (jeiInputs.isEmpty()) {
            return true;
        }
        for (ItemStack candidate : nativeInputs) {
            for (ItemStack jei : jeiInputs) {
                if (jei != null && !jei.isEmpty() && ItemStack.isSameItemSameComponents(candidate, jei)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesReagent(Ingredient nativeReagent, List<ItemStack> jeiReagents) {
        if (jeiReagents.isEmpty()) {
            return true;
        }
        for (ItemStack jei : jeiReagents) {
            if (jei != null && !jei.isEmpty() && nativeReagent.test(jei)) {
                return true;
            }
        }
        return false;
    }

    private static InputContent potionInputContent(List<ItemStack> potionInputs) {
        if (potionInputs.isEmpty()) {
            return InputContent.empty;
        }
        if (potionInputs.size() == 1) {
            ItemStack only = potionInputs.getFirst();
            ContentStack<Item, ItemStack> stack = ContentStack.fromStack(only, Math.max(1, only.getCount()));
            return stack.isEmpty()
                    ? InputContent.empty
                    : InputContent.fromStack(stack, RecipeContent.MAX_CHANCE, MatchStrategy.identityAndComponent);
        }
        return InputContent.fromTypedStackList(CirrusTypes.Contents.itemStack, potionInputs, 1);
    }

    private CachedSnapshot snapshot() {
        PotionBrewing brewing = VanillaWorldAccess.potionBrewingOrNull();
        long epoch = VanillaWorldAccess.worldEpoch();
        long brewingIdentity = brewing == null ? 0L : System.identityHashCode(brewing);
        long version = (epoch * 31L) ^ brewingIdentity;

        CachedSnapshot current = cachedSnapshot;
        if (current != null && current.version == version && current.brewing == brewing) {
            return current;
        }
        CachedSnapshot built = buildSnapshot(brewing, version);
        cachedSnapshot = built;
        return built;
    }

    @SuppressWarnings("unchecked")
    private static CachedSnapshot buildSnapshot(@Nullable PotionBrewing brewing, long version) {
        if (brewing == null) {
            return new CachedSnapshot(null, version, List.of(), Map.of());
        }

        List<VanillaBrewingRecipe> recipes = new ArrayList<>();
        Map<ResourceLocation, VanillaBrewingRecipe> byId = new LinkedHashMap<>();
        Set<RecipeDedupeKey> dedupe = new HashSet<>();

        PotionBrewingAccessor accessor = (PotionBrewingAccessor) brewing;
        List<Ingredient> containers = accessor.cirrusGetContainers();
        List<?> rawPotionMixes = accessor.cirrusGetPotionMixes();
        List<?> rawContainerMixes = accessor.cirrusGetContainerMixes();

        // BFS from Water Potion through all vanilla potion mixes and container mixes.
        ArrayDeque<PotionState> queue = new ArrayDeque<>();
        Set<PotionState> visited = new HashSet<>();

        PotionState waterBottle = new PotionState(Items.POTION, Potions.WATER);
        visited.add(waterBottle);
        queue.add(waterBottle);

        // Also seed any potion mix whose "from" potion isn't reachable via water (e.g. mod-added base potions).
        Set<Holder<Potion>> mixTargets = new HashSet<>();
        for (Object rawMix : rawPotionMixes) {
            PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) rawMix;
            mixTargets.add((Holder<Potion>) mix.cirrusGetTo());
        }
        for (Object rawMix : rawPotionMixes) {
            PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) rawMix;
            Holder<Potion> from = (Holder<Potion>) mix.cirrusGetFrom();
            if (!mixTargets.contains(from)) {
                PotionState seed = new PotionState(Items.POTION, from);
                if (visited.add(seed)) {
                    queue.add(seed);
                }
            }
        }

        while (!queue.isEmpty()) {
            PotionState state = queue.poll();
            ItemStack inputStack = PotionContents.createItemStack(state.container, state.potion);
            if (!isAllowedContainer(containers, inputStack)) {
                continue;
            }

            // 1. Potion mixes (same container, potion changes)
            for (Object rawMix : rawPotionMixes) {
                PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) rawMix;
                Holder<Potion> from = (Holder<Potion>) mix.cirrusGetFrom();
                if (!samePotion(from, state.potion)) {
                    continue;
                }
                Ingredient reagent = mix.cirrusGetIngredient();
                if (reagent.isEmpty()) {
                    continue;
                }
                Holder<Potion> to = (Holder<Potion>) mix.cirrusGetTo();
                ItemStack outputStack = PotionContents.createItemStack(state.container, to);
                ResourceLocation id = uniqueId(
                    byId,
                    VanillaWorldAccess.vanillaId(
                        "brewing",
                        containerPath(state.container),
                        potionPath(state.potion) + "_with_" + reagentPath(reagent) + "_to_" + potionPath(to)
                    )
                );
                addRecipeIfNew(
                    recipes,
                    byId,
                    dedupe,
                    new VanillaBrewingRecipe(
                        id,
                        List.of(inputStack),
                        reagent,
                        outputStack,
                        VanillaBrewingRecipe.defaultBrewingTicks,
                        true
                    )
                );
                PotionState next = new PotionState(state.container, to);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }

            // 2. Container mixes (same potion, container changes e.g. Potion -> Splash -> Lingering)
            for (Object rawMix : rawContainerMixes) {
                PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) rawMix;
                Holder<Item> from = (Holder<Item>) mix.cirrusGetFrom();
                if (from.value() != state.container) {
                    continue;
                }
                Ingredient reagent = mix.cirrusGetIngredient();
                if (reagent.isEmpty()) {
                    continue;
                }
                Holder<Item> to = (Holder<Item>) mix.cirrusGetTo();
                Item nextContainer = to.value();
                ItemStack outputStack = PotionContents.createItemStack(nextContainer, state.potion);
                ResourceLocation id = uniqueId(
                    byId,
                    VanillaWorldAccess.vanillaId(
                        "brewing",
                        containerPath(state.container) + "_to_" + containerPath(nextContainer),
                        potionPath(state.potion) + "_with_" + reagentPath(reagent)
                    )
                );
                addRecipeIfNew(
                    recipes,
                    byId,
                    dedupe,
                    new VanillaBrewingRecipe(
                        id,
                        List.of(inputStack),
                        reagent,
                        outputStack,
                        VanillaBrewingRecipe.defaultBrewingTicks,
                        true
                    )
                );
                PotionState next = new PotionState(nextContainer, state.potion);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        // 3. Modded NeoForge BrewingRecipe entries
        int moddedIndex = 0;
        for (IBrewingRecipe raw : brewing.getRecipes()) {
            if (raw instanceof BrewingRecipe modded) {
                Ingredient input = modded.getInput();
                Ingredient reagent = modded.getIngredient();
                ItemStack output = modded.getOutput();
                if (input.isEmpty() || reagent.isEmpty() || output.isEmpty()) {
                    continue;
                }
                List<ItemStack> inputStacks = Arrays.stream(input.getItems())
                        .filter(stack -> !stack.isEmpty())
                        .toList();
                if (inputStacks.isEmpty()) {
                    continue;
                }
                ResourceLocation id = uniqueId(
                    byId,
                    VanillaWorldAccess.vanillaId(
                        "brewing/modded",
                        moddedIndex + "_" + VanillaWorldAccess.sanitizePathSegment(
                            BuiltInRegistries.ITEM.getKey(output.getItem())
                        )
                    )
                );
                moddedIndex++;
                addRecipeIfNew(
                    recipes,
                    byId,
                    dedupe,
                    new VanillaBrewingRecipe(
                        id,
                        inputStacks,
                        reagent,
                        output,
                        VanillaBrewingRecipe.defaultBrewingTicks,
                        false
                    )
                );
            }
        }

        return new CachedSnapshot(brewing, version, List.copyOf(recipes), Map.copyOf(byId));
    }

    private static void addRecipeIfNew(
        List<VanillaBrewingRecipe> recipes,
        Map<ResourceLocation, VanillaBrewingRecipe> byId,
        Set<RecipeDedupeKey> dedupe,
        VanillaBrewingRecipe recipe
    ) {
        ItemStack firstInput = recipe.primaryInput();
        if (firstInput.isEmpty() || recipe.output().isEmpty()) {
            return;
        }
        ContentStack<Item, ItemStack> inputCs = ContentStack.fromStack(firstInput);
        ContentStack<Item, ItemStack> outputCs = ContentStack.fromStack(recipe.output());
        if (inputCs.isEmpty() || outputCs.isEmpty()) {
            return;
        }
        RecipeDedupeKey key = new RecipeDedupeKey(
            ComponentStackKey.of(inputCs),
            reagentPath(recipe.reagent()),
            ComponentStackKey.of(outputCs)
        );
        if (dedupe.add(key)) {
            recipes.add(recipe);
            byId.put(recipe.id(), recipe);
        }
    }

    private static ResourceLocation uniqueId(Map<ResourceLocation, VanillaBrewingRecipe> byId, ResourceLocation base) {
        if (!byId.containsKey(base)) {
            return base;
        }
        int suffix = 2;
        ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(
            base.getNamespace(),
            base.getPath() + "_" + suffix
        );
        while (byId.containsKey(candidate)) {
            suffix++;
            candidate = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "_" + suffix);
        }
        return candidate;
    }

    private static boolean isAllowedContainer(List<Ingredient> containers, ItemStack stack) {
        if (containers.isEmpty()) {
            return true;
        }
        for (Ingredient container : containers) {
            if (container.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePotion(Holder<Potion> a, Holder<Potion> b) {
        return a.equals(b) || a.value() == b.value();
    }

    private static String containerPath(Item item) {
        return VanillaWorldAccess.sanitizePathSegment(BuiltInRegistries.ITEM.getKey(item));
    }

    private static String potionPath(Holder<Potion> potion) {
        return potion.unwrapKey()
                .map(key -> VanillaWorldAccess.sanitizePathSegment(key.location()))
                .orElseGet(() -> VanillaWorldAccess.sanitizePathSegment(BuiltInRegistries.POTION.getKey(potion.value())));
    }

    private static String reagentPath(Ingredient reagent) {
        ItemStack[] items = reagent.getItems();
        if (items.length == 0) {
            return "empty";
        }
        return VanillaWorldAccess.sanitizePathSegment(BuiltInRegistries.ITEM.getKey(items[0].getItem()));
    }

    private record PotionState(Item container, Holder<Potion> potion) {}

    private record RecipeDedupeKey(ComponentStackKey input, String reagent, ComponentStackKey output) {}

    private record CachedSnapshot(
        @Nullable PotionBrewing brewing,
        long version,
        List<VanillaBrewingRecipe> recipes,
        Map<ResourceLocation, VanillaBrewingRecipe> byId
    ) {}

    @Override
    public String toString() {
        return "BrewingRecipeSystem";
    }
}
