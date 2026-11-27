package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.testutil.Reflect;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneTooltipRefreshContractTest {

    @Test
    void hoverTooltipRefreshesAfterClickWithoutMouseMove() throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(false);
        TestRoot root = new TestRoot();
        root.onHoverTooltip(
            (mouseX, mouseY) -> Tooltip.create().add(Component.literal(enabled.get() ? "enabled" : "disabled"))
        );
        root.onMouseClick((input, clickCount, context) -> {
            enabled.set(!enabled.get());
            context.consume();
            return EventDispatch.consumed;
        });

        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        root.setTestBounds(100, 100);

        scene.mouseMoved(5, 5);
        assertEquals(List.of("disabled"), hoverTooltipLines(scene));

        scene.mouseClicked(5, 5, 0);
        scene.mouseReleased(5, 5, 0);

        assertEquals(List.of("enabled"), hoverTooltipLines(scene));
    }

    private static List<String> hoverTooltipLines(Scene scene) {
        Tooltip tooltip = Reflect.field(scene, "hoverTooltip", Tooltip.class);
        return tooltip.flatEntries().stream().map(SceneTooltipRefreshContractTest::entryText).toList();
    }

    private static String entryText(TooltipEntry entry) {
        return switch (entry) {
            case TooltipEntry.TextEntry text -> text.text().getString();
            case TooltipEntry.DynamicEntry dynamic -> dynamic.provider().get().getString();
            case TooltipEntry.ComponentEntry ignored -> "";
            case TooltipEntry.RichTextEntry rich -> rich.text().toComponent().getString();
        };
    }

    private static SceneHost testSceneHost() {
        return new SceneHost() {
            @Override
            // Headless host: no Minecraft Font exists in unit tests, and the paths under test never read it.
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 100;
            }

            @Override
            public int height() {
                return 100;
            }
        };
    }

    private static final class TestRoot extends WidgetGroup<Widget> {
        void setTestBounds(int width, int height) {
            setBound(0, 0, width, height);
        }
    }
}
