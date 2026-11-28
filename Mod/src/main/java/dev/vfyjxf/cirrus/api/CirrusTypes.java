package dev.vfyjxf.cirrus.api;

import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyContentType;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValueType;
import dev.vfyjxf.cirrus.content.type.FluidContentType;
import dev.vfyjxf.cirrus.content.type.FluidStackType;
import dev.vfyjxf.cirrus.content.type.ItemContentType;
import dev.vfyjxf.cirrus.content.type.ItemStackType;
import dev.vfyjxf.cirrus.recipe.projection.BuiltinProjectionEffects;
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
 * All builtin {@link ContentType} & {@link StackType} & {@link TagType} & {@link ProjectionEffect} & {@link ContentResolverType} & {@link IngredientResolverType}
 * For the purpose of reducing ambiguity, members here use uppercase names
 */
public final class CirrusTypes {

    //NOTE: uppercase because the lowercase names carry too much ambiguity
    public final static class Contents {

        //region content
        public static final ContentType<Item> item = ItemContentType.instance;
        public static final ContentType<Fluid> fluidConst = FluidContentType.instance;
        //endregion

        //region stack
        public static final StackType<Item, ItemStack> itemStack = ItemStackType.instance;
        public static final StackType<Fluid, FluidStack> fluidStack = FluidStackType.instance;
        //endregion

        //region tag
        public static final TagType<Item> itemTag = TagType
                .fromRegistry(Namespace.ofMc("tag/item"), item, Registries.ITEM);
        public static final TagType<Fluid> fluidTag = TagType
                .fromRegistry(Namespace.ofMc("tag/fluid"), fluidConst, Registries.FLUID);
        //endregion

        //region recipe content
        public static final ValueType<TimeValue> time = TimeValueType.instance;
        public static final ValueType<EnergyValue> energy = EnergyContentType.instance;
        //endregion

        private Contents() {
            throw new AssertionError("This class should not be instantiated!");
        }
    }

    public final static class ProjectionEffects {

        public static final ProjectionEffect fluidContainerUnpack = BuiltinProjectionEffects.fluidContainerUnpack;

        public static final ProjectionEffect durabilityCost = BuiltinProjectionEffects.durabilityCost;

        public static final ProjectionEffect guaranteedOutput = BuiltinProjectionEffects.guaranteedOutput;

        private ProjectionEffects() {
            throw new AssertionError("This class should not be instantiated!");
        }
    }

    public final static class ContentResolvers {

        public static final ContentResolverType<ItemStack> fluidContainer = ContentResolverType
                .of(CirrusNamespaces.ofMod("fluid_container"));
        public static final ContentResolverType<ItemStack> itemContainer = ContentResolverType
                .of(CirrusNamespaces.ofMod("item_container"));

        private ContentResolvers() {
            throw new AssertionError("This class should not be instantiated!");
        }
    }

    public final static class IngredientResolvers {
        public static final IngredientResolverType<ICustomIngredient> custom = IngredientResolverType
                .of(Namespace.of(NeoForgeVersion.MOD_ID, "ingredient/custom"));
        public static final IngredientResolverType<FluidIngredient> fluidConst = IngredientResolverType
                .of(Namespace.of(NeoForgeVersion.MOD_ID, "ingredient/fluid"));

        private IngredientResolvers() {
            throw new AssertionError("This class should not be instantiated!");
        }
    }

    private CirrusTypes() {
        throw new AssertionError("This class should not be instantiated!");
    }
}
