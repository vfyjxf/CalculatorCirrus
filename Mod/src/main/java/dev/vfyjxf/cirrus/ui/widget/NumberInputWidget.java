package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorInputKeyMappings;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;

/**
 * Number input widget that only accepts numeric input.
 * Supports keyboard, mouse wheel scrolling, and middle-click to open calculator.
 */
public class NumberInputWidget extends Widget {

    private long value = 1;
    private long minValue = 0;
    private long maxValue = Long.MAX_VALUE;
    private long scrollStep = 1;
    private boolean scrollEnabled = true;

    private String displayText = "1";
    private int cursorPosition = 1;
    private int selectionStart = -1;
    private int scrollOffset = 0;
    private long cursorBlinkEpoch = System.currentTimeMillis();
    private boolean rangeWarning = false;
    private long committedValue = 1;

    @Nullable
    private LongConsumer onValueChanged;
    @Nullable
    private CalculatorOpenCallback onCalculatorOpen;
    @Nullable
    private Supplier<Tooltip> tooltipSupplier;
    private boolean defaultCalculatorPopupEnabled;

    @FunctionalInterface
    public interface CalculatorOpenCallback {
        void open(NumberInputWidget source, int mouseX, int mouseY);
    }

    public NumberInputWidget() {
        setFocusable(true);
        useStyle(
                background(Background.Outlined.inset)
        );
        setupEvents();
    }

    public static NumberInputWidget create() {
        return new NumberInputWidget();
    }

    public static NumberInputWidget create(long initialValue) {
        NumberInputWidget widget = new NumberInputWidget();
        widget.setValue(initialValue);
        return widget;
    }

