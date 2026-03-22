package dev.vfyjxf.cirrus.ui.widget.omnicalc;

import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;

/**
 * OmniCalc Pro dark-theme color palette.
 * Matches the browser version's Tailwind CSS color scheme.
 */
public final class OmniCalcColors {

    private OmniCalcColors() {}

    // ═══════════════════ Base Grays ═══════════════════
    public static final int BG_PRIMARY    = 0xFF030712; // gray-950 — deepest background
    public static final int BG_PANEL      = 0xFF111827; // gray-900 — card/panel background
    public static final int BG_HOVER      = 0xFF1F2937; // gray-800 — hover / elevated surface
    public static final int BG_ELEVATED   = 0xFF374151; // gray-700
    public static final int BG_SURFACE    = 0xFF0F172A; // gray-900/50 variant
    public static final int BORDER_MAIN   = 0xFF1F2937; // gray-800
    public static final int BORDER_SEC    = 0xFF374151; // gray-700

    // ═══════════════════ Text ═══════════════════
    public static final int TEXT_PRIMARY   = 0xFFE5E7EB; // gray-200
    public static final int TEXT_SECONDARY = 0xFF9CA3AF; // gray-400
    public static final int TEXT_WEAK      = 0xFF6B7280; // gray-500
    public static final int TEXT_DIM       = 0xFF4B5563; // gray-600
    public static final int TEXT_MUTED     = 0xFF374151; // gray-700
    public static final int TEXT_WHITE     = 0xFFFFFFFF;

    // ═══════════════════ Accent (Blue) ═══════════════════
    public static final int ACCENT         = 0xFF2563EB; // accent-600 — primary buttons
    public static final int ACCENT_LIGHT   = 0xFF3B82F6; // blue-500
    public static final int ACCENT_TEXT    = 0xFF60A5FA; // accent-400 — links, active text
    public static final int ACCENT_BG      = 0x262563EB; // accent-600/15 — active nav background
    public static final int ACCENT_BORDER  = 0x332563EB; // accent-500/20
    public static final int ACCENT_DARK    = 0xFF1E40AF; // blue-800

    // ═══════════════════ Semantic Colors ═══════════════════
    public static final int GREEN          = 0xFF22C55E; // green-500
    public static final int GREEN_BG       = 0x1A22C55E; // green-900/10
    public static final int GREEN_TEXT     = 0xFF4ADE80; // tech-green
    public static final int GREEN_DARK_BG  = 0x33166534; // green-900/20
    public static final int RED            = 0xFFEF4444; // red-500
    public static final int RED_TEXT       = 0xFFF87171; // tech-red
    public static final int RED_BG         = 0x337F1D1D; // red-900/20
    public static final int RED_DARK_BG    = 0x807F1D1D; // red-900/50
    public static final int ORANGE         = 0xFFF97316; // orange-500
    public static final int ORANGE_TEXT    = 0xFFFB923C; // tech-orange
    public static final int PURPLE         = 0xFF8B5CF6; // purple-500
    public static final int PURPLE_TEXT    = 0xFFA78BFA; // tech-purple
    public static final int PURPLE_BG      = 0x1A581C87; // purple-900/10
    public static final int AMBER          = 0xFFF59E0B; // amber-500

    // ═══════════════════ Sidebar ═══════════════════
    public static final int SIDEBAR_BG     = 0xF0111827; // glass-sidebar base
    public static final int SIDEBAR_BORDER = 0xFF1F2937;

    // ═══════════════════ Tree View ═══════════════════
    public static final int TREE_CANVAS_BG = 0xFF0A0E17;
    public static final int LAYER_ACTIVE   = 0xFF22C55E; // green-500

    // ═══════════════════ Node Types (Rules Engine) ═══════════════════
    public static final int NODE_TRIGGER   = 0xFFDC2626; // red-600 (bright)
    public static final int NODE_CONDITION = 0xFF16A34A; // green-600
    public static final int NODE_ACTION    = 0xFF2563EB; // blue-600
    public static final int NODE_LOGIC     = 0xFF9333EA; // purple-600

    // ═══════════════════ Pre-computed Textures ═══════════════════
    public static final ColorTexture TEX_BG_PRIMARY  = new ColorTexture(BG_PRIMARY);
    public static final ColorTexture TEX_BG_PANEL    = new ColorTexture(BG_PANEL);
    public static final ColorTexture TEX_BG_HOVER    = new ColorTexture(BG_HOVER);
    public static final ColorTexture TEX_BORDER_MAIN = new ColorTexture(BORDER_MAIN);
    public static final ColorTexture TEX_ACCENT      = new ColorTexture(ACCENT);
    public static final ColorTexture TEX_ACCENT_BG   = new ColorTexture(ACCENT_BG);
    public static final ColorTexture TEX_SIDEBAR     = new ColorTexture(SIDEBAR_BG);
    public static final ColorTexture TEX_TREE_CANVAS = new ColorTexture(TREE_CANVAS_BG);

    // ═══════════════════ Drawing Helpers ═══════════════════

    /** Draws a card with rounded corners, optional border, and optional shadow. */
    public static void drawCard(SceneCanvas c, int x, int y, int w, int h,
                                int fill, float radius, int borderColor) {
        c.shadow(x + 1, y + 1, w, h, radius, 2f, 4f, 0x40000000);
        c.roundedRect(x, y, w, h, radius, fill, 1f, borderColor);
    }

    /** Draws a panel section header with rounded top corners. */
    public static void drawSectionHeader(SceneCanvas c, int x, int y, int w, int h,
                                         int color, float radius) {
        c.roundedRect(x, y, w, h, radius, radius, 0f, 0f, color, 0, 0);
    }

    /** Draws a small badge (e.g., "CRITICAL", tier labels). */
    public static void drawBadge(SceneCanvas c, int x, int y, String text, int bgColor, int textColor) {
        int textW = text.length() * 4 + 4; // approximate
        int h = 7;
        c.roundedRect(x, y, textW, h, 2f, bgColor, 0, 0);
        c.drawString(text, x + 2, y + 1, textColor);
    }

    /** Draws a progress bar with rounded ends. */
    public static void drawProgressBar(SceneCanvas c, int x, int y, int w, int h,
                                       float progress, int bgColor, int fillColor) {
        float r = h / 2f;
        c.roundedRect(x, y, w, h, r, bgColor, 0, 0);
        if (progress > 0.01f) {
            int fillW = Math.max(h, (int) (w * Math.min(1f, progress)));
            c.roundedRect(x, y, fillW, h, r, fillColor, 0, 0);
        }
    }

    /** Draws a node for the rules engine canvas. */
    public static void drawRuleNode(SceneCanvas c, int x, int y, int w, int h,
                                    String type, String desc, int headerColor, boolean selected) {
        int headerH = 10;
        // Shadow
        c.shadow(x + 1, y + 1, w, h, 4f, 2f, 6f, 0x60000000);
        // Body
        c.roundedRect(x, y, w, h, 4f,
                BG_PANEL, selected ? 1.5f : 1f,
                selected ? ACCENT_LIGHT : BORDER_MAIN);
        // Header
        c.roundedRect(x, y, w, headerH, 4f, 4f, 0f, 0f, headerColor, 0, 0);
        c.drawString(type, x + 3, y + 2, TEXT_WHITE);
        // Description
        c.drawString(desc, x + 3, y + headerH + 2, TEXT_SECONDARY);
        // Ports
        float portR = 2.5f;
        c.circle(x - 1, y + h / 2f, portR, BG_ELEVATED);
        c.circle(x + w + 1, y + h / 2f, portR, BG_ELEVATED);
    }
}
