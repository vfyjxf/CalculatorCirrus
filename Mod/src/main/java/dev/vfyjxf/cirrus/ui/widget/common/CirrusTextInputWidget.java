package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

public class CirrusTextInputWidget extends Widget {
    private static final int paddingLeft = 4;
    private static final int paddingRight = 4;

    private String text = "";
    private String placeholder = "";
    private int cursorPosition;
    private int selectionStart = -1;
    private int scrollOffset;
    private int maxLength = 64;
    private long cursorBlinkEpoch = System.currentTimeMillis();
    private @Nullable Consumer<String> onTextChanged;
    private @Nullable Consumer<String> onEnterPressed;
    private @Nullable Supplier<Tooltip> tooltipSupplier;
    private @Nullable WorkbenchThemeMode appliedTheme;

    public CirrusTextInputWidget() {
        setFocusable(true);
        addStyleClass("cirrus-input");
        useStyle(heightOf(18), minWidth(0));
        syncDefaultBackground(true);
        onMount(
            (scene, context, handle) -> CirrusUiTheme
                    .bindThemeListener(this, handle, () -> syncDefaultBackground(false))
        );
        setupEvents();
    }

    private void syncDefaultBackground(boolean force) {
        WorkbenchThemeMode mode = CirrusUiTheme.mode(this);
        if (!force && mode == appliedTheme) {
            return;
        }
        appliedTheme = mode;
        defaultStyle(UIStyle.of(background(CirrusUiTheme.textures(this).recessed())));
    }

    public String text() {
        return text;
    }

    public CirrusTextInputWidget setText(@Nullable String text) {
        this.text = sanitize(text);
        cursorPosition = Math.min(cursorPosition, this.text.length());
        clearSelection();
        return this;
    }

    public String placeholder() {
        return placeholder;
    }

    public CirrusTextInputWidget setPlaceholder(@Nullable String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public CirrusTextInputWidget setPlaceholder(@Nullable LangEntry placeholder, Object... args) {
        this.placeholder = placeholder == null ? "" : placeholder.string(args);
        return this;
    }

    public int maxLength() {
        return maxLength;
    }

    public CirrusTextInputWidget maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        if (text.length() > this.maxLength) {
            setText(text.substring(0, this.maxLength));
        }
        return this;
    }

    public CirrusTextInputWidget onTextChanged(@Nullable Consumer<String> callback) {
        this.onTextChanged = callback;
        return this;
    }

    public CirrusTextInputWidget onEnterPressed(@Nullable Consumer<String> callback) {
        this.onEnterPressed = callback;
        return this;
    }

    public CirrusTextInputWidget tooltip(@Nullable Supplier<Tooltip> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    private void setupEvents() {
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            cursorPosition = cursorAt((int) input.mouseX());
            clearSelection();
            cursorBlinkEpoch = System.currentTimeMillis();
            return EventDispatch.consumed;
        });

        onKeyPressed((input, context) -> {
            if (!focused()) {
                return EventDispatch.pass;
            }
            return handleKey(input);
        });

        onCharTyped((codePoint, modifiers, context) -> {
            if (!focused() || Character.isISOControl(codePoint)) {
                return EventDispatch.pass;
            }
            insert(Character.toString(codePoint));
            return EventDispatch.consumed;
        });

