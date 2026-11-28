package dev.vfyjxf.cirrus.internal;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.CirrusTypes.IngredientResolvers;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.api.register.*;
import dev.vfyjxf.cirrus.content.resolver.fluid.VanillaFluidHandlerResolver;
import dev.vfyjxf.cirrus.content.type.variant.*;
import dev.vfyjxf.cirrus.recipe.projection.BuiltinProjectionEffects;
import dev.vfyjxf.cirrus.recipe.content.ComplexRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.EmptyRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.StackListRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.StackRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.TagStackRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.TypedStackListRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.ValueRecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.resolver.item.DataComponentIngredientResolver;
import dev.vfyjxf.cirrus.recipe.content.resolver.item.DifferenceIngredientResolver;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableServerExecutionDriver;
import dev.vfyjxf.cirrus.recipe.storage.source.PlayerInventorySource;
import dev.vfyjxf.cirrus.recipe.system.AnvilRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.BrewingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CompostingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CookingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.FuelRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.GrindstoneRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.SmithingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.StonecuttingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.WorldInteractionRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiRecipeLayoutSubsystem;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@PluginMarker
public class InternalPlugin implements CirrusPlugin, ModPlugin {

    @Override
    public Namespace pluginId() {
        return builtin;
    }

    @Override
    public void registerContentType(ContentTypeRegister register) {
        register.registerContentType(CirrusTypes.Contents.item);
        register.registerContentType(CirrusTypes.Contents.fluidConst);

        register.registerTagType(CirrusTypes.Contents.item, CirrusTypes.Contents.itemTag);
        register.registerTagType(CirrusTypes.Contents.fluidConst, CirrusTypes.Contents.fluidTag);

        register.registerStackType(CirrusTypes.Contents.item, CirrusTypes.Contents.itemStack);
        register.registerStackType(CirrusTypes.Contents.fluidConst, CirrusTypes.Contents.fluidStack);
    }

    @Override
    public void registerContentVariant(ContentVariantRegister register) {
        //region type
        register.registerItemVariantType(EnchantedBookVariantType.instance);
        register.registerItemVariantType(FireworkRocketVariantType.instance);
        register.registerItemVariantType(InstrumentVariantType.instance);
        register.registerItemVariantType(OminousBottleVariantType.instance);
        register.registerItemVariantType(PaintingVariantType.instance);
        register.registerItemVariantType(PotionVariantType.instance);
        register.registerItemVariantType(SuspiciousStewVariantType.instance);
        //endregion

        //region content variant

        register.registerVariantForContent(Items.ENCHANTED_BOOK, EnchantedBookVariantType.instance);
        register.registerVariantForContent(Items.FIREWORK_ROCKET, FireworkRocketVariantType.instance);
        //endregion
    }

    @Override
    public void registerContentResolver(ContentResolverRegister register) {
        //region content
        //endregion

        //region stack

        register.registerStackResolver(
            CirrusTypes.ContentResolvers.fluidContainer,
            CirrusTypes.Contents.itemStack,
            VanillaFluidHandlerResolver.instance
        );

        //endregion
    }

    @Override
    public void registerJeiTypeBridge(JeiTypeBridgeRegister register) {
        register.registerStackTypeBridge(CirrusTypes.Contents.itemStack.typeId(), VanillaTypes.ITEM_STACK);
        register.registerStackTypeBridge(CirrusTypes.Contents.fluidStack.typeId(), NeoForgeTypes.FLUID_STACK);
    }

    @Override
    public void registerRecipeSystem(RecipeSystemRegister register) {
        CraftingRecipeSystem crafting = new CraftingRecipeSystem();
        var smelting = CookingRecipeSystem.smelting();
        var blasting = CookingRecipeSystem.blasting();
        var smoking = CookingRecipeSystem.smoking();
        var campfire = CookingRecipeSystem.campfireCooking();
        StonecuttingRecipeSystem stonecutting = new StonecuttingRecipeSystem();
        SmithingRecipeSystem smithing = new SmithingRecipeSystem();
        BrewingRecipeSystem brewing = new BrewingRecipeSystem();
        CompostingRecipeSystem composting = new CompostingRecipeSystem();
        FuelRecipeSystem fueling = new FuelRecipeSystem();
        AnvilRecipeSystem anvil = new AnvilRecipeSystem();
        GrindstoneRecipeSystem grindstone = new GrindstoneRecipeSystem();
        WorldInteractionRecipeSystem worldInteraction = new WorldInteractionRecipeSystem();

        register.registerSystem(crafting);
        register.registerSystem(smelting);
        register.registerSystem(blasting);
        register.registerSystem(smoking);
        register.registerSystem(campfire);
        register.registerSystem(stonecutting);
        register.registerSystem(smithing);
        register.registerSystem(brewing);
        register.registerSystem(composting);
        register.registerSystem(fueling);
        register.registerSystem(anvil);
        register.registerSystem(grindstone);
        register.registerSystem(worldInteraction);

        registerJeiFallback(register, crafting, RecipeTypes.CRAFTING);
        registerJeiFallback(register, smelting, RecipeTypes.SMELTING);
        registerJeiFallback(register, blasting, RecipeTypes.BLASTING);
        registerJeiFallback(register, smoking, RecipeTypes.SMOKING);
        registerJeiFallback(register, campfire, RecipeTypes.CAMPFIRE_COOKING);
        registerJeiFallback(register, stonecutting, RecipeTypes.STONECUTTING);
        registerJeiFallback(register, smithing, RecipeTypes.SMITHING);
    }

