package dev.vfyjxf.cirrus.api.content;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.helper.TagHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@ExtendWith(EphemeralTestServerProvider.class)
class ContentApiTest {

    @Test
    void testContentApi(MinecraftServer server) {
        ContentApi instance = ContentApi.instance();
        var itemId = Namespace.ofMc("item_stack");
        StackType<Item, ItemStack> stackType = instance.getStackType(itemId);
        Assertions.assertNotNull(stackType);
        //        Assertions.assertEquals(Item.class, stackType.contentType().valueType());
        var fluidId = Namespace.ofMc("fluid_stack");
        StackType<Fluid, FluidStack> fluidType = instance.getStackType(fluidId);
        Assertions.assertNotNull(fluidType);
        //        Assertions.assertEquals(Fluid.class, fluidType.contentType().valueType());
    }

    @Test
    void testContentMatches(MinecraftServer server) {
        var itemType = CirrusTypes.Contents.itemStack;
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
        Assertions.assertFalse(itemType.matches(b, a));
        Assertions.assertFalse(itemType.matches(a, c));
        Assertions.assertFalse(itemType.matches(c, d));
        Assertions.assertFalse(itemType.matches(d, e));
        Assertions.assertFalse(itemType.matches(e, d));
        Assertions.assertFalse(itemType.matches(a, f));
        Assertions.assertFalse(itemType.matches(e, f));
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
        TagType<Item> tagType = Objects.requireNonNull(CirrusTypes.Contents.item.tagType(), "item tagType");
        var first = tagType.allTags();
        var second = tagType.allTags();
        Assertions.assertSame(first, second);
        TagType.clearAllTagsCache();
        var third = tagType.allTags();
        Assertions.assertNotSame(first, third);
    }

    @Test
    void testTagHelperMatchStaysStable(MinecraftServer server) {
        TagType<Item> tagType = Objects.requireNonNull(CirrusTypes.Contents.item.tagType(), "item tagType");
        TagKey<Item> tag = null;
        Collection<? extends ContentStack<Item, ?>> stacks = null;
        for (TagKey<Item> candidate : tagType.allTags()) {
            var values = tagType.getTagValues(candidate);
            if (values.isEmpty()) continue;
            var collected = new ArrayList<ContentStack<Item, ?>>();
            for (Item item : values) {
                collected.add(ContentStack.fromContent(item));
            }
            tag = candidate;
            stacks = collected;
            break;
        }
        Assertions.assertNotNull(tag);
        Assertions.assertNotNull(stacks);

        Assertions.assertEquals(tag, TagHelper.findTagMatchStacks(tagType, stacks));
        TagType.clearAllTagsCache();
        Assertions.assertEquals(tag, TagHelper.findTagMatchStacks(tagType, stacks));
    }

    @Test
    void testTagHelperMatchesDuplicateStackInputs(MinecraftServer server) {
        TagType<Item> tagType = Objects.requireNonNull(CirrusTypes.Contents.item.tagType(), "item tagType");
        TagKey<Item> tag = null;
        Collection<? extends ContentStack<Item, ?>> duplicated = null;
        for (TagKey<Item> candidate : tagType.allTags()) {
            var values = tagType.getTagValues(candidate);
            // a single-member tag duplicated across slots: the degenerate case that
            // used to compare 2 inputs against a tag size of 1 and scan to null
            if (values.size() != 1) continue;
            var one = ContentStack.fromContent(values.iterator().next());
            duplicated = List.of(one, one);
            tag = candidate;
            break;
        }
        Assertions.assertNotNull(tag, "no single-member item tag found in the ephemeral registry");
        Assertions.assertEquals(
            tag,
            TagHelper.findTagMatchStacks(tagType, Objects.requireNonNull(duplicated, "duplicated")),
            "duplicate-stack inputs must still resolve through the member index"
        );
    }

}