        onHoverTooltip((mouseX, mouseY) -> tooltipSupplier == null ? null : tooltipSupplier.get());
    }

    private EventDispatch handleKey(InputContext input) {
        return handleKeyInput(input.key().getValue(), input.isCtrlDown());
    }

    EventDispatch debugHandleKeyInput(int key, boolean ctrl) {
        return handleKeyInput(key, ctrl);
    }

    void debugInsertText(String value) {
        insert(value);
    }

    int debugCursorPosition() {
        return cursorPosition;
    }

    int debugSelectionStart() {
        return selectionStart;
    }

    String debugSelectedText() {
        return selectedText();
    }

    private EventDispatch handleKeyInput(int key, boolean ctrl) {
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    selectAll();
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_C -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedTextOrAll());
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_X -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedTextOrAll());
                    if (hasSelection()) {
                        deleteSelection();
                    } else {
                        replaceText("", 0);
                    }
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        insert(clip.replace('\n', ' ').replace('\r', ' '));
                    }
                    return EventDispatch.consumed;
                }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    replaceText(
                        text.substring(0, cursorPosition - 1) + text.substring(cursorPosition),
                        cursorPosition - 1
                    );
                }
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < text.length()) {
                    replaceText(text.substring(0, cursorPosition) + text.substring(cursorPosition + 1), cursorPosition);
                }
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursorPosition = hasSelection() ? selectionStart() : Math.max(0, cursorPosition - 1);
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursorPosition = hasSelection() ? selectionEnd() : Math.min(text.length(), cursorPosition + 1);
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0;
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPosition = text.length();
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onEnterPressed != null) {
                    onEnterPressed.accept(text);
                }
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                scene().requestUnfocus(this);
                return EventDispatch.consumed;
            }
        }
        return EventDispatch.pass;
    }

    private void insert(String value) {
        if (value.isEmpty()) {
            return;
        }
        int start = hasSelection() ? selectionStart() : cursorPosition;
        int end = hasSelection() ? selectionEnd() : cursorPosition;
        String next = text.substring(0, start) + value + text.substring(end);
        if (next.length() > maxLength) {
            next = next.substring(0, maxLength);
        }
        replaceText(next, Math.min(start + value.length(), next.length()));
    }

    private void replaceText(String value, int newCursorPosition) {
        text = sanitize(Objects.requireNonNull(value, "value"));
        cursorPosition = Math.max(0, Math.min(newCursorPosition, text.length()));
        clearSelection();
        cursorBlinkEpoch = System.currentTimeMillis();
        if (onTextChanged != null) {
            onTextChanged.accept(text);
        }
    }

    private void selectAll() {
        selectionStart = 0;
        cursorPosition = text.length();
        cursorBlinkEpoch = System.currentTimeMillis();
    }

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionStart != cursorPosition;
    }

    private int selectionStart() {
        return Math.min(selectionStart, cursorPosition);
    }

    private int selectionEnd() {
        return Math.max(selectionStart, cursorPosition);
    }

    private void clearSelection() {
        selectionStart = -1;
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        int start = selectionStart();
        int end = selectionEnd();
        replaceText(text.substring(0, start) + text.substring(end), start);
    }

    private String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        return text.substring(selectionStart(), selectionEnd());
    }

    private String selectedTextOrAll() {
        return hasSelection() ? selectedText() : text;
    }

    private String sanitize(@Nullable String value) {
        String out = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return out.length() <= maxLength ? out : out.substring(0, maxLength);
    }

    private int cursorAt(int sceneMouseX) {
        Font font = context().font();
        int clickX = sceneMouseX - absolutePos().x() - paddingLeft + scrollOffset;
        int best = text.length();
        for (int i = 0; i <= text.length(); i++) {
            int charX = font.width(text.substring(0, i));
            if (charX > clickX) {
                if (i > 0) {
                    int previous = font.width(text.substring(0, i - 1));
                    best = clickX - previous < charX - clickX ? i - 1 : i;
                } else {
                    best = 0;
                }
                break;
            }
        }
        return Math.max(0, Math.min(best, text.length()));
    }

    public Rect localTextClipRect() {
        int visibleWidth = Math.max(0, width() - paddingLeft - paddingRight);
        return new Rect(paddingLeft, 0, visibleWidth, height());
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette palette = CirrusUiTheme.palette(this);
        canvas.texture(CirrusUiTheme.textures(this).recessed(), 0, 0, width(), height());
        if (focused()) {
            canvas.strokeRect(0, 0, width(), height(), palette.focusRing(), 1);
        }

        Font font = context().font();
        int textY = CirrusControlChrome.centeredTextY(0, height(), font);
        Rect clip = localTextClipRect();
        int visibleWidth = clip.width();
        syncScroll(font, visibleWidth);

        canvas.pushClip(clip.x(), clip.y(), clip.width(), clip.height());
        if (text.isEmpty() && !placeholder.isEmpty() && !focused()) {
            canvas.text(
                Component.literal(fit(font, placeholder, visibleWidth)),
                paddingLeft,
                textY,
                palette.textMuted(),
                false
            );
        } else {
            renderSelection(canvas, font, textY, palette);
            canvas.text(text, paddingLeft - scrollOffset, textY, palette.textPrimary(), false);
            renderCursor(canvas, font, textY, palette);
        }
        canvas.popClip();
    }

    private void syncScroll(Font font, int visibleWidth) {
        String beforeCursor = text.substring(0, cursorPosition);
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
    }

    private void renderCursor(SceneCanvas canvas, Font font, int textY, CirrusPalette palette) {
        if (!focused() || ((System.currentTimeMillis() - cursorBlinkEpoch) / 500) % 2 != 0) {
            return;
        }
        int cursorX = paddingLeft + font.width(text.substring(0, cursorPosition)) - scrollOffset;
        canvas.fill(cursorX, textY - 1, 1, font.lineHeight + 2, palette.focusRing());
    }

    private void renderSelection(SceneCanvas canvas, Font font, int textY, CirrusPalette palette) {
        if (!focused() || !hasSelection()) {
            return;
        }
        int x1 = paddingLeft + font.width(text.substring(0, selectionStart())) - scrollOffset;
        int x2 = paddingLeft + font.width(text.substring(0, selectionEnd())) - scrollOffset;
        canvas.fill(
            x1,
            textY - 1,
            Math.max(1, x2 - x1),
            font.lineHeight + 2,
            (palette.focusRing() & 0x00FFFFFF) | 0x55000000
        );
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        if (maxWidth <= font.width("...")) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")).stripTrailing() + "...";
    }
}
