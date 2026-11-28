package dev.vfyjxf.cirrus.internal.debug;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributes;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionTags;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.TagQuery;
import dev.vfyjxf.cirrus.api.recipe.projection.context.BooleanProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ChoiceProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.LongProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextChoice;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.register.MachineEntryRegister;
import dev.vfyjxf.cirrus.api.register.ProjectionContextOptionRegister;
import dev.vfyjxf.cirrus.api.register.ProjectionEffectRegister;
import dev.vfyjxf.cirrus.api.register.ProjectionPluginRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.recipe.system.BasicRecipeSystem;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@PluginMarker
public final class DebugProjectionPlugin implements CirrusPlugin {
    public static final ProjectionPlugin plugin = ProjectionPlugin.of(
        Namespace.of(CirrusConstants.modId, "projection/debug"),
        "cirrus.ui.calculator.database.debug_plugin",
        "cirrus.ui.calculator.database.debug_plugin.desc",
        true
    );
    public static final Namespace effectId = Namespace.of(CirrusConstants.modId, "debug/projection_probe");
    public static final Namespace probeTag = Namespace.of(CirrusConstants.modId, "debug/tag/projection_probe");
    public static final Namespace systemId = Namespace.of(CirrusConstants.modId, "debug_projection");
    public static final ExecutionContextKey<Long> scaleKey = key("scale", Long.class, 1L);
    public static final ExecutionContextKey<Boolean> extraOutputKey = key("extra_output", Boolean.class, false);
    public static final ExecutionContextKey<String> channelKey = key("channel", String.class, "input");
    public static final Namespace resolveGuardFailId = Namespace.of(CirrusConstants.modId, "debug/resolve_guard_fail");
    public static final Namespace resolveGuardPassId = Namespace.of(CirrusConstants.modId, "debug/resolve_guard_pass");
    public static final Namespace resolveBoostInputId = Namespace
            .of(CirrusConstants.modId, "debug/resolve_boost_input");
    public static final int longChainDepth = 72;
    public static final String longChainTargetId = "debug_chain_target";

    public static final ProjectionEffect debugEffect = ProjectionEffect.builder(effectId)
            .plugin(plugin)
            .stage(ProjectionStage.transform)
            .watches(scaleKey, extraOutputKey, channelKey)
            .when((context, view) -> debugEnabled())
            .forEachSlot(null, TagQuery.requireAll(probeTag), (tx, slot) -> {
                if (!(slot.content() instanceof StackContent<?, ?> stack) || !stack.isItem()) {
                    return;
                }
                long scale = Math.max(1L, tx.context().get(scaleKey));
                String channel = tx.context().get(channelKey);
                boolean asInput = slot.channel() == SlotChannel.input;
                if ("input".equals(channel) && asInput && scale != 1L) {
                    tx.setSlotAmount(slot.address(), Math.multiplyExact(slot.content().amount(), scale));
                }
                if ("output".equals(channel) && !asInput && scale != 1L) {
                    tx.setSlotAmount(slot.address(), Math.multiplyExact(slot.content().amount(), scale));
                }
                if (tx.context().get(extraOutputKey) && asInput) {
                    tx.addOutput(OutputContent.fromItemStack(new ItemStack(Items.LIGHTNING_ROD)));
                }
            })
            .build();

    public static final ProjectionGuard failingGuard = ProjectionGuard
            .of(resolveGuardFailId, (context, view) -> ProjectionGuard.Result.fail("debug_guard_failed"));
    public static final ProjectionGuard passingGuard = ProjectionGuard
            .of(resolveGuardPassId, (context, view) -> ProjectionGuard.Result.pass());

    public static final ProjectionEffect boostFirstInput = ProjectionEffect.builder(resolveBoostInputId)
            .plugin(ProjectionPlugin.builtin)
            .stage(ProjectionStage.transform)
            .guard(passingGuard)
            .apply(tx -> {
                var inputs = tx.inputSlots();
                if (inputs.isEmpty()) {
                    return;
                }
                var first = inputs.getFirst();
                tx.setSlotAmount(first.address(), first.content().amount() + 3L);
            })
            .build();

    public static final ProjectionEffect blockedBoostFirstInput = ProjectionEffect.builder(resolveBoostInputId)
            .plugin(ProjectionPlugin.builtin)
            .stage(ProjectionStage.transform)
            .guard(failingGuard)
            .apply(tx -> {
                var inputs = tx.inputSlots();
                if (inputs.isEmpty()) {
                    return;
                }
                var first = inputs.getFirst();
                tx.setSlotAmount(first.address(), first.content().amount() + 3L);
            })
            .build();

