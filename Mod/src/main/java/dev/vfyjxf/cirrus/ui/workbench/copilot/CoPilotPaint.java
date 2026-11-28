package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchMeterBar;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Shared Co-Pilot dock painting: card plates, dashed drop frames, colour tags, icons and name fallbacks.
 */
public final class CoPilotPaint {

    public static final int pad = 6;
    public static final int gap = 4;
    public static final int iconSmall = 16;
    public static final int iconMedium = 20;
    public static final int chipHeight = 13;
    public static final int textHeight = 9;
    public static final int dashLength = 3;

    private CoPilotPaint() {}

    public static void card(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean hovered,
        boolean selected
    ) {
        WorkbenchPaint.card(canvas, x, y, width, height, palette, hovered, selected);
    }

    public static void plate(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.fill(x, y, width, height, palette.recessedSurface());
        canvas.strokeRect(x, y, width, height, palette.hairlineBorder(), 1);
    }

    public static void dashedRect(SceneCanvas canvas, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int offset = 0; offset < width; offset += dashLength * 2) {
            int length = Math.min(dashLength, width - offset);
            canvas.fill(x + offset, y, length, 1, color);
            canvas.fill(x + offset, y + height - 1, length, 1, color);
        }
        for (int offset = 0; offset < height; offset += dashLength * 2) {
            int length = Math.min(dashLength, height - offset);
            canvas.fill(x, y + offset, 1, length, color);
            canvas.fill(x + width - 1, y + offset, 1, length, color);
        }
    }

    public static void text(SceneCanvas canvas, Component label, int x, int y, int color) {
        if (label == null || label.getString().isEmpty()) {
            return;
        }
        canvas.drawString(label, x, y, color, false);
    }

    public static void textRight(SceneCanvas canvas, Component label, int rightX, int y, int color) {
        if (label == null || label.getString().isEmpty()) {
            return;
        }
        int width = canvas.font().width(label);
        canvas.drawString(label, rightX - width, y, color, false);
    }

    public static Component fit(SceneCanvas canvas, Component label, int maxWidth) {
        if (label == null) {
            return Component.empty();
        }
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (canvas.font().width(label) <= maxWidth) {
            return label;
        }
        String plain = label.getString();
        String prefix = canvas.font().plainSubstrByWidth(plain, Math.max(0, maxWidth - 6)).stripTrailing();
        return Component.literal(prefix.isEmpty() ? "" : prefix + "...");
    }

    public static int chip(SceneCanvas canvas, int x, int y, Component label, int ink, int plate) {
        int width = chipWidth(canvas, label);
        canvas.fill(x, y, width, chipHeight, plate);
        canvas.strokeRect(x, y, width, chipHeight, OreUiPaint.withAlpha(ink, 0.55f), 1);
        canvas.drawString(label, x + 4, ChromeText.centeredY(y, chipHeight, canvas), ink, false);
        return width;
    }

    public static int chipWidth(SceneCanvas canvas, Component label) {
        return label == null ? 8 : canvas.font().width(label) + 8;
    }

    public static void chip(
        SceneCanvas canvas,
        int x,
        int y,
        Component label,
        CirrusPalette palette,
        String styleClass
    ) {
        boolean dark = OreUiPaint.isDark(palette);
        chip(
            canvas,
            x,
            y,
            label,
            inkFor(palette, styleClass),
            OreUiPaint.withAlpha(plateFor(palette, styleClass), dark ? 0.22f : 0.13f)
        );
    }

    public static int inkFor(CirrusPalette palette, String styleClass) {
        return WorkbenchMeterBar.styleColor(palette, styleClass);
    }

    public static int plateFor(CirrusPalette palette, String styleClass) {
        return WorkbenchMeterBar.styleColor(palette, styleClass);
    }

    public static void icon(
        SceneCanvas canvas,
        @Nullable ContentStack<?, ?> stack,
        int x,
        int y,
        int size,
        CirrusPalette palette
    ) {
        if (size <= 0) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            canvas.fill(x, y, size, size, palette.recessedSurface());
            canvas.strokeRect(x, y, size, size, palette.hairlineBorder(), 1);
            return;
        }
        renderStack(canvas, stack, x, y, size);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void renderStack(SceneCanvas canvas, ContentStack<?, ?> stack, int x, int y, int size) {
        StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
        canvas.translated(x, y, () -> renderer.render(canvas.layeredGraphics(), stack, size, size, 0f));
    }

    public static void renderSlotItem(
        SceneCanvas canvas,
        @Nullable ContentStack<?, ?> stack,
        int slotX,
        int slotY,
        int slotSize,
        CirrusPalette palette
    ) {
        OreUiPaint.slot(canvas, slotX, slotY, slotSize, palette);
        int inner = Math.max(8, slotSize - 4);
        int itemSize = slotSize >= 20 ? 16 : inner;
        int ox = slotX + (slotSize - itemSize) / 2;
        int oy = slotY + (slotSize - itemSize) / 2;
        icon(canvas, stack, ox, oy, itemSize, palette);
    }

    public static @Nullable ContentStack<?, ?> stackOf(@Nullable ComponentStackKey resource, long amount) {
        if (resource == null) {
            return null;
        }
        try {
            return resource.toStack(Math.max(1L, amount));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static Component resourceName(@Nullable ComponentStackKey resource) {
        if (resource == null) {
            return Component.literal("-");
        }
        ContentStack<?, ?> stack = stackOf(resource, 1L);
        Component fallback = Component.literal(humanizeId(resource.displayId()));
        return stackName(stack, fallback);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Component stackName(@Nullable ContentStack<?, ?> stack, Component fallback) {
        if (stack == null || stack.isEmpty()) {
            return plainClean(fallback);
        }
        Component name;
        try {
            StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
            name = renderer.getName(stack.value());
        } catch (RuntimeException | LinkageError ignored) {
            return plainClean(fallback);
        }
        if (name == null || name.getString().isEmpty()) {
            return plainClean(fallback);
        }
        String text = stripBrackets(name.getString());
        if (looksLikeRawId(text)) {
            return Component.literal(humanizeId(text));
        }
        return Component.literal(text);
    }

    public static Component plainClean(@Nullable Component component) {
        if (component == null) {
            return Component.empty();
        }
        String stripped = stripBrackets(component.getString());
        if (looksLikeRawId(stripped)) {
            return Component.literal(humanizeId(stripped));
        }
        return Component.literal(stripped);
    }

    public static String stripBrackets(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    public static Component machineName(@Nullable MachineEntry machine) {
        if (machine == null || machine.isEmpty()) {
            return Component.literal("-");
        }
        ComponentStackKey key = machine.contentKey().orElse(null);
        if (key != null) {
            return resourceName(key);
        }
        return Component.literal(humanizeId(machine.canonicalKey()));
    }

    public static Component machineName(@Nullable ComponentStackKey iconKey, @Nullable String machineId) {
        if (iconKey != null) {
            return resourceName(iconKey);
        }
        if (machineId == null || machineId.isEmpty()) {
            return Component.literal("-");
        }
        return Component.literal(humanizeId(machineId));
    }

    public static String humanizeId(@Nullable String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "-";
        }
        String text = rawId.trim();
        int doubleColon = text.lastIndexOf("::");
        if (doubleColon >= 0 && doubleColon + 2 < text.length()) {
            text = text.substring(doubleColon + 2);
        }
        int colon = text.indexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            text = text.substring(colon + 1);
        }
        int slash = text.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < text.length()) {
            text = text.substring(slash + 1);
        }
        String[] words = text.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder(text.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }
        return out.isEmpty() ? rawId : out.toString();
    }

    private static boolean looksLikeRawId(String text) {
        return text.indexOf(':') >= 0 && text.indexOf(' ') < 0;
    }

    public static Component withSubject(LangEntry label, Component subject) {
        Objects.requireNonNull(label, "label");
        return label.get().copy().append(Component.literal(" ")).append(subject);
    }

    public static Component withSubject(LangEntry label, String subject) {
        return withSubject(label, Component.literal(subject == null ? "-" : subject));
    }

    public static void value(SceneCanvas canvas, Component label, int x, int y, int color) {
        text(canvas, label, x, y, color);
    }

    public static String percent(double fraction) {
        if (!Double.isFinite(fraction)) {
            return "0%";
        }
        return Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 100.0) + "%";
    }

    public static String decimal(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        String formatted = String.format(Locale.ROOT, "%." + decimals + "f", value);
        return formatted.contains(".") ? formatted.replaceAll("0+$", "").replaceAll("\\.$", "") : formatted;
    }
}
