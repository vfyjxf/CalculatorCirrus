package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CoordinateSpace;
import dev.vfyjxf.cloudlib.api.ui.base.SceneLayer;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayDefault;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class SearchBoxWidget extends Widget {
    private static final int paddingLeft = 15;
    private static final int paddingRight = 4;
    private static final int maxSuggestionCandidates = 48;
    private static final int maxVisibleSuggestions = 8;
    private static final int suggestionRowHeight = 20;
    private static final int suggestionGap = 2;
    private static final int suggestionPopupZIndex = 2400;
    private static final int suggestionPopupPadding = 2;
    private static final int suggestionPopupMinWidth = 64;

    private String text = "";
    private String placeholder = "";
    private int cursorPosition;
    private int selectionStart = -1;
    private int scrollOffset;
    private int selectedSuggestion;
    private int suggestionScrollIndex;
    private long cursorBlinkEpoch = System.currentTimeMillis();
    private List<SearchHighlightSegment> highlightedSegments = List.of();
    private List<SearchSuggestion> suggestions = List.of();
    private int suggestionVersion;
    private Function<String, List<SearchHighlightSegment>> highlighter = RecipeSearchHighlighter::highlight;
    private SearchSuggestionSource suggestionSource = SearchSuggestionSource.syntaxOnly();
    private @Nullable Consumer<String> onQueryChanged;
    private @Nullable Consumer<String> onEnterPressed;
    private @Nullable Supplier<Tooltip> tooltipSupplier;
    private @Nullable Supplier<Rect> suggestionViewportSupplier;
    private @Nullable SuggestionPopup suggestionPopup;
    private boolean closeSuggestionPopupScheduled;
    private @Nullable WorkbenchThemeMode appliedTheme;

    public SearchBoxWidget() {
        setFocusable(true);
        setClickGroup(this);
        setTickable(true);
        addStyleClass("cirrus-input");
        useStyle(heightOf(18), minWidth(0));
        syncDefaultBackground(true);
        onMount(
            (scene, context, handle) -> CirrusUiTheme
                    .bindThemeListener(this, handle, () -> syncDefaultBackground(false))
        );
        refreshDerivedState();
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

    private void setupEvents() {
        onMouseClick((input, clickCount, context) -> {
            if (input.isRightClick()) {
                if (!text.isEmpty()) {
                    replaceText("");
                    CirrusUiSound.playClickSound();
                } else {
                    clearSelection();
                }
                cursorBlinkEpoch = System.currentTimeMillis();
                syncSuggestionPopup();
                context.consume();
                return EventDispatch.consumed;
            }
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            cursorPosition = cursorAt((int) input.mouseX());
            clearSelection();
            cursorBlinkEpoch = System.currentTimeMillis();
            syncSuggestionPopup();
            return EventDispatch.consumed;
        });

        onKeyPressed((input, context) -> {
            if (!focused()) {
                return EventDispatch.pass;
            }
            return handleKey(input);
        });

        onCharTyped((codePoint, modifiers, context) -> {
            if (!focused()) {
                return EventDispatch.pass;
            }
            if (Character.isISOControl(codePoint)) {
                return EventDispatch.pass;
            }
            insert(Character.toString(codePoint));
            return EventDispatch.consumed;
        });

        onFocusLost(context -> {
            selectedSuggestion = 0;
            scheduleCloseSuggestionPopup();
        });
        onUnmount(this::closeSuggestionPopupNow);
        onTick(this::syncSuggestionPopup);
        onHoverTooltip((mouseX, mouseY) -> tooltipSupplier == null ? null : tooltipSupplier.get());
    }

    public String text() {
        return text;
    }

    public SearchBoxWidget setText(String text) {
        String value = text == null ? "" : text;
        this.text = value;
        cursorPosition = Math.min(cursorPosition, this.text.length());
        clearSelection();
        refreshDerivedState();
        return this;
    }

    public SearchBoxWidget setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public SearchBoxWidget setPlaceholder(LangEntry placeholder, Object... args) {
        this.placeholder = placeholder == null ? "" : placeholder.string(args);
        return this;
    }

    public SearchBoxWidget onQueryChanged(@Nullable Consumer<String> callback) {
        this.onQueryChanged = callback;
        return this;
    }

    public SearchBoxWidget onEnterPressed(@Nullable Consumer<String> callback) {
        this.onEnterPressed = callback;
        return this;
    }

    public SearchBoxWidget tooltip(@Nullable Supplier<Tooltip> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    public SearchBoxWidget suggestionViewport(@Nullable Supplier<Rect> viewportSupplier) {
        this.suggestionViewportSupplier = viewportSupplier;
        syncSuggestionPopup();
        return this;
    }

    public SearchBoxWidget suggestionSource(@Nullable SearchSuggestionSource source) {
        this.suggestionSource = source == null ? SearchSuggestionSource.syntaxOnly() : source;
        refreshDerivedState();
        return this;
    }

    public SearchBoxWidget highlighter(@Nullable Function<String, List<SearchHighlightSegment>> highlighter) {
        this.highlighter = highlighter == null ? RecipeSearchHighlighter::highlight : highlighter;
        refreshDerivedState();
        return this;
    }

    public List<SearchHighlightSegment> debugHighlightedSegments() {
        return highlightedSegments;
    }

    public int debugSuggestionCount() {
        return suggestions.size();
    }

    public int debugSelectedSuggestionIndex() {
        return selectedSuggestion;
    }

    public boolean debugSuggestionPopupOpen() {
        return suggestionPopup != null;
    }

    public int debugVisibleSuggestionRows() {
        return visibleSuggestionRowsForPopup();
    }

    public static int suggestionPopupZIndex() {
        return suggestionPopupZIndex;
    }

    public static int suggestionPopupHeightForCount(int count) {
        return Math.max(suggestionRowHeight, visibleSuggestionRows(count) * suggestionRowHeight)
                + suggestionPopupPadding * 2;
    }

    public static int suggestionPopupHeightForRows(int rows) {
        return Math.max(suggestionRowHeight, Math.max(1, rows) * suggestionRowHeight) + suggestionPopupPadding * 2;
    }

    public static int visibleSuggestionRowsForAvailableHeight(int count, int availableHeight) {
        int rows = visibleSuggestionRows(count);
        int availableRows = Math.max(0, (availableHeight - suggestionPopupPadding * 2) / suggestionRowHeight);
        return Math.min(rows, availableRows);
    }

    public static int maxVisibleSuggestions() {
        return maxVisibleSuggestions;
    }

    public void debugCommitSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) {
            return;
        }
        commitSuggestion(index);
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
                        replaceText("");
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
                refreshDerivedState();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursorPosition = hasSelection() ? selectionEnd() : Math.min(text.length(), cursorPosition + 1);
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                refreshDerivedState();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0;
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                refreshDerivedState();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPosition = text.length();
                clearSelection();
                cursorBlinkEpoch = System.currentTimeMillis();
                refreshDerivedState();
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveSuggestion(-1);
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveSuggestion(1);
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_TAB -> {
                commitSuggestion(selectedSuggestion);
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!suggestions.isEmpty()) {
                    commitSuggestion(selectedSuggestion);
                } else if (onEnterPressed != null) {
                    onEnterPressed.accept(text);
                }
                return EventDispatch.consumed;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (!suggestions.isEmpty()) {
                    suggestions = List.of();
                    selectedSuggestion = 0;
                    scheduleCloseSuggestionPopup();
                    return EventDispatch.consumed;
                }
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
        replaceText(text.substring(0, start) + value + text.substring(end), start + value.length());
    }

    private void replaceText(String value) {
        replaceText(value, value.length());
    }

    private void replaceText(String value, int newCursor) {
        text = Objects.requireNonNull(value, "value");
        cursorPosition = Math.max(0, Math.min(newCursor, text.length()));
        clearSelection();
        cursorBlinkEpoch = System.currentTimeMillis();
        refreshDerivedState();
        notifyQueryChanged();
    }

    private void selectAll() {
        selectionStart = 0;
        cursorPosition = text.length();
        cursorBlinkEpoch = System.currentTimeMillis();
        refreshDerivedState();
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

    private void notifyQueryChanged() {
        if (onQueryChanged != null) {
            onQueryChanged.accept(text);
        }
    }

    private void commitSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) {
            return;
        }
        SearchSuggestion suggestion = suggestions.get(index);
        String applied = suggestion.applyTo(text);
        int newCursor = suggestion.replaceStart() + suggestion.replacement().length();
        if (!suggestion.replacement().endsWith(":") && !suggestion.replacement().endsWith(" ")) {
            newCursor++;
        }
        replaceText(applied, Math.min(newCursor, applied.length()));
    }

    private void moveSuggestion(int delta) {
        if (suggestions.isEmpty()) {
            return;
        }
        selectedSuggestion = Math.floorMod(selectedSuggestion + delta, suggestions.size());
        revealSelectedSuggestion();
        if (suggestionPopup != null) {
            suggestionPopup.refresh();
        }
    }

    private String suggestionTypedPrefix = "";

    /** The word being completed at the cursor, for emphasizing the matching part of suggestion rows. */
    private String typedCompletionPrefix() {
        String upToCursor = text.substring(0, Math.min(cursorPosition, text.length()));
        int start = upToCursor.length();
        while (start > 0 && !Character.isWhitespace(upToCursor.charAt(start - 1))) {
            start--;
        }
        return upToCursor.substring(start);
    }

    private void refreshDerivedState() {
        suggestionTypedPrefix = typedCompletionPrefix();
        List<SearchHighlightSegment> nextHighlights = highlighter.apply(text);
        highlightedSegments = nextHighlights == null ? List.of() : nextHighlights;
        List<SearchSuggestion> nextSuggestions = suggestionSource
                .suggest(text, cursorPosition, maxSuggestionCandidates);
        if (!nextSuggestions.equals(suggestions)) {
            suggestions = nextSuggestions;
            suggestionVersion++;
        }
        if (selectedSuggestion >= suggestions.size()) {
            selectedSuggestion = 0;
        }
        suggestionScrollIndex = clampSuggestionScroll(suggestionScrollIndex);
        revealSelectedSuggestion();
        syncSuggestionPopup();
    }

    private void scrollSuggestions(int delta) {
        int rows = visibleSuggestionRowsForPopup();
        if (rows <= 0 || suggestions.size() <= rows || delta == 0) {
            return;
        }
        suggestionScrollIndex = clampSuggestionScroll(suggestionScrollIndex + delta);
        if (selectedSuggestion < suggestionScrollIndex) {
            selectedSuggestion = suggestionScrollIndex;
        } else if (selectedSuggestion >= suggestionScrollIndex + rows) {
            selectedSuggestion = suggestionScrollIndex + rows - 1;
        }
        if (suggestionPopup != null) {
            suggestionPopup.refresh();
        }
    }

    private void revealSelectedSuggestion() {
        if (suggestions.isEmpty()) {
            suggestionScrollIndex = 0;
            return;
        }
        int rows = visibleSuggestionRowsForPopup();
        if (rows <= 0) {
            suggestionScrollIndex = 0;
            return;
        }
        if (selectedSuggestion < suggestionScrollIndex) {
            suggestionScrollIndex = selectedSuggestion;
        } else if (selectedSuggestion >= suggestionScrollIndex + rows) {
            suggestionScrollIndex = selectedSuggestion - rows + 1;
        }
        suggestionScrollIndex = clampSuggestionScroll(suggestionScrollIndex);
    }

    private int clampSuggestionScroll(int value) {
        return Math.max(0, Math.min(value, Math.max(0, suggestions.size() - visibleSuggestionRowsForPopup())));
    }

    private int visibleSuggestionRowsForPopup() {
        if (suggestions.isEmpty()) {
            return 0;
        }
        Rect viewport = suggestionViewport();
        if (viewport == null) {
            return visibleSuggestionRows(suggestions.size());
        }
        Rect anchor = anchorRect();
        if (!anchorVisibleInViewport(anchor, viewport)) {
            return 0;
        }
        int availableHeight = Math.max(availableBelow(anchor, viewport), availableAbove(anchor, viewport));
        return visibleSuggestionRowsForAvailableHeight(suggestions.size(), availableHeight);
    }

    private @Nullable Rect suggestionViewport() {
        if (suggestionViewportSupplier == null) {
            return null;
        }
        Rect viewport = suggestionViewportSupplier.get();
        if (viewport == null || viewport.width() <= 0 || viewport.height() <= 0) {
            return null;
        }
        return viewport;
    }

    private Rect anchorRect() {
        return new Rect(absolutePos().x(), absolutePos().y(), width(), height());
    }

    private static boolean anchorVisibleInViewport(Rect anchor, Rect viewport) {
        return anchor.width() > 0
                && anchor.height() > 0
                && anchor.y() >= viewport.y()
                && anchor.bottom() <= viewport.bottom()
                && anchor.right() > viewport.x()
                && anchor.x() < viewport.right();
    }

    private static int availableBelow(Rect anchor, Rect viewport) {
        return Math.max(0, viewport.bottom() - anchor.bottom() - suggestionGap);
    }

    private static int availableAbove(Rect anchor, Rect viewport) {
        return Math.max(0, anchor.y() - viewport.y() - suggestionGap);
    }

    private static int visibleSuggestionRows(int count) {
        return Math.min(maxVisibleSuggestions, Math.max(1, Math.max(0, count)));
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
        boolean dark = CirrusUiTheme.isDark(this);
        canvas.texture(CirrusUiTheme.textures(this).recessed(), 0, 0, width(), height());
        if (focused()) {
            canvas.strokeRect(0, 0, width(), height(), palette.focusRing(), 1);
        }
        int iconY = Math.max(0, (height() - 10) / 2);
        var searchIcon = CirrusThemeIcons.resolve(CirrusTextures.Icon.search, this);
        canvas.texture(searchIcon, 3, iconY, 10, 10);

        Font font = context().font();
        int textY = (height() - font.lineHeight) / 2 + 1;
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
            renderHighlightedText(canvas, font, textY, palette, dark);
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

    /** Committed search words render in their own color, not plain text color. */
    private static final int searchWordColor = 0xFF3D7AB8;
    /** Accent color of the word at the cursor — the same color emphasizes the typed prefix in suggestion rows. */
    private static final int activeTokenColor = 0xFF2E86C1;

    private void renderHighlightedText(SceneCanvas canvas, Font font, int textY, CirrusPalette palette, boolean dark) {
        int activeStart = -1;
        int activeEnd = -1;
        if (focused()) {
            int position = Math.min(cursorPosition, text.length());
            activeStart = position;
            while (activeStart > 0 && !Character.isWhitespace(text.charAt(activeStart - 1))) {
                activeStart--;
            }
            activeEnd = position;
            while (activeEnd < text.length() && !Character.isWhitespace(text.charAt(activeEnd))) {
                activeEnd++;
            }
            if (activeEnd <= activeStart) {
                activeStart = -1;
            }
        }
        boolean segmentsCurrent = !highlightedSegments.isEmpty()
                && highlightedSegments.getFirst().start() == 0
                && highlightedSegments.getLast().end() == text.length();
        if (!segmentsCurrent) {
            canvas.text(text, paddingLeft - scrollOffset, textY, palette.textPrimary(), false);
            return;
        }
        int wordColor = dark ? palette.costP() : searchWordColor;
        int activeColor = dark ? palette.focusRing() : activeTokenColor;
        int x = paddingLeft - scrollOffset;
        for (SearchHighlightSegment segment : highlightedSegments) {
            int from = segment.start();
            int to = segment.end();
            while (from < to) {
                int color = segment.kind() == SearchHighlightKind.text
                        ? wordColor
                        : highlightColorFor(segment.kind(), palette, dark);
                int end = to;
                if (activeStart >= 0) {
                    if (from < activeStart) {
                        end = Math.min(to, activeStart);
                    } else if (from < activeEnd) {
                        color = activeColor;
                        end = Math.min(to, activeEnd);
                    }
                }
                String piece = text.substring(from, end);
                canvas.text(piece, x, textY, color, false);
                x += font.width(piece);
                from = end;
            }
        }
        if (activeStart >= 0) {
            int activeX = paddingLeft - scrollOffset + font.width(text.substring(0, activeStart));
            int activeW = font.width(text.substring(activeStart, activeEnd));
            canvas.fill(activeX, textY + font.lineHeight - 1, activeW, 1, activeColor);
        }
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

    private void renderCursor(SceneCanvas canvas, Font font, int textY, CirrusPalette palette) {
        if (!focused()) {
            return;
        }
        if (((System.currentTimeMillis() - cursorBlinkEpoch) / 500) % 2 != 0) {
            return;
        }
        int cursorX = paddingLeft + font.width(text.substring(0, cursorPosition)) - scrollOffset;
        canvas.fill(cursorX, textY - 1, 1, font.lineHeight + 2, palette.focusRing());
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        if (maxWidth <= font.width("...")) {
            return "";
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))).stripTrailing() + "...";
    }

    public static int highlightColorFor(SearchHighlightKind kind) {
        return highlightColorFor(kind, CirrusUiTheme.palette(null), false);
    }

    static int highlightColorFor(SearchHighlightKind kind, CirrusPalette palette, boolean dark) {
        if (dark) {
            return switch (kind) {
                case field -> palette.focusRing();
                case prefix -> palette.costS();
                case operator -> palette.costH();
                case keyword -> palette.costC();
                case negation -> palette.ruleHardBan();
                case quote -> palette.textSecondary();
                case error -> palette.advisoryLastResort();
                case text -> palette.textPrimary();
            };
        }
        return switch (kind) {
            case field -> 0xFF0B5CAD;
            case prefix -> 0xFF00796B;
            case operator -> 0xFF6A4A00;
            case keyword -> 0xFF6D3FB1;
            case negation -> 0xFFB3261E;
            case quote -> 0xFF5C6470;
            case error -> 0xFFD11F2A;
            case text -> palette.textPrimary();
        };
    }

    private void syncSuggestionPopup() {
        if (!focused()
                || suggestions.isEmpty()
                || width() <= 0
                || !lifecycle().mounted()
                || visibleSuggestionRowsForPopup() <= 0) {
            scheduleCloseSuggestionPopup();
            return;
        }
        if (suggestionPopup == null) {
            openSuggestionPopup();
        } else {
            suggestionPopup.syncRows();
        }
    }

    private void openSuggestionPopup() {
        if (suggestionPopup != null || suggestions.isEmpty() || !lifecycle().mounted()) {
            return;
        }
        SuggestionPopup next = new SuggestionPopup();
        next.setClickGroup(this);
        addRootWidget(scene().root(), next);
        suggestionPopup = next;
    }

    private void scheduleCloseSuggestionPopup() {
        if (suggestionPopup == null || closeSuggestionPopupScheduled) {
            return;
        }
        if (!lifecycle().mounted()) {
            closeSuggestionPopupNow();
            return;
        }
        closeSuggestionPopupScheduled = true;
        scene().nextTick(() -> {
            closeSuggestionPopupScheduled = false;
            closeSuggestionPopupNow();
        });
    }

    private void closeSuggestionPopupNow() {
        SuggestionPopup active = suggestionPopup;
        suggestionPopup = null;
        closeSuggestionPopupScheduled = false;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    private PopupLayout popupLayout(int rows) {
        int popupWidth = Math.max(suggestionPopupMinWidth, width());
        int popupHeight = suggestionPopupHeightForRows(rows);
        Rect anchor = anchorRect();
        Rect viewport = suggestionViewport();
        int x = anchor.x();
        int y = anchor.bottom() + suggestionGap;
        if (viewport != null) {
            int availableBelow = availableBelow(anchor, viewport);
            int availableAbove = availableAbove(anchor, viewport);
            if (availableBelow < popupHeight && availableAbove > availableBelow) {
                y = anchor.y() - suggestionGap - popupHeight;
            }
            x = clamp(x, viewport.x(), Math.max(viewport.x(), viewport.right() - popupWidth));
            y = clamp(y, viewport.y(), Math.max(viewport.y(), viewport.bottom() - popupHeight));
        }
        return new PopupLayout(x, y, popupWidth, popupHeight);
    }

    private PopupLayout popupLayout() {
        return popupLayout(visibleSuggestionRowsForPopup());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private record PopupLayout(int x, int y, int width, int height) {}

    private final class SuggestionPopup extends CompositeWidget<Widget> {
        private final ArrayList<SuggestionRow> rows = new ArrayList<>();
        private int rowCount = -1;
        private int popupWidth = -1;
        private int popupHeight = -1;
        private int popupX = Integer.MIN_VALUE;
        private int popupY = Integer.MIN_VALUE;
        private int renderedScrollIndex = -1;
        private int renderedSuggestionVersion = -1;

        private SuggestionPopup() {
            setCoordinateSpace(CoordinateSpace.scene);
            setSceneLayer(SceneLayer.floating);
            useStyle(
                positionAbsolute(),
                flexColumn(),
                minWidth(0),
                padding(suggestionPopupPadding),
                boxSizingBorderBox(),
                UIStyles.zIndex(suggestionPopupZIndex)
            );
            onLayout((widget, scope) -> {
                PopupLayout layout = popupLayout();
                scope.setBounds(layout.x(), layout.y(), layout.width(), layout.height());
            });
            onClickOutside(context -> scheduleCloseSuggestionPopup());
            onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
                int rows = visibleSuggestionRowsForPopup();
                if (rows <= 0 || suggestions.size() <= rows || scrollY == 0) {
                    return EventDispatch.pass;
                }
                scrollSuggestions(scrollY > 0 ? -1 : 1);
                return EventDispatch.consumed;
            });
            for (int i = 0; i < maxVisibleSuggestions; i++) {
                SuggestionRow row = new SuggestionRow();
                rows.add(row);
                addWidget(row);
            }
            refresh();
        }

        private void syncRows() {
            int nextRowCount = visibleSuggestionRowsForPopup();
            PopupLayout nextLayout = popupLayout(nextRowCount);
            int nextScrollIndex = clampSuggestionScroll(suggestionScrollIndex);
            if (nextLayout.width() == popupWidth
                    && nextLayout.height() == popupHeight
                    && nextLayout.x() == popupX
                    && nextLayout.y() == popupY
                    && nextRowCount == rowCount
                    && nextScrollIndex == renderedScrollIndex
                    && suggestionVersion == renderedSuggestionVersion) {
                return;
            }
            refresh(nextRowCount, nextLayout);
        }

        private void refresh() {
            int nextRowCount = visibleSuggestionRowsForPopup();
            refresh(nextRowCount, popupLayout(nextRowCount));
        }

        private void refresh(int nextRowCount, PopupLayout nextLayout) {
            popupWidth = nextLayout.width();
            popupHeight = nextLayout.height();
            popupX = nextLayout.x();
            popupY = nextLayout.y();
            rowCount = nextRowCount;
            suggestionScrollIndex = clampSuggestionScroll(suggestionScrollIndex);
            renderedScrollIndex = suggestionScrollIndex;
            renderedSuggestionVersion = suggestionVersion;
            setPos(popupX, popupY);
            setSize(popupWidth, popupHeight);
            useStyle(
                positionAbsolute(),
                sizeOf(popupWidth, popupHeight),
                flexColumn(),
                padding(suggestionPopupPadding),
                boxSizingBorderBox(),
                UIStyles.zIndex(suggestionPopupZIndex)
            );
            int rowWidth = Math.max(0, popupWidth - suggestionPopupPadding * 2);
            for (int i = 0; i < rows.size(); i++) {
                int suggestionIndex = renderedScrollIndex + i;
                rows.get(i).bind(i < rowCount && suggestionIndex < suggestions.size() ? suggestionIndex : -1, rowWidth);
            }
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            canvas.texture(CirrusUiTheme.textures(SearchBoxWidget.this).cardSurface(), 0, 0, width(), height());
        }
    }

    private final class SuggestionRow extends Widget {
        private int index = -1;

        private SuggestionRow() {
            bind(-1, 0);
            onHoverTooltip((mouseX, mouseY) -> suggestionTooltip());
            onMouseClicked((input, context) -> {
                if (!input.isLeftClick() || index < 0) {
                    return EventDispatch.pass;
                }
                commitSuggestion(index);
                return EventDispatch.consumed;
            });
        }

        private void bind(int index, int width) {
            this.index = index;
            boolean visible = index >= 0;
            useStyle(sizeOf(width, visible ? suggestionRowHeight : 0), visible ? displayDefault() : displayNone());
            setVisible(visible).setActive(visible).setInteractive(visible);
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (index < 0 || index >= suggestions.size()) {
                return;
            }
            CirrusPalette palette = CirrusUiTheme.palette(SearchBoxWidget.this);
            CirrusThemeTextures textures = CirrusUiTheme.textures(SearchBoxWidget.this);
            boolean dark = CirrusUiTheme.isDark(SearchBoxWidget.this);
            boolean selected = index == selectedSuggestion;
            if (selected || hovered()) {
                canvas.texture(
                    selected ? textures.cardSelected() : textures.cardHover(),
                    1,
                    0,
                    Math.max(0, width() - 2),
                    height()
                );
            }
            Font font = context().font();
            SearchSuggestion suggestion = suggestions.get(index);
            String countLabel = suggestion.count() > 0 ? NumberUtils.toSuffix(suggestion.count()) : null;
            int tokenBudget = Math.max(0, width() - 12 - (countLabel == null ? 0 : font.width(countLabel) + 6));
            String token = fit(font, suggestion.token(), tokenBudget);
            boolean emphasized = selected || hovered();
            int baseColor = emphasized ? palette.textPrimary() : palette.textSecondary();
            int accent = dark ? palette.focusRing() : activeTokenColor;
            String typedPrefix = suggestionTypedPrefix.toLowerCase(Locale.ROOT);
            int prefixStart = typedPrefix.isEmpty() ? -1 : token.toLowerCase(Locale.ROOT).indexOf(typedPrefix);
            int prefixEnd = prefixStart < 0 ? -1 : prefixStart + typedPrefix.length();
            int x = 6;
            int textY = Math.max(1, (height() - font.lineHeight) / 2 + 1);
            for (SearchHighlightSegment segment : highlighter.apply(token)) {
                int segmentColor = segment.kind() == SearchHighlightKind.text
                        ? baseColor
                        : highlightColorFor(segment.kind(), palette, dark);
                int from = segment.start();
                int to = segment.end();
                while (from < to) {
                    int color = segmentColor;
                    int end = to;
                    if (prefixStart >= 0) {
                        if (from < prefixStart) {
                            end = Math.min(to, prefixStart);
                        } else if (from < prefixEnd) {
                            color = accent;
                            end = Math.min(to, prefixEnd);
                        }
                    }
                    String piece = token.substring(from, end);
                    canvas.text(piece, x, textY, color, false);
                    x += font.width(piece);
                    from = end;
                }
            }
            if (countLabel != null) {
                canvas.text(
                    countLabel,
                    Math.max(8, width() - 4 - font.width(countLabel)),
                    textY,
                    palette.textMuted(),
                    false
                );
            }
        }

        private @Nullable Tooltip suggestionTooltip() {
            if (index < 0 || index >= suggestions.size()) {
                return null;
            }
            Component description = suggestions.get(index).description();
            if (description == null) {
                return null;
            }
            Tooltip tooltip = Tooltip.create();
            tooltip.add(description);
            return tooltip;
        }
    }
}
