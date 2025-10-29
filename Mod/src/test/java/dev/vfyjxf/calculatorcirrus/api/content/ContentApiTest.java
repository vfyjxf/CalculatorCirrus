package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(EphemeralTestServerProvider.class)
class ContentApiTest {


    @Test
    void testContentApi(MinecraftServer server) {
        ContentApi instance = ContentApi.instance();
        var itemId = ResourceLocation.fromNamespaceAndPath(ContentType.namespace, "item_stack");
        StackType<Item, ItemStack> stackType = instance.getStackType(itemId);
        Assertions.assertNotNull(stackType);
        Assertions.assertEquals(Item.class, stackType.contentType().valueType());
        var fluidId = ResourceLocation.fromNamespaceAndPath(ContentType.namespace, "fluid_stack");
        StackType<Fluid, FluidStack> fluidType = instance.getStackType(fluidId);
        Assertions.assertNotNull(fluidType);
        Assertions.assertEquals(Fluid.class, fluidType.contentType().valueType());
    }

    @Test
    void testContentMatches(MinecraftServer server) {
        var itemType = CirrusTypes.Contents.ITEM_STACK;
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
        Assertions.assertFalse(itemType.matches(a, b));
        Assertions.assertTrue(itemType.matches(a, b));
        Assertions.assertTrue(itemType.matches(b, a));
        Assertions.assertFalse(itemType.matches(a, c));
        Assertions.assertFalse(itemType.matches(a, c));
        Assertions.assertTrue(itemType.matches(c, d));
        Assertions.assertTrue(itemType.matches(c, d));
        Assertions.assertTrue(itemType.matches(d, e));
        Assertions.assertFalse(itemType.matches(e, d));
        Assertions.assertTrue(itemType.matches(a, f));
        Assertions.assertTrue(itemType.matches(e, f));
    }


    @Test
    void testContentEmpty(MinecraftServer server) {
        var is = ContentStack.fromStack(ItemStack.EMPTY);
        var fs = ContentStack.fromStack(FluidStack.EMPTY);
        var empty = ContentStack.empty();
        Assertions.assertTrue(is.isEmpty());
        Assertions.assertTrue(fs.isEmpty());
        Assertions.assertTrue(empty.isEmpty());
    }


    @Test
    void testTagType(MinecraftServer server) {


    }


}
