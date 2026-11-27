package dev.vfyjxf.cirrus.config;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = CirrusConstants.modId)
public class ModConfig {
    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue executionServerDriverEnabled;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> executionServerDriverInteractionWhitelist;
    public static final ModConfigSpec spec;
    private static final Namespace defaultInteractionId = Namespace.of("minecraft", "crafting_table");

    private static volatile boolean executionServerDriverEnabledCache = false;
    private static volatile Set<Namespace> executionServerDriverInteractionWhitelistCache = Set
            .of(defaultInteractionId);

    static {
        builder.push("execution");
        builder.push("serverDriver");
        executionServerDriverEnabled = builder
                .comment("Enable server-side execution driver for whitelisted interactions.").define("enabled", false);
        executionServerDriverInteractionWhitelist = builder
                .comment("Interaction ids allowed to run with server-side execution driver.").defineListAllowEmpty(
                    List.of("interactionWhitelist"),
                    List.of("minecraft:crafting_table"),
                    ModConfig::isValidInteractionId
                );
        builder.pop();
        builder.pop();
        spec = builder.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == spec) {
            refreshCachedValues();
        }
    }

    public static boolean isServerDriverEnabled() {
        return executionServerDriverEnabledCache;
    }

    public static boolean isServerDriverEnabledFor(Namespace interactionId) {
        return interactionId != null
                && executionServerDriverEnabledCache
                && executionServerDriverInteractionWhitelistCache.contains(interactionId);
    }

    private static boolean isValidInteractionId(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        return Namespace.maybe(text) != null;
    }

    private static void refreshCachedValues() {
        executionServerDriverEnabledCache = executionServerDriverEnabled.get();
        Set<Namespace> whitelist = new HashSet<>();
        for (String id : executionServerDriverInteractionWhitelist.get()) {
            Namespace parsed = Namespace.maybe(id);
            if (parsed != null) {
                whitelist.add(parsed);
            }
        }
        executionServerDriverInteractionWhitelistCache = Set.copyOf(whitelist);
    }
}
