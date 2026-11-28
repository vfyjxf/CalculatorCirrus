package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromeToastTest {

    private static Component text(String value) {
        return Component.literal(value);
    }

    @Test
    void stackKeepsAtMostThreeMessagesAndDropsTheOldest() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        assertEquals(3, ToastStackWidget.maxVisible);
        assertEquals(3, WorkbenchShellLayout.toastMaxVisible);

        toast.push(text("m1"));
        toast.push(text("m2"));
        toast.push(text("m3"));
        toast.push(text("m4"));

        assertEquals(3, toast.messageCount());
        assertEquals(List.of("m2", "m3", "m4"), toast.messages().stream().map(Component::getString).toList());
        assertTrue(toast.visible());
        assertNull(toast.toastAt(3));
    }

    @Test
    void undoButtonRunsItsCallbackAndRemovesTheToast() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        AtomicInteger undone = new AtomicInteger();

        toast.push(text("plain"));
        toast.push(text("undoable"), undone::incrementAndGet);
        assertEquals(2, toast.messageCount());
        assertNull(toast.undoAction(0));
        assertSame(Objects.requireNonNull(toast.toastAt(1)).undoAction(), toast.undoAction(1));

        assertTrue(toast.undo(1));
        assertEquals(1, undone.get());
        assertEquals(1, toast.messageCount());
        assertEquals(List.of("plain"), toast.messages().stream().map(Component::getString).toList());
        assertTrue(toast.visible());

        assertFalse(toast.undo(0));
        assertFalse(toast.undo(9));
        toast.dismiss(9);
        assertEquals(1, toast.messageCount());

        toast.dismiss(0);
        assertEquals(0, toast.messageCount());
        assertFalse(toast.visible());

        toast.push(text("again"));
        assertTrue(toast.visible());
        toast.clear();
        assertEquals(0, toast.messageCount());
        assertFalse(toast.visible());
    }

    @Test
    void reducedMotionSkipsTheAppearAnimation() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        toast.reducedMotion(true);
        toast.push(text("instant"));
        assertEquals(1.0, toast.appearProgress(toast.clock()), 1e-9);
        assertEquals(1.0, toast.appearProgress(Objects.requireNonNull(toast.toastAt(0)).bornAtMillis()), 1e-9);

        ToastStackWidget animated = new ToastStackWidget(ctx);
        assertFalse(animated.reducedMotion());
        animated.push(text("animated"));
        long born = Objects.requireNonNull(animated.toastAt(0)).bornAtMillis();
        assertEquals(0.0, animated.appearProgress(born), 1e-9);
        assertEquals(
            ToastStackWidget.backOut(1.0),
            animated.appearProgress(born + ToastStackWidget.appearMillis),
            1e-9
        );
        assertEquals(1.0, ToastStackWidget.backOut(1.0), 1e-9);
        assertEquals(0.0, ToastStackWidget.backOut(0.0), 1e-9);
        assertTrue(ToastStackWidget.backOut(0.5) > 0.5);
        assertEquals(0.0, animated.appearProgress(born - 1000L), 1e-9);
    }

    @Test
    void autoDismissUsesTheSixSecondBudget() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        assertEquals(6000L, ToastStackWidget.autoDismissMillis);

        toast.push(text("short lived"));
        assertEquals(1, toast.advance(1000L));
        assertEquals(1, toast.messageCount());
        assertEquals(1, toast.advance(4999L - 1000L));
        assertEquals(1, toast.messageCount());
        assertEquals(0, toast.advance(2000L));
        assertEquals(0, toast.messageCount());
        assertFalse(toast.visible());
    }

    @Test
    void rowsStackDownwardsForHitTesting() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        toast.push(text("a"));
        toast.push(text("b"));

        assertEquals(0, toast.rowY(0));
        assertEquals(ToastStackWidget.rowHeight + ToastStackWidget.gap, toast.rowY(1));
        assertNull(toast.toastAtRow(toast.rowY(1) + ToastStackWidget.rowHeight));
        assertNull(toast.toastAtRow(-1));
    }

    @Test
    void overlayRemovedMessagesAreStillUndoableOnlyThroughTheirIndex() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        toast.push(text("one"), first::incrementAndGet);
        toast.push(text("two"), second::incrementAndGet);
        toast.push(text("three"));
        toast.push(text("four"));

        assertEquals(3, toast.messageCount());
        assertEquals(0, first.get(), "the evicted toast must never run its undo callback");
        assertTrue(toast.undo(0));
        assertEquals(1, second.get());
        assertEquals(0, first.get());
    }

    @Test
    void toastStackIsHitTestableAndDismissesOnClickInScene() {
        ChromeTestContext ctx = new ChromeTestContext();
        ToastStackWidget toast = new ToastStackWidget(ctx);
        toast.push(text("dismissable"));

        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(UIStyles.sizeOf(640, 360));
        toast.useStyle(
            UIStyles.positionAbsolute(),
            UIStyles.insetLeft(380),
            UIStyles.insetTop(220),
            UIStyles.sizeOf(240, 120)
        );
        root.addWidget(toast);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 640;
            }

            @Override
            public int height() {
                return 360;
            }
        }));
        try {
            scene.setLayoutArea(640, 360);
            scene.stabilize();
            scene.layout();
            root.applyLayout();

            Rect bounds = toast.absoluteBounds();
            double clickX = bounds.right() - 12;
            double clickY = bounds.y() + 10;
            assertSame(toast, scene.hitTest(clickX, clickY), "toast must be hit-testable (not on SceneLayer.overlay)");
            assertTrue(scene.mouseClicked(clickX, clickY, 0));
            assertTrue(scene.mouseReleased(clickX, clickY, 0));
            assertEquals(0, toast.messageCount());
            assertFalse(toast.visible());
        } finally {
            scene.destroy();
        }
    }
}
