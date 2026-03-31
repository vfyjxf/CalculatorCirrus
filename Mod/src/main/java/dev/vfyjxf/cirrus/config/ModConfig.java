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
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue EXECUTION_SERVER_DRIVER_ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXECUTION_SERVER_DRIVER_INTERACTION_WHITELIST;
    public static final ModConfigSpec SPEC;
    private static final Namespace DEFAULT_INTERACTION_ID =
        Namespace.of("minecraft", "crafting_table");

    private static volatile boolean executionServerDriverEnabled = false;
    private static volatile Set<Namespace> executionServerDriverInteractionWhitelist = Set.of(DEFAULT_INTERACTION_ID);

    static {
        BUILDER.push("execution");
        BUILDER.push("serverDriver");
        EXECUTION_SERVER_DRIVER_ENABLED = BUILDER
            .comment("Enable server-side execution driver for whitelisted interactions.")
            .define("enabled", false);
        EXECUTION_SERVER_DRIVER_INTERACTION_WHITELIST = BUILDER
            .comment("Interaction ids allowed to run with server-side execution driver.")
            .defineListAllowEmpty(
                List.of("interactionWhitelist"),
                List.of("minecraft:crafting_table"),
                ModConfig::isValidInteractionId
            );
        BUILDER.pop();
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshCachedValues();
        }
    }

    public static boolean isServerDriverEnabled() {
        return executionServerDriverEnabled;
    }

    public static boolean isServerDriverEnabledFor(Namespace interactionId) {
        return interactionId != null
            && executionServerDriverEnabled
            && executionServerDriverInteractionWhitelist.contains(interactionId);
    }

    private static boolean isValidInteractionId(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        return Namespace.maybe(text) != null;
    }

    private static void refreshCachedValues() {
        executionServerDriverEnabled = EXECUTION_SERVER_DRIVER_ENABLED.get();
        Set<Namespace> whitelist = new HashSet<>();
        for (String id : EXECUTION_SERVER_DRIVER_INTERACTION_WHITELIST.get()) {
            Namespace parsed = Namespace.maybe(id);
            if (parsed != null) {
                whitelist.add(parsed);
            }
        }
        executionServerDriverInteractionWhitelist = Set.copyOf(whitelist);
    }
}
