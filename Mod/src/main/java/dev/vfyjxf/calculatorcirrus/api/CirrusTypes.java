package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.calculatorcirrus.api.recipe.value.EnergyContentType;
import dev.vfyjxf.calculatorcirrus.api.recipe.value.EnergyValue;
import dev.vfyjxf.calculatorcirrus.api.recipe.value.TimeValue;
import dev.vfyjxf.calculatorcirrus.api.recipe.value.TimeValueType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidContentType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemContentType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemStackType;
import dev.vfyjxf.calculatorcirrus.recipe.facet.ItemDamageFacet;
import dev.vfyjxf.calculatorcirrus.recipe.facet.UnwrapFluidHandlerFacet;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

/**
 * All builtin {@link ContentType} & {@link StackType} & {@link TagType}
 * For the purpose of reducing ambiguity, members here use uppercase names
 */
public final class CirrusTypes {

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
        public static final TagType<Item> ITEM_TAG = TagType.fromRegistry(Locations.ofMc("tag/item"), ITEM, BuiltInRegistries.ITEM);
        public static final TagType<Fluid> FLUID_TAG = TagType.fromRegistry(Locations.ofMc("tag/fluid"), FLUID, BuiltInRegistries.FLUID);
        //endregion

        //region recipe content
        public static final ValueType<TimeValue> TIME = TimeValueType.instance;
        public static final ValueType<EnergyValue> ENERGY = EnergyContentType.instance;
        //endregion

        private Contents() {throw new AssertionError("This class should not be instantiated!");}
    }

    public final static class Facets {

        public static final RecipeFacetType FLUID_HANDLE_2_FLUID = UnwrapFluidHandlerFacet.type;
        public static final RecipeFacetType ITEM_2_DAMAGE = ItemDamageFacet.type;

        private Facets() {throw new AssertionError("This class should not be instantiated!");}
    }

    public final static class ContentResolvers {

        public static final ContentResolverType<ItemStack> FLUID_CONTAINER = ContentResolverType.of(CirrusLocations.ofMod("fluid_container"));
        public static final ContentResolverType<ItemStack> ITEM_CONTAINER = ContentResolverType.of(CirrusLocations.ofMod("item_container"));

        private ContentResolvers() {throw new AssertionError("This class should not be instantiated!");}
    }

    public final static class IngredientResolvers {
        public static final IngredientResolverType<ICustomIngredient> CUSTOM = IngredientResolverType.of(Locations.create(NeoForgeVersion.MOD_ID, "ingredient/custom"));
        public static final IngredientResolverType<FluidIngredient> FLUID = IngredientResolverType.of(Locations.create(NeoForgeVersion.MOD_ID, "ingredient/fluid"));

        private IngredientResolvers() {throw new AssertionError("This class should not be instantiated!");}
    }


    private CirrusTypes() {throw new AssertionError("This class should not be instantiated!");}
}
