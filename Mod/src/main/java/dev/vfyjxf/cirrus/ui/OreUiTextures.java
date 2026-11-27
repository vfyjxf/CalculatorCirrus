package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.texture.BatchableTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural sharp 90-degree Minecraft OreUI textures with crisp pixel bevels and ledges.
 */
public final class OreUiTextures {

    private OreUiTextures() {}

    public static VisualTexture raised(
        int face,
        int highlight,
        int ledge,
        int outerInk,
        int ledgeHeight,
        int borderWidth
    ) {
        return new RaisedTexture(face, highlight, ledge, outerInk, Math.max(0, ledgeHeight), Math.max(1, borderWidth));
    }

    public static VisualTexture pressed(int face, int topShadow, int bottomLip, int outerInk) {
        return new PressedTexture(face, topShadow, bottomLip, outerInk);
    }

    public static VisualTexture searchField(int face, int topShadow, int bottomLip, int border) {
        return new SearchFieldTexture(face, topShadow, bottomLip, border);
    }

    public static VisualTexture slot(int cavity, int topShadow, int leftShadow, int calmRim) {
        return new SlotTexture(cavity, topShadow, leftShadow, calmRim);
    }

    public static VisualTexture flatBox(int fill, int border) {
        return new FlatBoxTexture(fill, border);
    }

    public static SizedTexture oreSwitch(
        boolean knobRight,
        boolean activeTrack,
        boolean hovered,
        int trackOff,
        int trackOn,
        int thumbFace,
        int thumbHighlight,
        int thumbLedge,
        int outerInk
    ) {
        return new OreSwitchTexture(
            knobRight,
            activeTrack,
            hovered,
            trackOff,
            trackOn,
            thumbFace,
            thumbHighlight,
            thumbLedge,
            outerInk
        );
    }

    public static final class Dark {
        public static final int outerInk = 0xFF14171E;
        public static final int dockFace = 0xF7292F3B;
        public static final int dockHighlight = 0xFF3E4759;
        public static final int dockLedge = 0xFF1B1F29;
        public static final int cardFace = 0xFF343B4A;
        public static final int cardHighlight = 0xFF4E586D;
        public static final int cardLedge = 0xFF212631;
        public static final int cardHoverFace = 0xFF3F485A;
        public static final int cardHoverHighlight = 0xFF5D6982;
        public static final int cardHoverLedge = 0xFF282E3B;
        public static final int cardSelectedFace = 0xFF3B4659;
        public static final int cardSelectedHighlight = 0xFF5A6C8A;
        public static final int cardSelectedLedge = 0xFF252F40;
        public static final int cardSelectedBorder = 0xFF63BCFC;
        public static final int cardFooter = 0xFF2B313E;
        public static final int cardDivider = 0xFF434C5E;
        public static final int inputFace = 0xFF222834;
        public static final int inputShadow = 0xFF161A22;
        public static final int inputLip = 0xFF3A4252;
        public static final int slotCavity = 0xFF242A36;
        public static final int slotTopShadow = 0xFF151921;
        public static final int slotLeftShadow = 0xFF191E28;
        public static final int slotRim = 0xFF3D4556;
        public static final int slotDarkCavity = 0xFF34242D;
        public static final int buttonUpFace = 0xFF434C5E;
        public static final int buttonUpHighlight = 0xFF626E87;
        public static final int buttonUpLedge = 0xFF2A303D;
        public static final int buttonHoverFace = 0xFF505B70;
        public static final int buttonHoverHighlight = 0xFF72809C;
        public static final int buttonHoverLedge = 0xFF323947;
        public static final int buttonDownFace = 0xFF2C3240;
        public static final int ctaPrimaryFace = 0xFF2F7D38;
        public static final int ctaPrimaryHighlight = 0xFF5EC268;
        public static final int ctaPrimaryLedge = 0xFF1D5423;
        public static final int ctaDangerFace = 0xFFB8323C;
        public static final int ctaDangerHighlight = 0xFFE3626C;
        public static final int ctaDangerLedge = 0xFF7D1E26;
        public static final int meterTrack = 0xFF212631;
        public static final int meterBorder = 0xFF465064;
        public static final int scrollTrack = 0xFF1F242E;
        public static final int scrollThumb = 0xFF535F75;
        public static final int scrollThumbHighlight = 0xFF6E7D99;
        public static final int scrollThumbLedge = 0xFF353D4D;