    private void setupEvents() {
        onMouseClick((input, clickCount, context) -> {
            if (onCalculatorOpen != null
                && (input.key().getValue() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || CalculatorInputKeyMappings.openAmountCalculatorPopupPressed(input.key()))) {
                openCalculator((int) input.mouseX(), (int) input.mouseY());
                return EventDispatch.consumed;
            }
            // Position cursor at click location
            var font = Minecraft.getInstance().font;
            int clickX = (int) input.mouseX() - 4 + scrollOffset;
            int bestPos = displayText.length();
            for (int i = 0; i <= displayText.length(); i++) {
                int charX = font.width(displayText.substring(0, i));
                if (charX > clickX) {
                    if (i > 0) {
                        int prevCharX = font.width(displayText.substring(0, i - 1));
                        bestPos = (clickX - prevCharX < charX - clickX) ? i - 1 : i;
                    } else {
                        bestPos = 0;
                    }
                    break;
                }
            }
            cursorPosition = bestPos;
            selectionStart = -1;
            cursorBlinkEpoch = System.currentTimeMillis();
            return EventDispatch.consumed;
        });

        onKeyPressed((input, context) -> {
            if (!focused()) return EventDispatch.pass;
            if (onCalculatorOpen != null && CalculatorInputKeyMappings.openAmountCalculatorPopupPressed(input.key())) {
                openCalculator(absolutePos().x(), absolutePos().y());
                return EventDispatch.consumed;
            }
            return handleKeyInput(input.key().getValue(), input.isCtrlDown());
        });

        onCharTyped((codePoint, modifiers, context) -> {
            if (!focused()) return EventDispatch.pass;
            return handleCharInput(codePoint);
        });

        onFocusLost(context -> commitOrRollback());

        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
            if (!scrollEnabled || scrollY == 0) return EventDispatch.pass;
            context.consume();
            if (scrollY > 0) {
                adjustValue(scrollStep);
            } else {
                adjustValue(-scrollStep);
            }
            return EventDispatch.consumed;
        });
        onHoverTooltip((mouseX, mouseY) -> {
            Tooltip supplied = tooltipSupplier == null ? null : tooltipSupplier.get();
            if (supplied != null) {
                return supplied;
            }
            return tooltip().notEmpty() ? tooltip() : null;
        });
    }

    private EventDispatch handleKeyInput(int key, boolean ctrl) {
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    selectionStart = 0;
                    cursorPosition = displayText.length();
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_C -> {
                    copySelection();
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_X -> {
                    copySelection();
                    deleteSelection();
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_V -> {
                    pasteFromClipboard();
                    return EventDispatch.consumed;
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    displayText = displayText.substring(0, cursorPosition - 1) + displayText.substring(cursorPosition);
                    cursorPosition--;
                    if (displayText.isEmpty()) {
                        displayText = "0";
                        cursorPosition = 1;
                    }
                    updateValueFromText();
                }
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < displayText.length()) {
                    displayText = displayText.substring(0, cursorPosition) + displayText.substring(cursorPosition + 1);
                    if (displayText.isEmpty()) {
                        displayText = "0";
                        cursorPosition = 0;
                    }
                    updateValueFromText();
                }
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorPosition > 0) cursorPosition--;
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPosition < displayText.length()) cursorPosition++;
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0;
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPosition = displayText.length();
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_UP -> {
                adjustValue(scrollStep);
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                adjustValue(-scrollStep);
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    rollbackToCommitted();
                } else {
                    commitOrRollback();
                }
                scene().requestUnfocus(this);
                return EventDispatch.consumed;
            }
        }
        return EventDispatch.pass;
    }

    private EventDispatch handleCharInput(char codePoint) {
        if (Character.isDigit(codePoint)) {
            if (hasSelection()) {
                deleteSelection();
            }
            if (displayText.equals("0") && codePoint != '0') {
                displayText = String.valueOf(codePoint);
                cursorPosition = 1;
            } else {
                displayText = displayText.substring(0, cursorPosition) + codePoint + displayText.substring(cursorPosition);
                cursorPosition++;
            }
            stripLeadingZeros();
            updateValueFromText();
            return EventDispatch.consumed;
        }
        if (codePoint == '-' && cursorPosition == 0 && minValue < 0 && !displayText.startsWith("-")) {
            displayText = "-" + displayText;
            cursorPosition++;
            updateValueFromText();
            return EventDispatch.consumed;
        }
        return EventDispatch.pass;
    }

    private void updateValueFromText() {
        stripLeadingZeros();
        selectionStart = -1;
        if (displayText.isEmpty() || displayText.equals("-")) {
            rangeWarning = true;
        } else {
            try {
                long parsed = Long.parseLong(displayText);
                if (parsed < minValue || parsed > maxValue) {
                    rangeWarning = true;
                    return;
                }
                rangeWarning = false;
                value = parsed;
                committedValue = parsed;
                notifyValueChanged();
            } catch (NumberFormatException e) {
                rangeWarning = true;
            }
        }
    }

    private void stripLeadingZeros() {
        boolean negative = displayText.startsWith("-");
        String digits = negative ? displayText.substring(1) : displayText;
        while (digits.length() > 1 && digits.startsWith("0")) {
            digits = digits.substring(1);
            cursorPosition = Math.max(negative ? 1 : 0, cursorPosition - 1);
        }
        displayText = negative ? "-" + digits : digits;
    }

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionStart != cursorPosition;
    }

    private int selStart() {
        return Math.min(selectionStart, cursorPosition);
    }

    private int selEnd() {
        return Math.max(selectionStart, cursorPosition);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = selStart();
        int end = selEnd();
        displayText = displayText.substring(0, start) + displayText.substring(end);
        cursorPosition = start;
        selectionStart = -1;
        if (displayText.isEmpty()) {
            displayText = "0";
            cursorPosition = 1;
        }
        updateValueFromText();
    }

    private void copySelection() {
        if (!hasSelection()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(displayText);
        } else {
            Minecraft.getInstance().keyboardHandler.setClipboard(displayText.substring(selStart(), selEnd()));
        }
    }

    private void pasteFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        String digits = clip.replaceAll("[^0-9-]", "");
        if (digits.isEmpty()) return;
        if (hasSelection()) {
            int start = selStart();
            int end = selEnd();
            displayText = displayText.substring(0, start) + digits + displayText.substring(end);
            cursorPosition = start + digits.length();
            selectionStart = -1;
        } else {
            displayText = displayText.substring(0, cursorPosition) + digits + displayText.substring(cursorPosition);
            cursorPosition += digits.length();
        }
        updateValueFromText();
    }

    private void adjustValue(long delta) {
        long newValue = clampValue(committedValue + delta);
        if (newValue != committedValue) {
            setValue(newValue);
        }
    }

    private long clampValue(long val) {
        return Math.max(minValue, Math.min(maxValue, val));
    }

    private void openCalculator(int mouseX, int mouseY) {
        if (onCalculatorOpen != null) {
            onCalculatorOpen.open(this, mouseX, mouseY);
        }
    }

    private void notifyValueChanged() {
        if (onValueChanged != null) {
            onValueChanged.accept(value);
        }
    }

    private void commitOrRollback() {
        if (displayText.isEmpty() || displayText.equals("-")) {
            rollbackToCommitted();
            return;
        }
        try {
            long parsed = Long.parseLong(displayText);
            if (parsed < minValue || parsed > maxValue) {
                rollbackToCommitted();
                return;
            }
            if (parsed != committedValue) {
                value = parsed;
                committedValue = parsed;
                displayText = String.valueOf(parsed);
                cursorPosition = displayText.length();
                rangeWarning = false;
                notifyValueChanged();
                return;
            }
            displayText = String.valueOf(committedValue);
            cursorPosition = displayText.length();
            rangeWarning = false;
        } catch (NumberFormatException e) {
            rollbackToCommitted();
        }
    }

    private void rollbackToCommitted() {
        value = committedValue;
        displayText = String.valueOf(committedValue);
        cursorPosition = displayText.length();
        selectionStart = -1;
        scrollOffset = 0;
        rangeWarning = false;
    }

    public long getValue() {
        return value;
    }

    public boolean hasRangeWarning() {
        return rangeWarning;
    }

    public long minValue() {
        return minValue;
    }

    public long maxValue() {
        return maxValue;
    }

    public long scrollStep() {
        return scrollStep;
    }

    public NumberInputWidget setValue(long value) {
        this.value = clampValue(value);
        this.committedValue = this.value;
        this.displayText = String.valueOf(this.value);
        this.cursorPosition = this.displayText.length();
        this.rangeWarning = false;
        notifyValueChanged();
        return this;
    }

    public NumberInputWidget setMinValue(long minValue) {
        this.minValue = minValue;
        if (maxValue < this.minValue) {
            maxValue = this.minValue;
        }
        if (value < minValue) {
            setValue(minValue);
        }
        return this;
    }

    public NumberInputWidget setMaxValue(long maxValue) {
        this.maxValue = maxValue;
        if (minValue > this.maxValue) {
            minValue = this.maxValue;
        }
        if (value > maxValue) {
            setValue(maxValue);
        }
        return this;
    }

    public NumberInputWidget setRange(long min, long max) {
        this.minValue = Math.min(min, max);
        this.maxValue = Math.max(min, max);
        setValue(clampValue(value));
        return this;
    }

    public NumberInputWidget setScrollStep(long step) {
        this.scrollStep = step;
        return this;
    }

    public NumberInputWidget setScrollEnabled(boolean enabled) {
        this.scrollEnabled = enabled;
        return this;
    }

    public NumberInputWidget onValueChanged(LongConsumer callback) {
        this.onValueChanged = callback;
        return this;
    }

    public NumberInputWidget onCalculatorOpen(@Nullable CalculatorOpenCallback callback) {
        this.onCalculatorOpen = callback;
        this.defaultCalculatorPopupEnabled = false;
        return this;
    }

    public NumberInputWidget enableCalculatorPopup() {
        this.onCalculatorOpen = NumberInputCalculatorPopup::open;
        this.defaultCalculatorPopupEnabled = true;
        return this;
    }

    public NumberInputWidget tooltip(@Nullable Supplier<Tooltip> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    public NumberInputWidget tooltipWithCalculatorPopup(@Nullable Supplier<Tooltip> tooltipSupplier) {
        this.tooltipSupplier = () -> {
            Tooltip supplied = tooltipSupplier == null ? null : tooltipSupplier.get();
            Tooltip tooltip = supplied == null ? Tooltip.create() : supplied.copy();
            if (tooltip.notEmpty()) {
                tooltip.add(Component.empty());
            }
            tooltip.add(CalculatorInputKeyMappings.openAmountCalculatorPopupUsage(CirrusLangs.Calc.amountMiddleClick.component()));
            return tooltip;
        };
        return this;
    }

    public boolean debugCalculatorPopupEnabled() {
        return defaultCalculatorPopupEnabled;
    }

    public boolean debugCalculatorPopupOpen() {
        return NumberInputCalculatorPopup.isOpen(this);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);

        var font = context().font();
        int textColor = rangeWarning ? 0xFFF36D6D : focused() ? 0xFFFFFFFF : 0xFFE0E0E0;
        int padding = 4;
        int textY = (height() - font.lineHeight) / 2 + 1;
        int visibleWidth = width() - padding * 2;

        String text = displayText.isEmpty() ? "0" : displayText;

        String beforeCursor = displayText.substring(0, cursorPosition);
        int cursorX = font.width(beforeCursor);
        if (cursorX - scrollOffset > visibleWidth - 2) {
            scrollOffset = cursorX - visibleWidth + 2;
        } else if (cursorX - scrollOffset < 0) {
            scrollOffset = cursorX;
        }
        int textWidth = font.width(text);
        if (textWidth - scrollOffset < visibleWidth && scrollOffset > 0) {
            scrollOffset = Math.max(0, textWidth - visibleWidth);
        }

        int absX = absolutePos().x();
        int absY = absolutePos().y();
        if (rangeWarning) {
            canvas.fill(1, 1, Math.max(0, width() - 2), Math.max(0, height() - 2), 0x26F36D6D);
        }
        canvas.pushClip(absX + padding, absY, visibleWidth, height());

        canvas.text(text, padding - scrollOffset, textY, textColor, false);

        if (focused() && ((System.currentTimeMillis() - cursorBlinkEpoch) / 500) % 2 == 0) {
            int cx = padding + cursorX - scrollOffset;
            canvas.fill(cx, textY - 1, 1, font.lineHeight + 2, 0xFFFFFFFF);
        }

        if (focused() && hasSelection()) {
            int x1 = padding + font.width(displayText.substring(0, selStart())) - scrollOffset;
            int x2 = padding + font.width(displayText.substring(0, selEnd())) - scrollOffset;
            canvas.fill(x1, textY - 1, x2 - x1, font.lineHeight + 2, 0x600000FF);
        }

        canvas.popClip();
    }


}
