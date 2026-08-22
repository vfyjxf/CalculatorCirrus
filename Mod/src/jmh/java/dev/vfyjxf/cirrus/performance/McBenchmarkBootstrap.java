package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Boots vanilla registries and the mod's content API for JMH benchmarks that need real
 * {@link net.minecraft.world.item.ItemStack} data components.
 *
 * <p>Outside FML, NeoForge's {@code FeatureFlags} class-init dereferences {@code LoadingModList.get()}
 * (null without a mod loader). We install an empty {@code LoadingModList} via reflection first — the
 * vanilla {@link Bootstrap#bootStrap()} path then completes. Enchantments are data-driven in 1.21.1
 * and stay empty without a datapack; items, potions and component types are code-registered and work.
 */
public final class McBenchmarkBootstrap {

    private static volatile boolean bootstrapped;
    private static volatile boolean contentApiAttached;

    private McBenchmarkBootstrap() {
    }

    public static synchronized void ensureBootstrapped() {
        if (bootstrapped) return;
        installEmptyLoadingModList();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Force class-init of the registries the benchmarks touch.
        Items.DIAMOND_SWORD.hashCode();
        DataComponents.MAX_STACK_SIZE.hashCode();
        Potions.HEALING.hashCode();
        BuiltInRegistries.ITEM.hashCode();
        bootstrapped = true;
    }

    /**
     * Attaches a {@link ContentApiImpl} built from the mod's {@link InternalPlugin} builtin content
     * registrations, so {@code ContentStack.fromStack} and friends work in the benchmark JVM.
     */
    public static synchronized void ensureContentApi() {
        if (contentApiAttached) return;
        ensureBootstrapped();
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin plugin = new InternalPlugin();
        plugin.registerContentType(builder);
        plugin.registerContentVariant(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
        contentApiAttached = true;
    }

    private static void installEmptyLoadingModList() {
        try {
            Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Object empty = loadingModList
                    .getMethod("of", List.class, List.class, List.class, List.class, Map.class)
                    .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
            Field instance = loadingModList.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            instance.set(null, empty);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to install empty LoadingModList for benchmark bootstrap", e);
        }
    }
}