    @Override
    public Namespace pluginId() {
        return Namespace.of(CirrusConstants.modId, "debug_projection");
    }

    @Override
    public void registerRecipeSystem(RecipeSystemRegister register) {
        if (debugEnabled()) {
            register.registerSystem(DebugProjectionRecipeSystem.instance);
        }
    }

    @Override
    public void registerMachineEntry(MachineEntryRegister register) {
        if (!debugEnabled()) {
            return;
        }
        register.registerMachineForSystem(
            systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.CRAFTING_TABLE)),
            MachineEntry.fromItemStack(new ItemStack(Items.FURNACE)),
            MachineEntry.fromItemStack(new ItemStack(Items.BREWING_STAND))
        );
    }

    @Override
    public void registerProjectionPlugin(ProjectionPluginRegister register) {
        if (debugEnabled()) {
            register.registerProjectionPlugin(plugin);
        }
    }

    @Override
    public void registerProjectionContextOption(ProjectionContextOptionRegister register) {
        if (!debugEnabled()) {
            return;
        }
        register.registerProjectionContextOption(
            new LongProjectionContextOption(
                Namespace.of(CirrusConstants.modId, "debug/projection_scale"),
                plugin,
                scaleKey,
                "cirrus.ui.calculator.database.debug_plugin.scale",
                "cirrus.ui.calculator.database.debug_plugin.scale.desc",
                1L,
                8L,
                1L
            )
        );
        register.registerProjectionContextOption(
            new BooleanProjectionContextOption(
                Namespace.of(CirrusConstants.modId, "debug/projection_extra_output"),
                plugin,
                extraOutputKey,
                "cirrus.ui.calculator.database.debug_plugin.extra_output",
                "cirrus.ui.calculator.database.debug_plugin.extra_output.desc"
            )
        );
        register.registerProjectionContextOption(
            new ChoiceProjectionContextOption<>(
                Namespace.of(CirrusConstants.modId, "debug/projection_channel"),
                plugin,
                channelKey,
                "cirrus.ui.calculator.database.debug_plugin.channel",
                "cirrus.ui.calculator.database.debug_plugin.channel.desc",
                List.of(
                    new ProjectionContextChoice<>("input", "cirrus.ui.calculator.database.debug_plugin.channel.input"),
                    new ProjectionContextChoice<>("output", "cirrus.ui.calculator.database.debug_plugin.channel.output")
                )
            )
        );
    }

    @Override
    public void registerProjectionEffect(ProjectionEffectRegister register) {
        if (debugEnabled()) {
            register.registerProjectionEffect(debugEffect);
        }
    }

    public static boolean debugEnabled() {
        return !FMLEnvironment.production;
    }

    private static <T> ExecutionContextKey<T> key(String path, Class<T> type, T defaultValue) {
        return ExecutionContextKey.create(
            ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "debug/projection/" + path),
            type,
            defaultValue
        );
    }

    public record DebugProjectionRecipe(String id, Kind kind, int chainIndex) {
        public DebugProjectionRecipe(String id, Kind kind) {
            this(id, kind, -1);
        }

        public enum Kind {
            debugTransformer,
            fluidContainerUnpack,
            durabilityCost,
            guaranteedOutput,
            resolvePolicyEffect,
            resolvePolicyGuard,
            craftingGridTransform,
            longChainStep,
            longChainTarget
        }

        public RecipeProjection toProjection() {
            return switch (kind) {
                case debugTransformer -> debugTransformerProjection();
                case fluidContainerUnpack -> fluidContainerUnpackProjection();
                case durabilityCost -> durabilityCostProjection();
                case guaranteedOutput -> guaranteedOutputProjection();
                case resolvePolicyEffect -> resolvePolicyEffectProjection();
                case resolvePolicyGuard -> resolvePolicyGuardProjection();
                case craftingGridTransform -> craftingGridTransformProjection();
                case longChainStep -> longChainStepProjection();
                case longChainTarget -> longChainTargetProjection();
            };
        }

        private RecipeProjection debugTransformerProjection() {
            return builder()
                    .input(
                        InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT, 2)),
                        ProjectionAttributes.empty(),
                        ProjectionTags.of(probeTag)
                    )
                    .output(
                        OutputContent.fromItemStack(new ItemStack(Items.COPPER_BLOCK)),
                        ProjectionAttributes.empty(),
                        ProjectionTags.of(probeTag)
                    )
                    .build();
        }

        private RecipeProjection fluidContainerUnpackProjection() {
            return builder()
                    .input(
                        InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)),
                        ProjectionAttributes.empty(),
                        ProjectionTags.of(ProjectionKeys.Tags.fluidContainer)
                    )
                    .output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                    .build();
        }

        private RecipeProjection durabilityCostProjection() {
            ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
            tool.setDamageValue(Math.max(0, tool.getMaxDamage() - 1));
            return builder()
                    .input(
                        InputContent.fromItemStack(tool),
                        ProjectionAttributes.of(ProjectionKeys.durabilityCost, 1),
                        ProjectionTags.of(ProjectionKeys.Tags.durabilityTool)
                    )
                    .output(OutputContent.fromItemStack(new ItemStack(Items.IRON_NUGGET)))
                    .build();
        }

        private RecipeProjection guaranteedOutputProjection() {
            return builder()
                    .input(InputContent.fromItemStack(new ItemStack(Items.STICK)))
                    .output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F))
                    .output(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE)))
                    .tag(ProjectionKeys.Tags.guaranteedOutput)
                    .build();
        }

        private RecipeProjection resolvePolicyEffectProjection() {
            return builder()
                    .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))
                    .output(OutputContent.fromItemStack(new ItemStack(Items.TORCH)))
                    .localEffect(boostFirstInput)
                    .build();
        }

        private RecipeProjection resolvePolicyGuardProjection() {
            return builder()
                    .input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))
                    .output(OutputContent.fromItemStack(new ItemStack(Items.REDSTONE_TORCH)))
                    .localEffect(blockedBoostFirstInput)
                    .build();
        }

        private RecipeProjection craftingGridTransformProjection() {
            ItemStack breakingTool = new ItemStack(Items.IRON_PICKAXE);
            breakingTool.setDamageValue(Math.max(0, breakingTool.getMaxDamage() - 1));
            return builder()
                    .inputGrid(3, 3)
                    .inputCell(
                        0,
                        0,
                        InputContent.fromItemStack(breakingTool),
                        ProjectionAttributes.of(ProjectionKeys.durabilityCost, 1),
                        ProjectionTags.of(ProjectionKeys.Tags.durabilityTool)
                    )
                    .inputCell(
                        1,
                        1,
                        InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)),
                        ProjectionAttributes.empty(),
                        ProjectionTags.of(ProjectionKeys.Tags.fluidContainer)
                    )
                    .inputCell(
                        2,
                        1,
                        InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT, 2)),
                        ProjectionAttributes.empty(),
                        ProjectionTags.of(probeTag)
                    )
                    .inputCell(1, 2, InputContent.fromItemStack(new ItemStack(Items.STICK)))
                    .output(OutputContent.fromItemStack(new ItemStack(Items.COPPER_BLOCK)))
                    .output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F))
                    .tag(ProjectionKeys.Tags.guaranteedOutput)
                    .build();
        }

        private RecipeProjection longChainStepProjection() {
            if (chainIndex < 0 || chainIndex >= longChainDepth) {
                throw new IllegalStateException("Invalid debug chain step index: " + chainIndex);
            }

            RecipeProjection.Builder b = builder().inputGrid(3, 3).outputGrid(3, 3);
            InputContent stageInput = chainIndex == 0
                    ? InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT), 1)
                    : InputContent.fromItemStack(chainStack(chainIndex), 1);
            b.inputCellIndex(0, stageInput);
            if (chainIndex % 3 == 0) {
                b.inputCellIndex(1, InputContent.fromItemStack(new ItemStack(Items.REDSTONE), 1));
            }
            if (chainIndex % 5 == 0) {
                b.inputCellIndex(
                    2,
                    InputContent.fromItemStack(new ItemStack(Items.GLASS_BOTTLE), 1, RecipeContent.MIN_CHANCE)
                );
            }
            if (chainIndex % 8 == 0) {
                b.inputCellIndex(3, InputContent.fromFluidStack(new FluidStack(Fluids.WATER, 250)));
            }

            ProjectionTags primaryTags = chainIndex % 4 == 0 ? ProjectionTags.of(probeTag) : ProjectionTags.empty();
            b.outputCell(0, 0, OutputContent.fromItemStack(chainStack(chainIndex + 1), 1), ProjectionAttributes.empty(), primaryTags);
            if (chainIndex % 6 == 0) {
                b.outputCell(1, 0, OutputContent.fromEnergy(128L * (chainIndex + 1)));
            }
            if (chainIndex % 7 == 0) {
                b.outputCell(2, 0, OutputContent.fromTimeTick(20 + chainIndex));
            }
            if (chainIndex % 11 == 0) {
                b.outputCell(0, 1, OutputContent.fromItemStack(new ItemStack(Items.IRON_NUGGET), 1, 0.35F));
            }
            if (chainIndex % 11 == 0 || chainIndex % 13 == 0) {
                b.tag(ProjectionKeys.Tags.guaranteedOutput);
            }
            return b.build();
        }

        private RecipeProjection longChainTargetProjection() {
            return builder()
                    .inputGrid(3, 3)
                    .outputGrid(3, 3)
                    .inputCellIndex(0, InputContent.fromItemStack(chainStack(longChainDepth), 1))
                    .inputCellIndex(1, InputContent.fromItemStack(new ItemStack(Items.DIAMOND), 2))
                    .inputCellIndex(
                        2,
                        InputContent.fromItemStack(new ItemStack(Items.BLAZE_ROD), 1, RecipeContent.MIN_CHANCE)
                    )
                    .outputCell(0, 0, OutputContent.fromItemStack(namedStack(Items.NETHER_STAR, "Debug chain result"), 1))
                    .outputCell(1, 0, OutputContent.fromEnergy(64_000L))
                    .outputCell(2, 0, OutputContent.fromTimeTick(240))
                    .localEffect(boostFirstInput)
                    .build();
        }

        private RecipeProjection.Builder builder() {
            return RecipeProjection.builder(RecipeEntry.of(DebugProjectionRecipeSystem.instance, this));
        }

        private static ItemStack chainStack(int index) {
            return namedStack(Items.PRISMARINE_SHARD, "Debug chain stage " + index);
        }

        private static ItemStack namedStack(Item item, String name) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            return stack;
        }
    }

    public static final class DebugProjectionRecipeSystem extends BasicRecipeSystem<DebugProjectionRecipe> {
        static final DebugProjectionRecipeSystem instance = new DebugProjectionRecipeSystem();
        private static final List<DebugProjectionRecipe> recipes = createRecipes();

        private DebugProjectionRecipeSystem() {
            super(systemId);
        }

        private static List<DebugProjectionRecipe> createRecipes() {
            ArrayList<DebugProjectionRecipe> out = new ArrayList<>();
            out.add(new DebugProjectionRecipe("projection_probe", DebugProjectionRecipe.Kind.debugTransformer));
            out.add(
                new DebugProjectionRecipe("fluid_container_unpack", DebugProjectionRecipe.Kind.fluidContainerUnpack)
            );
            out.add(new DebugProjectionRecipe("durability_cost", DebugProjectionRecipe.Kind.durabilityCost));
            out.add(new DebugProjectionRecipe("guaranteed_output", DebugProjectionRecipe.Kind.guaranteedOutput));
            out.add(new DebugProjectionRecipe("resolve_policy_effect", DebugProjectionRecipe.Kind.resolvePolicyEffect));
            out.add(new DebugProjectionRecipe("resolve_policy_guard", DebugProjectionRecipe.Kind.resolvePolicyGuard));
            out.add(
                new DebugProjectionRecipe("crafting_grid_transform", DebugProjectionRecipe.Kind.craftingGridTransform)
            );
            for (int i = 0; i < longChainDepth; i++) {
                out.add(
                    new DebugProjectionRecipe("debug_chain_step_" + i, DebugProjectionRecipe.Kind.longChainStep, i)
                );
            }
            out.add(new DebugProjectionRecipe(longChainTargetId, DebugProjectionRecipe.Kind.longChainTarget));
            return List.copyOf(out);
        }

        @Override
        public Class<?> basicType() {
            return DebugProjectionRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(DebugProjectionRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, recipe.id());
        }

        @Override
        public @Nullable DebugProjectionRecipe findRecipe(String uniqueId) {
            for (DebugProjectionRecipe recipe : recipes) {
                ResourceLocation id = getId(recipe);
                if (recipe.id().equals(uniqueId) || (id != null && id.toString().equals(uniqueId))) {
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public @Nullable DebugProjectionRecipe findRecipe(ResourceLocation registryName) {
            for (DebugProjectionRecipe recipe : recipes) {
                if (registryName.equals(getId(recipe))) {
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public int hash(DebugProjectionRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(DebugProjectionRecipe first, DebugProjectionRecipe second) {
            return first.id().equals(second.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, DebugProjectionRecipe recipe) {
            return ResolveResult.resolved(recipe.toProjection());
        }

        @Override
        public @Unmodifiable RichIterable<DebugProjectionRecipe> allRecipes() {
            return Lists.immutable.withAll(recipes);
        }

        @Override
        public MachineEntry conventionMachine() {
            return MachineEntry.fromItemStack(new ItemStack(Items.CRAFTING_TABLE));
        }
    }
}
