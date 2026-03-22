package dev.vfyjxf.cirrus.api;

import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyContentType;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValueType;
import dev.vfyjxf.cirrus.content.type.FluidContentType;
import dev.vfyjxf.cirrus.content.type.FluidStackType;
import dev.vfyjxf.cirrus.content.type.ItemContentType;
import dev.vfyjxf.cirrus.content.type.ItemStackType;
import dev.vfyjxf.cirrus.recipe.clause.DurabilityCostClause;
import dev.vfyjxf.cirrus.recipe.clause.FluidContainerUnpackClause;
import dev.vfyjxf.cirrus.recipe.clause.GuaranteedOutputClause;
import dev.vfyjxf.cirrus.recipe.clause.ResolvePolicyClause;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

/**
 * All builtin {@link ContentType} & {@link StackType} & {@link TagType} & {@link ClauseType} & {@link ContentResolverType} & {@link IngredientResolverType}
 * For the purpose of reducing ambiguity, members here use uppercase names
 */
public final class CirrusTypes {

    //NOTE:这里用大写是因为小写歧义太多，用大写去歧义
    public final static class Contents {

        //region content
        public static final ContentType<Item> ITEM = ItemContentType.instance;
        public static final ContentType<Fluid> FLUID = FluidContentType.instance;
        //endregion

        //region stack
        public static final StackType<Item, ItemStack> ITEM_STACK = ItemStackType.instance;
        public static final StackType<Fluid, FluidStack> FLUID_STACK = FluidStackType.instance;
        //endregion

        //region tag
        public static final TagType<Item> ITEM_TAG = TagType.fromRegistry(Namespace.ofMc("tag/item"), ITEM, Registries.ITEM);
        public static final TagType<Fluid> FLUID_TAG = TagType.fromRegistry(Namespace.ofMc("tag/fluid"), FLUID, Registries.FLUID);
        //endregion

        //region recipe content
        public static final ValueType<TimeValue> TIME = TimeValueType.instance;
        public static final ValueType<EnergyValue> ENERGY = EnergyContentType.instance;
        //endregion

        private Contents() {throw new AssertionError("This class should not be instantiated!");}
    }

    public final static class Clauses {

        public static final ClauseType fluidContainerUnpack = FluidContainerUnpackClause.type;

        public static final ClauseType durabilityCost = DurabilityCostClause.type;

        public static final ClauseType guaranteedOutput = GuaranteedOutputClause.type;

        public static final ClauseType resolvePolicy = ResolvePolicyClause.type;

        private Clauses() {throw new AssertionError("This class should not be instantiated!");}
    }

    public final static class ContentResolvers {

        public static final ContentResolverType<ItemStack> FLUID_CONTAINER = ContentResolverType.of(CirrusNamespaces.ofMod("fluid_container"));
        public static final ContentResolverType<ItemStack> ITEM_CONTAINER = ContentResolverType.of(CirrusNamespaces.ofMod("item_container"));

        private ContentResolvers() {throw new AssertionError("This class should not be instantiated!");}
    }

    public final static class IngredientResolvers {
        public static final IngredientResolverType<ICustomIngredient> CUSTOM = IngredientResolverType.of(Namespace.of(NeoForgeVersion.MOD_ID, "ingredient/custom"));
        public static final IngredientResolverType<FluidIngredient> FLUID = IngredientResolverType.of(Namespace.of(NeoForgeVersion.MOD_ID, "ingredient/fluid"));

        private IngredientResolvers() {throw new AssertionError("This class should not be instantiated!");}
    }


    private CirrusTypes() {throw new AssertionError("This class should not be instantiated!");}
}
