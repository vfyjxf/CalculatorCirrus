package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingEffect;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Inline canvas decision beacon: an 18x18 breathing diamond that expands a compact decision card in place.
 */
public class InlineDecisionBeaconWidget extends Widget {

    public static final int beaconSize = 18;
    public static final int popoverGap = 6;

    private final CoPilotHost host;
    private final ResolvedPrompt prompt;
    private @Nullable PromptCardWidget card;
    private PromptCardWidget.@Nullable PreviewListener previewListener;
    private @Nullable Consumer<String> candidateRequestListener;
    private boolean closeScheduled;

    public InlineDecisionBeaconWidget(CoPilotHost host, ResolvedPrompt prompt) {
        this.host = Objects.requireNonNull(host, "host");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        useStyle(sizeOf(beaconSize, beaconSize), flexShrink(0));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            toggleCard();
            CirrusUiSound.playClickSound();
            return EventDispatch.consumed;
        });
        setClickGroup(this);
        onClickOutside(context -> scheduleCloseCard());
        onHoverTooltip(
            Tooltip.create().add(Component.translatable(prompt.raw().titleKey())).add(
                CirrusLangs.Workbench.Copilot.leverage
                        .get(CoPilotPaint.percent(PromptDeltaFormat.leverageFraction(prompt.raw().leverageScore())))
            )
        );
        onKeyPressed((input, context) -> {
            if (input.isKey(GLFW.GLFW_KEY_ESCAPE) && expanded()) {
                closeCardNow();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public ResolvedPrompt prompt() {
        return prompt;
    }

    public InlineDecisionBeaconWidget previewListener(PromptCardWidget.@Nullable PreviewListener listener) {
        this.previewListener = listener;
        if (card != null) {
            card.previewListener(listener);
        }
        return this;
    }

    public InlineDecisionBeaconWidget candidateRequestListener(@Nullable Consumer<String> listener) {
        this.candidateRequestListener = listener;
        if (card != null) {
            card.candidateRequestListener(listener);
        }
        return this;
    }

    public boolean expanded() {
        return card != null;
    }

    public @Nullable PromptCardWidget card() {
        return card;
    }

    public boolean highLeverage() {
        return PromptDeltaFormat.isHighLeverage(prompt.raw().leverageScore());
    }

    public int beaconColor() {
        CirrusPalette palette = host.palette();
        return highLeverage() ? palette.advisoryDiscouraged() : palette.costC();
    }

    public int beaconAlpha(long timeMs) {
        double alpha = WorkbenchPaint.pulseAlpha(timeMs, host.isReducedMotion());
        int a = (int) Math.round(Math.max(0.0, Math.min(1.0, alpha)) * 255.0);
        return (a << 24) | (beaconColor() & 0x00FFFFFF);
    }

    public void toggleCard() {
        if (card != null) {
            scheduleCloseCard();
            return;
        }
        openCard();
    }

    public void openCard() {
        if (card != null || !lifecycle().mounted()) {
            return;
        }
        PromptCardWidget next = new PromptCardWidget(host, prompt, true, false);
        next.previewListener(previewListener);
        next.candidateRequestListener(candidateRequestListener);
        next.setClickGroup(this);
        next.onClickOutside(context -> scheduleCloseCard());
        next.useEffect(
            FloatingEffect.create(this, FloatingPlacement.rightStart, offset(popoverGap), shift(popoverGap))
        );
        addRootWidget(scene().root(), next);
        card = next;
    }

    public void scheduleCloseCard() {
        if (card == null || closeScheduled) {
            return;
        }
        if (!lifecycle().mounted()) {
            closeCardNow();
            return;
        }
        closeScheduled = true;
        scene().nextTick(() -> {
            closeScheduled = false;
            closeCardNow();
        });
    }

    public void closeCardNow() {
        PromptCardWidget active = card;
        card = null;
        closeScheduled = false;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette palette = host.palette();
        int size = Math.min(width(), height());
        if (size <= 0) {
            return;
        }
        int color = beaconColor();
        int alive = beaconAlpha(System.currentTimeMillis());
        float mid = size / 2f;
        float radius = mid - 2f;
        canvas.circle(mid, mid, radius + (hovered() ? 1.5f : 0.5f), alive, 1f, color);
        canvas.line(mid, 1f, size - 1f, mid, 1.5f, color);
        canvas.line(size - 1f, mid, mid, size - 1f, 1.5f, color);
        canvas.line(mid, size - 1f, 1f, mid, 1.5f, color);
        canvas.line(1f, mid, mid, 1f, 1.5f, color);
        if (expanded()) {
            canvas.strokeRect(0, 0, size, size, palette.focusRing(), 1);
        }
    }
}