        public static final VisualTexture dock = raised(dockFace, dockHighlight, dockLedge, outerInk, 2, 1);
        public static final VisualTexture card = raised(cardFace, cardHighlight, cardLedge, outerInk, 2, 1);
        public static final VisualTexture cardHover = raised(
            cardHoverFace,
            cardHoverHighlight,
            cardHoverLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture cardSelected = raised(
            cardSelectedFace,
            cardSelectedHighlight,
            cardSelectedLedge,
            cardSelectedBorder,
            2,
            2
        );
        public static final VisualTexture flat = flatBox(cardFace, cardDivider);
        public static final VisualTexture inset = flatBox(0xFF232833, cardDivider);
        public static final VisualTexture recessed = searchField(inputFace, inputShadow, inputLip, outerInk);
        public static final VisualTexture slotBase = slot(slotCavity, slotTopShadow, slotLeftShadow, slotRim);
        public static final VisualTexture slotDark = slot(slotDarkCavity, slotTopShadow, slotLeftShadow, 0xFF693B47);
        public static final VisualTexture btnUp = raised(
            buttonUpFace,
            buttonUpHighlight,
            buttonUpLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture btnHover = raised(
            buttonHoverFace,
            buttonHoverHighlight,
            buttonHoverLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture btnDown = pressed(buttonDownFace, inputShadow, cardDivider, outerInk);
        public static final VisualTexture ctaPrimary = raised(
            ctaPrimaryFace,
            ctaPrimaryHighlight,
            ctaPrimaryLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture ctaDanger = raised(
            ctaDangerFace,
            ctaDangerHighlight,
            ctaDangerLedge,
            outerInk,
            2,
            1
        );
        public static final SizedTexture switchOff = oreSwitch(
            false,
            false,
            false,
            0xFF2A303D,
            ctaPrimaryFace,
            buttonUpFace,
            buttonUpHighlight,
            buttonUpLedge,
            outerInk
        );
        public static final SizedTexture switchOffHover = oreSwitch(
            false,
            false,
            true,
            0xFF333A4A,
            0xFF3B9645,
            buttonHoverFace,
            buttonHoverHighlight,
            buttonHoverLedge,
            outerInk
        );
        public static final SizedTexture switchOn = oreSwitch(
            true,
            true,
            false,
            0xFF2A303D,
            ctaPrimaryFace,
            0xFFE2E8F0,
            0xFFFFFFFF,
            0xFF94A3B8,
            outerInk
        );
        public static final SizedTexture switchOnHover = oreSwitch(
            true,
            true,
            true,
            0xFF333A4A,
            0xFF3B9645,
            0xFFF8FAFC,
            0xFFFFFFFF,
            0xFFAAB8CC,
            outerInk
        );
        public static final VisualTexture scrollTrackTex = flatBox(scrollTrack, cardDivider);
        public static final VisualTexture scrollThumbTex = raised(
            scrollThumb,
            scrollThumbHighlight,
            scrollThumbLedge,
            outerInk,
            1,
            1
        );
        public static final VisualTexture minimapFrame = flatBox(0xE8242A35, cardDivider);
        public static final VisualTexture meterTrackTex = flatBox(meterTrack, meterBorder);

        private Dark() {}
    }

    public static final class Light {
        public static final int outerInk = 0xFF8A909B;
        public static final int dockFace = 0xFAE8EAEE;
        public static final int dockHighlight = 0xFFFFFFFF;
        public static final int dockLedge = 0xFFC4C8D1;
        public static final int cardFace = 0xFFF5F6F8;
        public static final int cardHighlight = 0xFFFFFFFF;
        public static final int cardLedge = 0xFFC9CDD6;
        public static final int cardHoverFace = 0xFFFFFFFF;
        public static final int cardHoverHighlight = 0xFFFFFFFF;
        public static final int cardHoverLedge = 0xFFD2D6DE;
        public static final int cardSelectedFace = 0xFFF2F7FC;
        public static final int cardSelectedHighlight = 0xFFFFFFFF;
        public static final int cardSelectedLedge = 0xFFBCD0E2;
        public static final int cardSelectedBorder = 0xFF3E78A8;
        public static final int cardFooter = 0xFFEBEDF1;
        public static final int cardDivider = 0xFFD3D7DF;
        public static final int inputFace = 0xFFFAFAFC;
        public static final int inputShadow = 0xFFB8BDC7;
        public static final int inputLip = 0xFFFFFFFF;
        public static final int inputBorder = 0xFF9BA1AC;
        public static final int slotCavity = 0xFFC5C8D0;
        public static final int slotTopShadow = 0xFF8F949F;
        public static final int slotLeftShadow = 0xFFA3A8B2;
        public static final int slotRim = 0xFFE5E7EC;
        public static final int slotDarkCavity = 0xFFD4B7BC;
        public static final int buttonUpFace = 0xFFEFF0F3;
        public static final int buttonUpHighlight = 0xFFFFFFFF;
        public static final int buttonUpLedge = 0xFFC2C7D0;
        public static final int buttonHoverFace = 0xFFF9FAFB;
        public static final int buttonHoverHighlight = 0xFFFFFFFF;
        public static final int buttonHoverLedge = 0xFFCDD1DA;
        public static final int buttonDownFace = 0xFFDCDEE4;
        public static final int ctaPrimaryFace = 0xFF367D24;
        public static final int ctaPrimaryHighlight = 0xFF68BF52;
        public static final int ctaPrimaryLedge = 0xFF245717;
        public static final int ctaDangerFace = 0xFFB52B33;
        public static final int ctaDangerHighlight = 0xFFE0636A;
        public static final int ctaDangerLedge = 0xFF7A1B21;
        public static final int meterTrack = 0xFFDDE0E6;
        public static final int meterBorder = 0xFFB3B8C2;
        public static final int scrollTrack = 0xFFD8DBE2;
        public static final int scrollThumb = 0xFFF2F3F6;
        public static final int scrollThumbHighlight = 0xFFFFFFFF;
        public static final int scrollThumbLedge = 0xFFB8BDC6;

        public static final VisualTexture dock = raised(dockFace, dockHighlight, dockLedge, outerInk, 2, 1);
        public static final VisualTexture card = raised(cardFace, cardHighlight, cardLedge, outerInk, 2, 1);
        public static final VisualTexture cardHover = raised(
            cardHoverFace,
            cardHoverHighlight,
            cardHoverLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture cardSelected = raised(
            cardSelectedFace,
            cardSelectedHighlight,
            cardSelectedLedge,
            cardSelectedBorder,
            2,
            1
        );
        public static final VisualTexture flat = flatBox(cardFace, cardDivider);
        public static final VisualTexture inset = flatBox(0xFFE6E8ED, cardDivider);
        public static final VisualTexture recessed = searchField(inputFace, inputShadow, inputLip, inputBorder);
        public static final VisualTexture slotBase = slot(slotCavity, slotTopShadow, slotLeftShadow, slotRim);
        public static final VisualTexture slotDark = slot(slotDarkCavity, 0xFF9E7076, 0xFFB0848A, slotRim);
        public static final VisualTexture btnUp = raised(
            buttonUpFace,
            buttonUpHighlight,
            buttonUpLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture btnHover = raised(
            buttonHoverFace,
            buttonHoverHighlight,
            buttonHoverLedge,
            outerInk,
            2,
            1
        );
        public static final VisualTexture btnDown = pressed(buttonDownFace, inputShadow, inputLip, outerInk);
        public static final VisualTexture ctaPrimary = raised(
            ctaPrimaryFace,
            ctaPrimaryHighlight,
            ctaPrimaryLedge,
            0xFF284F20,
            2,
            1
        );
        public static final VisualTexture ctaDanger = raised(
            ctaDangerFace,
            ctaDangerHighlight,
            ctaDangerLedge,
            0xFF6B2429,
            2,
            1
        );
        public static final SizedTexture switchOff = oreSwitch(
            false,
            false,
            false,
            0xFFCDD1DA,
            ctaPrimaryFace,
            0xFFF8F9FA,
            0xFFFFFFFF,
            0xFFBCC1CB,
            outerInk
        );
        public static final SizedTexture switchOffHover = oreSwitch(
            false,
            false,
            true,
            0xFFD6DAE2,
            0xFF42912E,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFC4C9D3,
            outerInk
        );
        public static final SizedTexture switchOn = oreSwitch(
            true,
            true,
            false,
            0xFFCDD1DA,
            ctaPrimaryFace,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFBCC1CB,
            outerInk
        );
        public static final SizedTexture switchOnHover = oreSwitch(
            true,
            true,
            true,
            0xFFD6DAE2,
            0xFF42912E,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFC4C9D3,
            outerInk
        );
        public static final VisualTexture scrollTrackTex = flatBox(scrollTrack, meterBorder);
        public static final VisualTexture scrollThumbTex = raised(
            scrollThumb,
            scrollThumbHighlight,
            scrollThumbLedge,
            outerInk,
            1,
            1
        );
        public static final VisualTexture minimapFrame = flatBox(0xF0ECEEF2, outerInk);
        public static final VisualTexture meterTrackTex = flatBox(meterTrack, meterBorder);

        private Light() {}
    }

    private static int mul(int base, int tint) {
        return tint == 0xFFFFFFFF ? base : VisualTexture.multiply(base, tint);
    }

    private record RaisedTexture(int face, int highlight, int ledge, int outerInk, int ledgeHeight, int borderWidth)
            implements
                BatchableTexture {

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            int bw = Math.min(borderWidth, Math.min(width, height) / 2);
            graphics.fill(x, y, x + width, y + height, outerInk);
            int ix = x + bw;
            int iy = y + bw;
            int iw = width - bw * 2;
            int ih = height - bw * 2;
            if (iw <= 0 || ih <= 0) {
                return;
            }
            int lh = ih >= ledgeHeight + 4 ? ledgeHeight : (ih >= 4 ? 1 : 0);
            int capH = ih - lh;
            if (lh > 0) {
                graphics.fill(ix, iy + capH, ix + iw, iy + ih, ledge);
            }
            graphics.fill(ix, iy, ix + iw, iy + capH, face);
            graphics.fill(ix, iy, ix + iw, iy + 1, highlight);
            graphics.fill(ix, iy, ix + 1, iy + capH, highlight);
            graphics.fill(ix + iw - 1, iy, ix + iw, iy + capH, highlight);
            if (lh > 0 && capH >= 3) {
                graphics.fill(ix, iy + capH - 1, ix + iw, iy + capH, highlight);
            }
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            float bw = Math.min(borderWidth, Math.min(width, height) * 0.5f);
            emitter.colored(x, y, width, height, mul(outerInk, color));
            float ix = x + bw;
            float iy = y + bw;
            float iw = width - bw * 2f;
            float ih = height - bw * 2f;
            if (iw <= 0f || ih <= 0f) {
                return;
            }
            float lh = ih >= ledgeHeight + 4f ? ledgeHeight : (ih >= 4f ? 1f : 0f);
            float capH = ih - lh;
            if (lh > 0f) {
                emitter.colored(ix, iy + capH, iw, lh, mul(ledge, color));
            }
            emitter.colored(ix, iy, iw, capH, mul(face, color));
            int hi = mul(highlight, color);
            emitter.colored(ix, iy, iw, 1f, hi);
            emitter.colored(ix, iy, 1f, capH, hi);
            emitter.colored(ix + iw - 1f, iy, 1f, capH, hi);
            if (lh > 0f && capH >= 3f) {
                emitter.colored(ix, iy + capH - 1f, iw, 1f, hi);
            }
        }
    }

    private record PressedTexture(int face, int topShadow, int bottomLip, int outerInk) implements BatchableTexture {

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            graphics.fill(x, y, x + width, y + height, outerInk);
            if (width <= 2 || height <= 2) {
                return;
            }
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, face);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, topShadow);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, topShadow);
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, bottomLip);
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            emitter.colored(x, y, width, height, mul(outerInk, color));
            if (width <= 2f || height <= 2f) {
                return;
            }
            emitter.colored(x + 1f, y + 1f, width - 2f, height - 2f, mul(face, color));
            int sh = mul(topShadow, color);
            emitter.colored(x + 1f, y + 1f, width - 2f, 1f, sh);
            emitter.colored(x + 1f, y + 1f, 1f, height - 2f, sh);
            emitter.colored(x + 1f, y + height - 2f, width - 2f, 1f, mul(bottomLip, color));
        }
    }

