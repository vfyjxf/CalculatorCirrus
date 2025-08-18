package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static dev.vfyjxf.calculatorcirrus.api.content.value.TagStack.createFluidTag;
import static dev.vfyjxf.calculatorcirrus.api.content.value.TagStack.createItemTag;

@ExtendWith(EphemeralTestServerProvider.class)
class ContentApiTest {


    @Test
    void testContentApi(MinecraftServer server) {
        ContentApi instance = ContentApi.getInstance();
        var itemId = ResourceLocation.fromNamespaceAndPath(ContentType.namespace, "item_stack");
        StackContentType<ItemStack> stackType = instance.getStackType(itemId);
        Assertions.assertEquals(stackType, instance.getContentType("content_type:item_stack"));
        Assertions.assertNotNull(stackType);
        Assertions.assertEquals(ItemStack.class, stackType.valueType());
        Assertions.assertEquals(Item.class, stackType.baseType());
        var fluidId = ResourceLocation.fromNamespaceAndPath(ContentType.namespace, "fluid_stack");
        StackContentType<FluidStack> fluidType = instance.getStackType(fluidId);
        Assertions.assertEquals(fluidType, instance.getContentType("content_type:fluid_stack"));
        Assertions.assertNotNull(fluidType);
        Assertions.assertEquals(FluidStack.class, fluidType.valueType());
        Assertions.assertEquals(Fluid.class, fluidType.baseType());
    }

    @Test
    void testContentMatches(MinecraftServer server) {
        var itemType = CirrusContentTypes.ITEM;
        var a = new ItemStack(Items.CANDLE);
        var b = new ItemStack(Items.CANDLE, 2);
        var c = new ItemStack(Items.WHEAT, 30);
        c.set(DataComponents.MAX_STACK_SIZE, 99);
        var d = new ItemStack(Items.STICK, 30);
        d.set(DataComponents.MAX_STACK_SIZE, 99);
        var e = new ItemStack(Items.BEDROCK, 30);
        e.set(DataComponents.MAX_STACK_SIZE, 98);
        e.set(DataComponents.CUSTOM_NAME, Component.literal("HelloWorld"));
        var f = new ItemStack(Items.CANDLE);
        f.set(DataComponents.MAX_STACK_SIZE, 99);
        Assertions.assertFalse(itemType.matches(a, b, MatchStrategy.ALL));
        Assertions.assertTrue(itemType.matches(a, b, MatchStrategy.IDENTITY));
        Assertions.assertTrue(itemType.matches(b, a, MatchStrategy.IDENTITY_COMPONENT));
        Assertions.assertFalse(itemType.matches(a, c, MatchStrategy.IDENTITY_COMPONENT));
        Assertions.assertFalse(itemType.matches(a, c, MatchStrategy.AMOUNT_COMPONENT));
        Assertions.assertTrue(itemType.matches(c, d, MatchStrategy.AMOUNT_COMPONENT));
        Assertions.assertTrue(itemType.matches(c, d, MatchStrategy.COMPONENT));
        Assertions.assertTrue(itemType.matches(d, e, MatchStrategy.AMOUNT));
        Assertions.assertFalse(itemType.matches(e, d, MatchStrategy.COMPONENT));
        Assertions.assertTrue(itemType.matches(a, f, MatchStrategy.IDENTITY_AMOUNT));
        Assertions.assertTrue(itemType.matches(e, f, MatchStrategy.NONE));
    }


    @Test
    void testContentEmpty(MinecraftServer server) {
        ContentStack<ItemStack> is = ContentStack.create(ItemStack.EMPTY);
        ContentStack<FluidStack> fs = ContentStack.create(FluidStack.EMPTY);
        ContentStack<?> empty = ContentStack.empty();
        Assertions.assertTrue(is.isEmpty());
        Assertions.assertTrue(fs.isEmpty());
        Assertions.assertTrue(empty.isEmpty());
    }


    @Test
    void testTagContent(MinecraftServer server) {
        var animalFood = createItemTag(Tags.Items.ANIMAL_FOODS, 100);
        var armors = createItemTag(Tags.Items.ARMORS, 100);
        var animalFood2 = createItemTag(Tags.Items.ANIMAL_FOODS, 1000);
        var lava = createFluidTag(Tags.Fluids.LAVA, 1000);
        var water = createFluidTag(Tags.Fluids.WATER, 100);
        var lava2 = createFluidTag(Tags.Fluids.LAVA, 100);
        Assertions.assertTrue(animalFood.matches(animalFood2, MatchStrategy.IDENTITY));
        Assertions.assertTrue(animalFood.matches(armors, MatchStrategy.AMOUNT));
        Assertions.assertTrue(lava.matches(animalFood2, MatchStrategy.AMOUNT));
        Assertions.assertFalse(animalFood.matches(animalFood2, MatchStrategy.AMOUNT));
        Assertions.assertTrue(lava.matches(lava2, MatchStrategy.IDENTITY));
        Assertions.assertTrue(lava2.matches(water, MatchStrategy.AMOUNT));

    }


}