    private static <T extends Recipe<?>> void registerJeiFallback(
        RecipeSystemRegister register,
        RecipeSystem<RecipeHolder<T>> system,
        RecipeType<RecipeHolder<T>> recipeType
    ) {
        register.registerSubsystem(
            system.systemId(),
            JeiRecipeLayoutSubsystem.id,
            new JeiRecipeLayoutSubsystem<>(system, recipeType)
        );
    }

    @Override
    public void registerRecipeSystemTag(RecipeSystemTagRegister register) {
        register.registerSystemTagEntry(RecipeSystemTags.crafting, CraftingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.cooking, CookingRecipeSystem.smeltingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.cooking, CookingRecipeSystem.blastingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.cooking, CookingRecipeSystem.smokingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.cooking, CookingRecipeSystem.campfireCookingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.cutting, StonecuttingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.smithing, SmithingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.brewing, BrewingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.composting, CompostingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.fueling, FuelRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.anvil, AnvilRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.grindstone, GrindstoneRecipeSystem.systemId);

        register.registerSystemTagEntry(RecipeSystemTags.vanilla, CraftingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, CookingRecipeSystem.smeltingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, CookingRecipeSystem.blastingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, CookingRecipeSystem.smokingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, CookingRecipeSystem.campfireCookingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, StonecuttingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, SmithingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, BrewingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, CompostingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, FuelRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, AnvilRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, GrindstoneRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.vanilla, WorldInteractionRecipeSystem.systemId);

        register.registerSystemTagEntry(RecipeSystemTags.displayOnly, FuelRecipeSystem.systemId);

        register.registerSystemTagEntry(RecipeSystemTags.handOperable, CraftingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.handOperable, CookingRecipeSystem.campfireCookingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.handOperable, StonecuttingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.handOperable, SmithingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.handOperable, AnvilRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.handOperable, GrindstoneRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.handOperable, WorldInteractionRecipeSystem.systemId);

        register.registerSystemTagEntry(RecipeSystemTags.worldInteraction, WorldInteractionRecipeSystem.systemId);

        register.registerSystemTagEntry(RecipeSystemTags.automatable, CookingRecipeSystem.smeltingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.automatable, CookingRecipeSystem.blastingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.automatable, CookingRecipeSystem.smokingSystemId);
        register.registerSystemTagEntry(RecipeSystemTags.automatable, BrewingRecipeSystem.systemId);
        register.registerSystemTagEntry(RecipeSystemTags.automatable, CompostingRecipeSystem.systemId);
    }

    @Override
    public void registerProjectionPlugin(ProjectionPluginRegister register) {
        register.registerProjectionPlugin(ProjectionPlugin.builtin);
    }

    @Override
    public void registerProjectionEffect(ProjectionEffectRegister register) {
        register.registerProjectionEffect(BuiltinProjectionEffects.fluidContainerUnpack);
        register.registerProjectionEffect(BuiltinProjectionEffects.durabilityCost);
        register.registerProjectionEffect(BuiltinProjectionEffects.guaranteedOutput);
    }

    @Override
    public void registerRecipeContentRenderer(RecipeContentRendererRegister register) {
        register.registerRenderer(RecipeContentType.empty, EmptyRecipeContentRenderer.instance);
        register.registerRenderer(RecipeContentType.STACK, StackRecipeContentRenderer.instance);
        register.registerRenderer(RecipeContentType.value, ValueRecipeContentRenderer.instance);
        register.registerRenderer(RecipeContentType.TAG_STACK, TagStackRecipeContentRenderer.instance);
        register.registerRenderer(RecipeContentType.STACK_LIST, StackListRecipeContentRenderer.instance);
        register.registerRenderer(RecipeContentType.TYPED_STACK_LIST, TypedStackListRecipeContentRenderer.instance);
        register.registerRenderer(RecipeContentType.COMPLEX, ComplexRecipeContentRenderer.instance);
    }