    private record SearchFieldTexture(int face, int topShadow, int bottomLip, int border) implements BatchableTexture {

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            graphics.fill(x, y, x + width, y + height, border);
            if (width <= 2 || height <= 2) {
                return;
            }
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, face);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, topShadow);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, topShadow);
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, bottomLip);
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            emitter.colored(x, y, width, height, mul(border, color));
            if (width <= 2f || height <= 2f) {
                return;
            }
            emitter.colored(x + 1f, y + 1f, width - 2f, height - 2f, mul(face, color));
            int sh = mul(topShadow, color);
            emitter.colored(x + 1f, y + 1f, width - 2f, 1f, sh);
            emitter.colored(x + 1f, y + 1f, 1f, height - 2f, sh);
            emitter.colored(x + 1f, y + height - 2f, width - 2f, 1f, mul(bottomLip, color));
        }
    }

    private record SlotTexture(int cavity, int topShadow, int leftShadow, int calmRim) implements BatchableTexture {

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            graphics.fill(x, y, x + width, y + height, cavity);
            if (width <= 2 || height <= 2) {
                return;
            }
            graphics.fill(x, y, x + width - 1, y + 1, topShadow);
            graphics.fill(x, y, x + 1, y + height - 1, leftShadow);
            if (height >= 20) {
                graphics.fill(x + 1, y + 1, x + width - 1, y + 2, leftShadow);
            }
            graphics.fill(x, y + height - 1, x + width, y + height, calmRim);
            graphics.fill(x + width - 1, y, x + width, y + height, calmRim);
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            emitter.colored(x, y, width, height, mul(cavity, color));
            if (width <= 2f || height <= 2f) {
                return;
            }
            emitter.colored(x, y, width - 1f, 1f, mul(topShadow, color));
            emitter.colored(x, y, 1f, height - 1f, mul(leftShadow, color));
            if (height >= 20f) {
                emitter.colored(x + 1f, y + 1f, width - 2f, 1f, mul(leftShadow, color));
            }
            int rim = mul(calmRim, color);
            emitter.colored(x, y + height - 1f, width, 1f, rim);
            emitter.colored(x + width - 1f, y, 1f, height, rim);
        }
    }

    private record FlatBoxTexture(int fill, int border) implements BatchableTexture {

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            graphics.fill(x, y, x + width, y + height, border);
            if (width > 2 && height > 2) {
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
            }
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            emitter.colored(x, y, width, height, mul(border, color));
            if (width > 2f && height > 2f) {
                emitter.colored(x + 1f, y + 1f, width - 2f, height - 2f, mul(fill, color));
            }
        }
    }

    private record OreSwitchTexture(
        boolean knobRight,
        boolean activeTrack,
        boolean hovered,
        int trackOff,
        int trackOn,
        int thumbFace,
        int thumbHighlight,
        int thumbLedge,
        int outerInk
    ) implements SizedTexture, BatchableTexture {

        @Override
        public int width() {
            return 20;
        }

        @Override
        public int height() {
            return 12;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            int trackY = y + 1;
            int trackH = Math.max(6, height - 2);
            graphics.fill(x, trackY, x + width, trackY + trackH, outerInk);
            graphics.fill(x + 1, trackY + 1, x + width - 1, trackY + trackH - 1, activeTrack ? trackOn : trackOff);
            int thumbW = Math.max(6, width / 2);
            int thumbX = knobRight ? x + width - thumbW : x;
            int thumbH = Math.max(8, height);
            graphics.fill(thumbX, y, thumbX + thumbW, y + thumbH, outerInk);
            if (thumbW > 2 && thumbH > 3) {
                int capH = thumbH - 3;
                graphics.fill(thumbX + 1, y + 1 + capH, thumbX + thumbW - 1, y + thumbH - 1, thumbLedge);
                graphics.fill(thumbX + 1, y + 1, thumbX + thumbW - 1, y + 1 + capH, thumbFace);
                graphics.fill(thumbX + 1, y + 1, thumbX + thumbW - 1, y + 2, thumbHighlight);
            }
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            float trackY = y + 1f;
            float trackH = Math.max(6f, height - 2f);
            int ink = mul(outerInk, color);
            emitter.colored(x, trackY, width, trackH, ink);
            emitter.colored(x + 1f, trackY + 1f, width - 2f, trackH - 2f, mul(activeTrack ? trackOn : trackOff, color));
            float thumbW = Math.max(6f, width * 0.5f);
            float thumbX = knobRight ? x + width - thumbW : x;
            float thumbH = Math.max(8f, height);
            emitter.colored(thumbX, y, thumbW, thumbH, ink);
            if (thumbW > 2f && thumbH > 3f) {
                float capH = thumbH - 3f;
                emitter.colored(thumbX + 1f, y + 1f + capH, thumbW - 2f, 1f, mul(thumbLedge, color));
                emitter.colored(thumbX + 1f, y + 1f, thumbW - 2f, capH, mul(thumbFace, color));
                emitter.colored(thumbX + 1f, y + 1f, thumbW - 2f, 1f, mul(thumbHighlight, color));
            }
        }
    }
}
