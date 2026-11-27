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
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseSet;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.context.BooleanProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ChoiceProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.LongProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextChoice;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardResult;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolvePolicy;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveScope;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridSize;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemas;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.register.MachineEntryRegister;
import dev.vfyjxf.cirrus.api.register.ProjectionContextOptionRegister;
import dev.vfyjxf.cirrus.api.register.ProjectionPluginRegister;
import dev.vfyjxf.cirrus.api.register.RecipeClauseRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.recipe.clause.DurabilityCostClause;
import dev.vfyjxf.cirrus.recipe.clause.FluidContainerUnpackClause;
import dev.vfyjxf.cirrus.recipe.clause.GuaranteedOutputClause;
import dev.vfyjxf.cirrus.recipe.clause.ResolvePolicyClause;
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
import java.util.Comparator;
import java.util.List;

@PluginMarker
public final class DebugProjectionPlugin implements CirrusPlugin {
    public static final ProjectionPlugin plugin = ProjectionPlugin.of(
        Namespace.of(CirrusConstants.modId, "projection/debug"),
        "cirrus.ui.calculator.database.debug_plugin",
        "cirrus.ui.calculator.database.debug_plugin.desc",
        true
    );
    public static final Namespace clauseId = Namespace.of(CirrusConstants.modId, "debug/projection_probe");
    public static final Namespace systemId = Namespace.of(CirrusConstants.modId, "debug_projection");
    public static final ClauseType clauseType = new ClauseType(
        clauseId,
        plugin,
        (entry, content) -> content instanceof StackContent<?, ?> stack && stack.isItem()
    );
    public static final DebugProjectionClause clause = new DebugProjectionClause();
    public static final ExecutionContextKey<Long> scaleKey = key("scale", Long.class, 1L);
    public static final ExecutionContextKey<Boolean> extraOutputKey = key("extra_output", Boolean.class, false);
    public static final ExecutionContextKey<String> channelKey = key("channel", String.class, "input");
    public static final Namespace resolveGuardFailId = Namespace.of(CirrusConstants.modId, "debug/resolve_guard_fail");
    public static final Namespace resolveBoostInputId = Namespace
            .of(CirrusConstants.modId, "debug/resolve_boost_input");
    public static final int longChainDepth = 72;
    public static final String longChainTargetId = "debug_chain_target";
    public static final ContentTransformer<StackContent<Item, ItemStack>, DebugProjectionClause> transformer = ContentTransformer
            .of(
                plugin,
                (context, projection, content, asInput) -> debugEnabled()
                        && content instanceof StackContent<?, ?> stack
                        && stack.isItem(),
                (context, recipe, content, facet, asInput, increment, decrement) -> {
                    long scale = Math.max(1L, context.get(scaleKey));
                    String channel = context.get(channelKey);
                    if ("input".equals(channel) && asInput && scale != 1L) {
                        content.content().setAmount(Math.multiplyExact(content.amount(), scale));
                    }
                    if ("output".equals(channel) && !asInput && scale != 1L) {
                        content.content().setAmount(Math.multiplyExact(content.amount(), scale));
                    }
                    if (context.get(extraOutputKey) && asInput) {
                        increment.injectOutput(OutputContent.fromItemStack(new ItemStack(Items.LIGHTNING_ROD)));
                    }
                    return ContentTransformer.ActionResult.retain;
                }
            );
    public static final ResolveGuard failingGuard = ResolveGuard
            .of(resolveGuardFailId, (context, recipe) -> ResolveGuardResult.fail("debug_guard_failed"));
    public static final ResolveGuard passingGuard = ResolveGuard.of(
        Namespace.of(CirrusConstants.modId, "debug/resolve_guard_pass"),
        (context, recipe) -> ResolveGuardResult.pass()
    );
    public static final ResolveEffect boostFirstInput = ResolveEffect
            .of(resolveBoostInputId, 0, ResolveScope.input, (context, inputs, outputs) -> {
                if (inputs.isEmpty()) {
                    return;
                }
                InputContent first = inputs.getFirst();
                inputs.set(0, (InputContent) first.copyWithAmount(first.amount() + 3L));
            });

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
    public void registerRecipeClause(RecipeClauseRegister register) {
        if (debugEnabled()) {
            register.registerContentTransformer(clauseType, transformer);
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

    public static final class DebugProjectionClause implements RecipeClause {
        private DebugProjectionClause() {}

        @Override
        public Namespace id() {
            return clauseId;
        }

        @Override
        public ProjectionPlugin plugin() {
            return plugin;
        }
    }

    public record DebugProjectionRecipe(String id, Kind kind, int chainIndex) {
        public DebugProjectionRecipe(String id, Kind kind) {
            this(id, kind, -1);
        }

        public enum Kind {
            debugTransformer, fluidContainerUnpack, durabilityCost, guaranteedOutput, resolvePolicyEffect, resolvePolicyGuard, longChainStep, longChainTarget
        }

        public RecipeProjection toProjection() {
            return switch (kind) {
                case debugTransformer -> debugTransformerProjection();
                case fluidContainerUnpack -> fluidContainerUnpackProjection();
                case durabilityCost -> durabilityCostProjection();
                case guaranteedOutput -> guaranteedOutputProjection();
                case resolvePolicyEffect -> resolvePolicyEffectProjection();
                case resolvePolicyGuard -> resolvePolicyGuardProjection();
                case longChainStep -> longChainStepProjection();
                case longChainTarget -> longChainTargetProjection();
            };
        }

        private RecipeProjection debugTransformerProjection() {
            ClausedContent<InputContent> input = input(
                InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT, 2))
            );
            input.addClause(clause);
            ClausedContent<OutputContent> output = output(
                OutputContent.fromItemStack(new ItemStack(Items.COPPER_BLOCK))
            );
            output.addClause(clause);
            return projection(List.of(input), List.of(output));
        }

        private RecipeProjection fluidContainerUnpackProjection() {
            ClausedContent<InputContent> input = input(InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)));
            input.addClause(FluidContainerUnpackClause.instance);
            return projection(List.of(input), List.of(output(OutputContent.fromItemStack(new ItemStack(Items.STONE)))));
        }

        private RecipeProjection durabilityCostProjection() {
            ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
            tool.setDamageValue(Math.max(0, tool.getMaxDamage() - 1));
            ClausedContent<InputContent> input = input(InputContent.fromItemStack(tool));
            input.addClause(new DurabilityCostClause(1));
            return projection(
                List.of(input),
                List.of(output(OutputContent.fromItemStack(new ItemStack(Items.IRON_NUGGET))))
            );
        }

        private RecipeProjection guaranteedOutputProjection() {
            RecipeProjection projection = projection(
                List.of(input(InputContent.fromItemStack(new ItemStack(Items.STICK)))),
                List.of(
                    output(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25F)),
                    output(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE)))
                )
            );
            projection.addClause(GuaranteedOutputClause.instance);
            return projection;
        }

        private RecipeProjection resolvePolicyEffectProjection() {
            RecipeProjection projection = projection(
                List.of(input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))),
                List.of(output(OutputContent.fromItemStack(new ItemStack(Items.TORCH))))
            );
            projection.addClause(
                new ResolvePolicyClause(
                    new ResolvePolicy(
                        Lists.immutable.with(passingGuard),
                        Lists.immutable.with(boostFirstInput),
                        Comparator.comparingInt(ResolveEffect::order)
                    )
                )
            );
            return projection;
        }

        private RecipeProjection resolvePolicyGuardProjection() {
            RecipeProjection projection = projection(
                List.of(input(InputContent.fromItemStack(new ItemStack(Items.STICK, 2)))),
                List.of(output(OutputContent.fromItemStack(new ItemStack(Items.REDSTONE_TORCH))))
            );
            projection.addClause(
                new ResolvePolicyClause(
                    new ResolvePolicy(Lists.immutable.with(failingGuard), Lists.immutable.with(boostFirstInput))
                )
            );
            return projection;
        }

        private RecipeProjection longChainStepProjection() {
            if (chainIndex < 0 || chainIndex >= longChainDepth) {
                throw new IllegalStateException("Invalid debug chain step index: " + chainIndex);
            }

            ArrayList<ClausedContent<InputContent>> inputs = new ArrayList<>();
            ArrayList<ClausedContent<OutputContent>> outputs = new ArrayList<>();
            InputContent stageInput = chainIndex == 0
                    ? InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT), 1)
                    : InputContent.fromItemStack(chainStack(chainIndex), 1);
            inputs.add(chainInput(stageInput, 0));
            if (chainIndex % 3 == 0) {
                inputs.add(chainInput(InputContent.fromItemStack(new ItemStack(Items.REDSTONE), 1), 1));
            }
            if (chainIndex % 5 == 0) {
                inputs.add(
                    chainInput(
                        InputContent.fromItemStack(new ItemStack(Items.GLASS_BOTTLE), 1, RecipeContent.MIN_CHANCE),
                        2
                    )
                );
            }
            if (chainIndex % 8 == 0) {
                inputs.add(chainInput(InputContent.fromFluidStack(new FluidStack(Fluids.WATER, 250)), 3));
            }

            ClausedContent<OutputContent> primary = chainOutput(
                OutputContent.fromItemStack(chainStack(chainIndex + 1), 1),
                0
            );
            if (chainIndex % 4 == 0) {
                primary.addClause(clause);
            }
            outputs.add(primary);
            if (chainIndex % 6 == 0) {
                outputs.add(chainOutput(OutputContent.fromEnergy(128L * (chainIndex + 1)), 1));
            }
            if (chainIndex % 7 == 0) {
                outputs.add(chainOutput(OutputContent.fromTimeTick(20 + chainIndex), 2));
            }
            if (chainIndex % 11 == 0) {
                outputs.add(chainOutput(OutputContent.fromItemStack(new ItemStack(Items.IRON_NUGGET), 1, 0.35F), 3));
            }

            RecipeProjection projection = projection(
                inputs,
                outputs,
                ProjectionSchema.empty().with(ProjectionSchemas.gridSize, new GridSize(3, 3))
            );
            if (chainIndex % 11 == 0 || chainIndex % 13 == 0) {
                projection.addClause(GuaranteedOutputClause.instance);
            }
            return projection;
        }

        private RecipeProjection longChainTargetProjection() {
            ArrayList<ClausedContent<InputContent>> inputs = new ArrayList<>();
            inputs.add(chainInput(InputContent.fromItemStack(chainStack(longChainDepth), 1), 0));
            inputs.add(chainInput(InputContent.fromItemStack(new ItemStack(Items.DIAMOND), 2), 1));
            inputs.add(
                chainInput(InputContent.fromItemStack(new ItemStack(Items.BLAZE_ROD), 1, RecipeContent.MIN_CHANCE), 2)
            );

            ArrayList<ClausedContent<OutputContent>> outputs = new ArrayList<>();
            outputs.add(
                chainOutput(OutputContent.fromItemStack(namedStack(Items.NETHER_STAR, "Debug chain result"), 1), 0)
            );
            outputs.add(chainOutput(OutputContent.fromEnergy(64_000L), 1));
            outputs.add(chainOutput(OutputContent.fromTimeTick(240), 2));

            RecipeProjection projection = projection(
                inputs,
                outputs,
                ProjectionSchema.empty().with(ProjectionSchemas.gridSize, new GridSize(3, 3))
            );
            projection.addClause(
                new ResolvePolicyClause(
                    new ResolvePolicy(
                        Lists.immutable.with(passingGuard),
                        Lists.immutable.with(boostFirstInput),
                        Comparator.comparingInt(ResolveEffect::order)
                    )
                )
            );
            return projection;
        }

        private RecipeProjection projection(
            List<ClausedContent<InputContent>> inputs,
            List<ClausedContent<OutputContent>> outputs
        ) {
            return projection(inputs, outputs, ProjectionSchema.empty());
        }

        private RecipeProjection projection(
            List<ClausedContent<InputContent>> inputs,
            List<ClausedContent<OutputContent>> outputs,
            ProjectionSchema schema
        ) {
            return new RecipeProjection(
                RecipeEntry.of(DebugProjectionRecipeSystem.instance, this),
                Lists.immutable.withAll(inputs),
                Lists.immutable.withAll(outputs),
                schema,
                ClauseSet.create()
            );
        }

        private static ClausedContent<InputContent> input(InputContent content) {
            return ClausedContent.from(content);
        }

        private static ClausedContent<OutputContent> output(OutputContent content) {
            return ClausedContent.from(content);
        }

        private static ClausedContent<InputContent> chainInput(InputContent content, int slot) {
            return ClausedContent.from(
                content,
                ProjectionSchema.empty().with(ProjectionSchemas.gridIndex, new GridIndex(slot)),
                ClauseSet.create()
            );
        }

        private static ClausedContent<OutputContent> chainOutput(OutputContent content, int slot) {
            return ClausedContent.from(
                content,
                ProjectionSchema.empty().with(ProjectionSchemas.gridIndex, new GridIndex(slot)),
                ClauseSet.create()
            );
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
