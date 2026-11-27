package dev.vfyjxf.cirrus.ui.execution.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayLayoutScreenDetectionTest {

    @Test
    void containerScreensUseCompactJeiInsets() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(
            478,
            768,
            ExecutionOverlayLayout.usesCompactInsets(FakeContainerScreen.class),
            162,
            148,
            162
        );

        assertTrue(ExecutionOverlayLayout.usesCompactInsets(FakeContainerScreen.class));
        assertEquals(6, bounds.getX());
        assertEquals(6, bounds.getY());
        assertEquals(734, bounds.getHeight());
    }

    @Test
    void plainScreensKeepNonJeiInsets() {
        assertFalse(ExecutionOverlayLayout.usesCompactInsets(FakeScreen.class));
    }

    private static final class FakeScreen extends Screen {
        private FakeScreen() {
            super(Component.literal("plain"));
        }
    }

    private static final class FakeContainerScreen extends AbstractContainerScreen<AbstractContainerMenu> {
        private FakeContainerScreen() {
            super(null, null, Component.literal("container"));
        }

        @Override
        protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {}
    }
}
