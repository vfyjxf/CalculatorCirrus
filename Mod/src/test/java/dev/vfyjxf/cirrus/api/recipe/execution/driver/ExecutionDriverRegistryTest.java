package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector.MatchResult;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.DriverContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuTypeContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.ScreenContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.SideContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverApiImpl;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverRegistry;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotExecutionDriver;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionDriverRegistryTest {

    private static final Namespace testInteractionId = Namespace.of("test", "driver");

    @Test
    void laterClientRegistrationWins() {
        var registry = new ExecutionDriverRegistry();
        var fallbackFactory = (ExecutionDriverFactory) lookup -> new DummyDriver();
        var latestFactory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        DriverSelector acceptAll = context -> MatchResult.accept;

        registry.registerClient(acceptAll, fallbackFactory);
        registry.registerClient(acceptAll, latestFactory);
        var api = new ExecutionDriverApiImpl(registry);

        var lookup = DriverLookup.of(
            new MenuTypeContext(MenuType.CRAFTING),
            new MenuContext(CraftingMenu.class),
            new ScreenContext(Screen.class)
        );
        assertSame(latestFactory, api.findClient(lookup));
    }

    @Test
    void clientAndServerLookupsAreSeparated() {
        var registry = new ExecutionDriverRegistry();
        var clientFactory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        registry.registerClient(context -> MatchResult.accept, clientFactory);
        var api = new ExecutionDriverApiImpl(registry);

        var lookup = DriverLookup.of(new MenuTypeContext(MenuType.CRAFTING), new MenuContext(CraftingMenu.class));
        assertNull(api.findServer(lookup));
    }

    @Test
    void selectorCanRejectAndFallThroughToEarlierMatch() {
        var registry = new ExecutionDriverRegistry();
        var earlierFactory = (ExecutionDriverFactory) lookup -> new DummyDriver();
        var latestFactory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        registry.registerClient(context -> switch (context) {
            case MenuContext(var type) when CraftingMenu.class.isAssignableFrom(type) -> MatchResult.accept;
            case MenuContext ignored -> MatchResult.reject;
            default -> MatchResult.pass;
        }, earlierFactory);
        registry.registerClient(context -> switch (context) {
            case MenuContext(var type) when type == CraftingMenu.class -> MatchResult.reject;
            case MenuContext ignored -> MatchResult.accept;
            default -> MatchResult.pass;
        }, latestFactory);
        var api = new ExecutionDriverApiImpl(registry);

        var lookup = DriverLookup.of(new MenuContext(CraftingMenu.class));
        assertSame(earlierFactory, api.findClient(lookup));
    }

    @Test
    void resolveRequiresRelevantContext() {
        var registry = new ExecutionDriverRegistry();
        var factory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        registry.registerClient(context -> switch (context) {
            case MenuContext(var type) when CraftingMenu.class.isAssignableFrom(type) -> MatchResult.accept;
            case MenuContext ignored -> MatchResult.reject;
            default -> MatchResult.pass;
        }, factory);
        var api = new ExecutionDriverApiImpl(registry);

        record CustomMenuContext(Class<?> menuClass) implements DriverContext {}

        var lookupNoMenu = DriverLookup.of(new ScreenContext(Screen.class), new CustomMenuContext(CraftingMenu.class));
        assertNull(api.findClient(lookupNoMenu));
    }

    @Test
    void interactionContextWorksInSelector() {
        var registry = new ExecutionDriverRegistry();
        var interactionId = Namespace.of("minecraft", "crafting_table");
        var factory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        registry.registerServer(context -> switch (context) {
            case InteractionContext(var id) when interactionId.equals(id) -> MatchResult.accept;
            case InteractionContext ignored -> MatchResult.reject;
            default -> MatchResult.pass;
        }, factory);
        var api = new ExecutionDriverApiImpl(registry);

        var matchLookup = DriverLookup.of(new InteractionContext(interactionId), new MenuContext(CraftingMenu.class));
        assertSame(factory, api.findServer(matchLookup));

        var noMatchLookup = DriverLookup
                .of(new InteractionContext(Namespace.of("mod", "other")), new MenuContext(CraftingMenu.class));
        assertNull(api.findServer(noMatchLookup));
    }

    @Test
    void factoryCanAcceptCraftingMenuSubclasses() {
        var registry = new ExecutionDriverRegistry();
        var factory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        registry.registerClient(context -> switch (context) {
            case MenuContext(var type) when CraftingMenu.class.isAssignableFrom(type) -> MatchResult.accept;
            case MenuContext ignored -> MatchResult.reject;
            default -> MatchResult.pass;
        }, factory);
        var api = new ExecutionDriverApiImpl(registry);

        assertSame(factory, api.findClient(DriverLookup.of(new MenuContext(CustomCraftingMenu.class))));
    }

    @Test
    void passOnlyMeansNoMatch() {
        var registry = new ExecutionDriverRegistry();
        var factory = (ExecutionDriverFactory) lookup -> new DummyDriver();

        registry.registerClient(context -> MatchResult.pass, factory);
        var api = new ExecutionDriverApiImpl(registry);

        var lookup = DriverLookup.of(new SideContext(ExecutionSideRequirement.client));
        assertNull(api.findClient(lookup));
    }

    @Test
    void resolvedHandleCarriesNormalizedLookupForRecreation() {
        var registry = new ExecutionDriverRegistry();
        AtomicReference<DriverLookup> createLookup = new AtomicReference<>();
        var factory = (ExecutionDriverFactory) lookup -> {
            createLookup.set(lookup);
            return new DummyDriver();
        };

        registry.registerClient(context -> MatchResult.accept, factory);
        var api = new ExecutionDriverApiImpl(registry);

        var handle = api.resolveClient(DriverLookup.of(new MenuContext(CraftingMenu.class)));
        assertNotNull(handle);
        assertSame(factory, handle.factory());
        assertEquals(ExecutionSideRequirement.client, handle.side());

        handle.create();
        assertNotNull(createLookup.get());
        assertEquals(ExecutionSideRequirement.client, createLookup.get().side());
    }

    private static final class DummyDriver implements ExecutionDriver {
        @Override
        public ExecutionSession session() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Namespace interactionId() {
            return testInteractionId;
        }

        @Override
        public InteractionApi interactionApi() {
            return SlotExecutionDriver
                    .simpleInteraction(testInteractionId, false, ExecutionGuidance.simple(Component.literal("dummy")));
        }

        @Override
        public ExecutionDriverSnapshot snapshot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advanceSteps(int steps) {}

        @Override
        public void pause() {}

        @Override
        public void resume() {}

        @Override
        public void toggleAutoAdvance() {}

        @Override
        public void stopAutoAdvance() {}

        @Override
        public void setRatePerSecond(double rate) {}

        @Override
        public void reset() {}

        @Override
        public void jumpToStep(int index) {}

        @Override
        public void tick() {}
    }

    private static final class CustomCraftingMenu extends CraftingMenu {
        private CustomCraftingMenu(int containerId, Inventory inventory) {
            super(containerId, inventory);
        }
    }
}
