package dev.vfyjxf.cirrus.ui.execution.support;

import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExecutionOverlayUi {
	//region constants

	public static final int bodyPadding = 6;
	public static final int slotSize = 18;
	public static final int titleColor = 0xFF393939;
	public static final int textPrimary = 0xFF525252;
	public static final int textSecondary = 0xFF697077;
	public static final int accent = 0xFF3668DF;
	public static final int success = 0xFF198038;
	public static final int danger = 0xFFDA1E28;
	public static final int warning = 0xFF8E6A00;
	public static final int divider = 0x66A8ADB3;
	public static final int tooltipTitleColor = 0xFFF4F4F4;
	public static final int tooltipTextPrimary = 0xFFE0E0E0;
	public static final int tooltipTextSecondary = 0xFFC6C6C6;
	public static final int tooltipAccent = 0xFF78A9FF;
	public static final int tooltipSuccess = 0xFF42BE65;
	public static final int tooltipDanger = 0xFFFF8389;
	public static final int tooltipWarning = 0xFFF1C21B;
	public static final int rateMin = ExecutionOverlayClientConfig.overlayRateMin;
	public static final int rateMax = ExecutionOverlayClientConfig.overlayRateMax;
	//endregion

	private ExecutionOverlayUi() {
	}

	//region text

	public static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	public static String tr(LangEntry entry, Object... args) {
		return entry.string(args);
	}

	public static MutableComponent tooltipTitle(String key, Object... args) {
		return Component.translatable(key, args).withColor(tooltipTitleColor);
	}

	public static MutableComponent tooltipTitle(LangEntry entry, Object... args) {
		return entry.colored(tooltipTitleColor, args);
	}

	public static MutableComponent tooltipTitleText(String text) {
		return Component.literal(text).withColor(tooltipTitleColor);
	}

	public static MutableComponent tooltipBody(String key, Object... args) {
		return Component.translatable(key, args).withColor(tooltipTextSecondary);
	}

	public static MutableComponent tooltipBody(LangEntry entry, Object... args) {
		return entry.colored(tooltipTextSecondary, args);
	}

	public static MutableComponent tooltipBodyText(String text) {
		return Component.literal(text).withColor(tooltipTextSecondary);
	}

	public static MutableComponent tooltipAccentText(String text) {
		return Component.literal(text).withColor(tooltipAccent);
	}

	public static MutableComponent tooltipWarningText(String text) {
		return Component.literal(text).withColor(tooltipWarning);
	}

	public static MutableComponent tooltipDangerText(String text) {
		return Component.literal(text).withColor(tooltipDanger);
	}
	//endregion

	//region sound

	public static void playClickSound() {
		playClickSound(1f);
	}

	public static void playClickSound(float pitch) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getSoundManager() == null) {
			return;
		}
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}
	//endregion

	//region format

	public static String trim(Font font, String text, int maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 0) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String prefix = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("...")));
		return prefix + "...";
	}

	public static List<FormattedCharSequence> wrap(Font font, Component text, int maxWidth) {
		if (font == null || text == null || text.getString().isEmpty() || maxWidth <= 0) {
			return List.of();
		}
		return List.copyOf(font.split(text, Math.max(1, maxWidth)));
	}

	public static int wrappedHeight(Font font, Component text, int maxWidth, int lineGap) {
		List<FormattedCharSequence> lines = wrap(font, text, maxWidth);
		if (lines.isEmpty()) {
			return 0;
		}
		return lines.size() * font.lineHeight + Math.max(0, lines.size() - 1) * Math.max(0, lineGap);
	}

	public static int drawWrappedText(
		SceneCanvas canvas,
		Font font,
		Component text,
		int x,
		int y,
		int maxWidth,
		int color,
		boolean shadow,
		int lineGap
	) {
		List<FormattedCharSequence> lines = wrap(font, text, maxWidth);
		for (int index = 0; index < lines.size(); index++) {
			FormattedCharSequence line = lines.get(index);
			canvas.text(line, x, y, color, shadow);
			y += font.lineHeight;
			if (index + 1 < lines.size()) {
				y += Math.max(0, lineGap);
			}
		}
		return y;
	}

	public static List<String> wrapPlainLines(Font font, @Nullable String text, int maxWidth) {
		return wrapPlainLines(font, text, maxWidth, Integer.MAX_VALUE);
	}

	public static List<String> wrapPlainLines(Font font, @Nullable String text, int maxWidth, int maxLines) {
		if (text == null || text.isEmpty() || maxWidth <= 0 || maxLines <= 0) {
			return List.of();
		}
		if (font == null) {
			return List.of(text);
		}

		ArrayList<String> lines = new ArrayList<>();
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		for (String paragraph : normalized.split("\n", -1)) {
			if (lines.size() >= maxLines) {
				break;
			}
			if (paragraph.isEmpty()) {
				lines.add("");
				continue;
			}

			String remaining = paragraph;
			while (!remaining.isEmpty() && lines.size() < maxLines) {
				String slice = font.plainSubstrByWidth(remaining, Math.max(1, maxWidth));
				if (slice.isEmpty()) {
					slice = remaining.substring(0, 1);
				}

				int consumed = slice.length();
				if (consumed < remaining.length()) {
					int wrapBreak = lastPlainWrapBreak(slice);
					if (wrapBreak > 0) {
						slice = slice.substring(0, wrapBreak).stripTrailing();
						consumed = wrapBreak;
						while (consumed < remaining.length() && Character.isWhitespace(remaining.charAt(consumed))) {
							consumed++;
						}
					}
				}

				lines.add(slice);
				remaining = remaining.substring(Math.min(consumed, remaining.length())).stripLeading();
			}
		}
		return List.copyOf(lines);
	}

	public static int wrappedPlainHeight(Font font, @Nullable String text, int maxWidth, int lineGap, int maxLines) {
		List<String> lines = wrapPlainLines(font, text, maxWidth, maxLines);
		if (lines.isEmpty()) {
			return 0;
		}
		return lines.size() * font.lineHeight + Math.max(0, lines.size() - 1) * Math.max(0, lineGap);
	}

	public static int drawWrappedPlainText(
		SceneCanvas canvas,
		Font font,
		@Nullable String text,
		int x,
		int y,
		int maxWidth,
		int color,
		boolean shadow,
		int lineGap,
		int maxLines
	) {
		List<String> lines = wrapPlainLines(font, text, maxWidth, maxLines);
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			canvas.drawString(line, x, y, color, shadow);
			y += font.lineHeight;
			if (index + 1 < lines.size()) {
				y += Math.max(0, lineGap);
			}
		}
		return y;
	}

	private static int lastPlainWrapBreak(String text) {
		for (int index = text.length() - 1; index >= 0; index--) {
			char c = text.charAt(index);
			if (Character.isWhitespace(c) || c == '-' || c == '/' || c == '_' || c == '|') {
				return index;
			}
		}
		return -1;
	}

	public static int statusColor(@Nullable String status) {
		if (status == null || status.isBlank()) {
			return textSecondary;
		}
		return switch (status.toLowerCase(Locale.ROOT)) {
			case "completed" -> success;
			case "paused" -> warning;
			case "failed" -> danger;
			case "running" -> accent;
			default -> textSecondary;
		};
	}

	public static String localizedStatus(@Nullable String status) {
		if (status == null || status.isBlank()) {
			return "-";
		}
		return tr(CirrusLangs.Execution.Overlay.status(status.toLowerCase(Locale.ROOT)));
	}

	public static String compactNumber(long value) {
		long normalized = Math.max(0L, value);
		if (normalized >= 1_000_000L) {
			return (normalized / 1_000_000L) + "M";
		}
		if (normalized >= 1_000L) {
			return (normalized / 1_000L) + "K";
		}
		return Long.toString(normalized);
	}
	//endregion

	//region geometry

	public static boolean contains(Rect2i area, int x, int y) {
		return x >= area.getX()
				&& x < area.getX() + area.getWidth()
				&& y >= area.getY()
				&& y < area.getY() + area.getHeight();
	}
	//endregion

	//region render

	public static void drawSlot(SceneCanvas canvas, ItemStack stack, int x, int y) {
		canvas.texture(Background.Slot.dark, x, y, slotSize, slotSize);
		if (stack.isEmpty()) {
			return;
		}
		canvas.renderItem(stack, x + 1, y + 1);
		canvas.renderItemDecorations(stack, x + 1, y + 1);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void drawSlot(SceneCanvas canvas, ContentStack<?, ?> stack, int x, int y) {
		canvas.texture(Background.Slot.dark, x, y, slotSize, slotSize);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
		canvas.translated(x + 1, y + 1, () ->
				renderer.render(canvas.layeredGraphics(), stack, slotSize - 2, slotSize - 2, 0));
	}

	public static void drawSlot(SceneCanvas canvas, @Nullable RecipeContent content, int x, int y) {
		canvas.texture(Background.Slot.dark, x, y, slotSize, slotSize);
		if (content == null || content.isEmpty() || !ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
			return;
		}
		ExecutionOverlayContentHelper.renderRecipeContent(canvas, content, x + 1, y + 1, slotSize - 2, slotSize - 2, 0);
	}
	//endregion
}