    @Override
    public void registerJeiRecipeTypeBridge(JeiRecipeTypeBridgeRegister register) {
        register.registerRecipeTypeForSystem(CraftingRecipeSystem.systemId, RecipeTypes.CRAFTING);
        register.registerRecipeTypeForSystem(CookingRecipeSystem.smeltingSystemId, RecipeTypes.SMELTING);
        register.registerRecipeTypeForSystem(CookingRecipeSystem.blastingSystemId, RecipeTypes.BLASTING);
        register.registerRecipeTypeForSystem(CookingRecipeSystem.smokingSystemId, RecipeTypes.SMOKING);
        register.registerRecipeTypeForSystem(CookingRecipeSystem.campfireCookingSystemId, RecipeTypes.CAMPFIRE_COOKING);
        register.registerRecipeTypeForSystem(StonecuttingRecipeSystem.systemId, RecipeTypes.STONECUTTING);
        register.registerRecipeTypeForSystem(SmithingRecipeSystem.systemId, RecipeTypes.SMITHING);
        register.registerRecipeTypeForSystem(BrewingRecipeSystem.systemId, RecipeTypes.BREWING);
        register.registerRecipeTypeForSystem(CompostingRecipeSystem.systemId, RecipeTypes.COMPOSTING);
        register.registerRecipeTypeForSystem(FuelRecipeSystem.systemId, RecipeTypes.FUELING);
        register.registerRecipeTypeForSystem(AnvilRecipeSystem.systemId, RecipeTypes.ANVIL);
        register.registerRecipeTypeForSystem(GrindstoneRecipeSystem.systemId, RecipeTypes.GRINDSTONE);
    }

    @Override
    public void registerMachineEntry(MachineEntryRegister register) {
        register.registerMachineForSystem(
            CraftingRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.CRAFTING_TABLE))
        );
        register.registerMachineForSystem(
            CookingRecipeSystem.smeltingSystemId,
            MachineEntry.fromItemStack(new ItemStack(Items.FURNACE))
        );
        register.registerMachineForSystem(
            CookingRecipeSystem.blastingSystemId,
            MachineEntry.fromItemStack(new ItemStack(Items.BLAST_FURNACE))
        );
        register.registerMachineForSystem(
            CookingRecipeSystem.smokingSystemId,
            MachineEntry.fromItemStack(new ItemStack(Items.SMOKER))
        );
        register.registerMachineForSystem(
            CookingRecipeSystem.campfireCookingSystemId,
            MachineEntry.fromItemStack(new ItemStack(Items.CAMPFIRE))
        );
        register.registerMachineForSystem(
            CookingRecipeSystem.campfireCookingSystemId,
            MachineEntry.fromItemStack(new ItemStack(Items.SOUL_CAMPFIRE))
        );
        register.registerMachineForSystem(
            StonecuttingRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.STONECUTTER))
        );
        register.registerMachineForSystem(
            SmithingRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.SMITHING_TABLE))
        );
        register.registerMachineForSystem(
            BrewingRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.BREWING_STAND))
        );
        register.registerMachineForSystem(
            CompostingRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.COMPOSTER))
        );
        register.registerMachineForSystem(
            FuelRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.FURNACE))
        );
        register.registerMachineForSystem(
            FuelRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.BLAST_FURNACE))
        );
        register.registerMachineForSystem(
            FuelRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.SMOKER))
        );
        register.registerMachineForSystem(
            AnvilRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.ANVIL))
        );
        register.registerMachineForSystem(
            AnvilRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.CHIPPED_ANVIL))
        );
        register.registerMachineForSystem(
            AnvilRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.DAMAGED_ANVIL))
        );
        register.registerMachineForSystem(
            GrindstoneRecipeSystem.systemId,
            MachineEntry.fromItemStack(new ItemStack(Items.GRINDSTONE))
        );
    }

    @Override
    public void registerIngredientResolver(IngredientResolverRegister register) {
        register.registerIngredientResolver(IngredientResolvers.custom, DataComponentIngredientResolver.instance);
        register.registerIngredientResolver(IngredientResolvers.custom, DifferenceIngredientResolver.instance);
    }

    @Override
    public void registerStorageType(StorageTypeRegister register) {
        register.registerStorageType(PlayerInventorySource.type);
    }

    @Override
    public void registerExecutionDriver(ExecutionDriverRegister register) {
        register.registerServer(
            CraftingTableServerExecutionDriver.selector,
            CraftingTableServerExecutionDriver::create
        );
    }
}
