package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusResolvers;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
public class ResolverTest {

    @Test
    void testFluidContainer(MinecraftServer server) {
        ContentApi api = ContentApi.instance();
        var stack = new ItemStack(Items.BUCKET);
        var resolver = api.findStackResolver(CirrusResolvers.FLUID_CONTAINER, CirrusTypes.ITEM_STACK, stack);
        Assertions.assertNotNull(resolver);
        ItemStack resolved = resolver.resolve(stack.getItem(), stack);
        Assertions.assertSame(Items.BUCKET, resolved.getItem());
    }

}
