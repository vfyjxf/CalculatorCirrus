package dev.vfyjxf.cirrus.recipe.facet;

import dev.vfyjxf.cirrus.api.CirrusTypes.ContentResolvers;
import dev.vfyjxf.cirrus.api.CirrusTypes.Contents;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer.ActionResult;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;

public final class UnwrapFluidHandlerFacet implements RecipeFacet {

    public static final Namespace id = CirrusNamespaces.ofMod("fluid/unwrap_fluid_handler");
    public static final RecipeFacetType type = new RecipeFacetType(id, (entry, content) -> checkContent(content));
    public static final UnwrapFluidHandlerFacet instance = new UnwrapFluidHandlerFacet();

    //region transform

    public static final FacetTransformer<StackContent<Item, ItemStack>, UnwrapFluidHandlerFacet> transformer = FacetTransformer.of(
        (context, view, content, asInput) -> checkContent(content),
        (context, recipe, content, facet, asInput, increment, decrement) -> {
            var containerStack = content.content().value();
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
            var resolver = api.findStackResolver(ContentResolvers.FLUID_CONTAINER, Contents.ITEM_STACK, container);
            if (resolver != null) {
                container = resolver.resolve(container.getItem(), container);
            }
            increment.injectInput(InputContent.fromItemStack(container, Contents.ITEM_STACK.quantity(), RecipeContent.MIN_CHANCE));
            if (fluids.isEmpty()) return ActionResult.RETAIN;
            InputContent fluidContent;
            if (fluids.size() == 1) {
                fluidContent = InputContent.fromStack(fluids.getFirst());
            } else {
                long amount = fluids.collectLong(ContentStack::amount).sum() / fluids.size();
                fluidContent = InputContent.fromStackList(fluids, amount);
            }
            increment.injectInput(fluidContent);
            return ActionResult.REMOVE;
        }
    );

    //endregion

    //region internal

    private static boolean checkContent(RecipeContent content) {
        if (!(content instanceof StackContent<?, ?> stack)) return false;
        if (!stack.isItem()) return false;

        ItemStack maybeHandler = stack.content().<Item, ItemStack>cast().value();
        return maybeHandler.getCapability(FluidHandler.ITEM) != null;
    }

    private UnwrapFluidHandlerFacet() {}

    @Override
    public Namespace id() {
        return id;
    }

    //endregion
}

