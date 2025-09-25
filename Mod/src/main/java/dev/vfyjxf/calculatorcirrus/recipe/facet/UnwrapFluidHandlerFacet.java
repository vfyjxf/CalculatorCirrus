package dev.vfyjxf.calculatorcirrus.recipe.facet;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusResolvers;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.StackContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;

public final class UnwrapFluidHandlerFacet implements RecipeFacet {

    public static final ResourceLocation ID = CirrusLocations.ofMod("facet/unwrap_fluid_handler");
    public static final RecipeFacetType TYPE = new RecipeFacetType(ID, (entry, content) -> checkContent(content));
    public static final UnwrapFluidHandlerFacet instance = new UnwrapFluidHandlerFacet();

    //region transform

    public static final FacetTransformer<StackContent<Item, ItemStack>, UnwrapFluidHandlerFacet> transformer = FacetTransformer.of(
        (context, view, content, asInput) -> checkContent(content),
        (context, recipe, content, facet, asInput, increment, decrement) -> {
            var containerStack = content.stack().value();
            var fluidHandler = containerStack.getCapability(FluidHandler.ITEM);
            assert fluidHandler != null;//we check it before
            var fluids = MutableLists.<ContentStack<Fluid, FluidStack>>empty();
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                var fluidStack = fluidHandler.getFluidInTank(i);
                if (fluidStack.isEmpty()) continue;
                fluids.add(ContentStack.fromStack(fluidStack));
            }
            var container = fluidHandler.getContainer();
            var api = ContentApi.instance();
            var resolver = api.findStackResolver(CirrusResolvers.FLUID_CONTAINER, CirrusTypes.ITEM_STACK, container);
            if (resolver != null) {
                container = resolver.resolve(container.getItem(), container);
            }
            increment.injectInput(InputContent.fromItemStack(container, CirrusTypes.ITEM_STACK.quantity(), RecipeContent.MIN_CHANCE));

            if (fluids.isEmpty()) return;
            InputContent fluidContent;
            if (fluids.size() == 1) {
                fluidContent = InputContent.fromStack(fluids.getFirst());
            } else {
                long amount = fluids.collectLong(ContentStack::amount).sum() / fluids.size();
                fluidContent = InputContent.fromStackList(fluids, amount);
            }
            increment.injectInput(fluidContent);
        }
    );

    //endregion

    //region internal

    private static boolean checkContent(RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stack)) return false;
        if (!stack.isItem()) return false;

        ItemStack maybeHandler = stack.stack().<Item, ItemStack>cast().value();
        return maybeHandler.getCapability(FluidHandler.ITEM) != null;
    }

    private UnwrapFluidHandlerFacet() {}

    @Override
    public ResourceLocation id() {
        return ID;
    }

    //endregion
}

