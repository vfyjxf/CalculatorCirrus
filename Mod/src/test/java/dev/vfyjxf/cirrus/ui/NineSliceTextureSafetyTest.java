package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.texture.BatchableTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.NineSliceTexture;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NineSliceTextureSafetyTest {

    @Test
    void standardScrollbarTrackUsesDedicatedScrollSprite() {
        NineSliceTexture texture = CirrusTextures.Background.scroll;

        assertEquals("calculator_cirrus", texture.location().getNamespace());
        assertEquals("background/scoll", texture.location().getPath());
        assertEquals(18, texture.width());
        assertEquals(9, texture.height());
    }

    @Test
    void emitDoesNotLoopWhenMiddleSliceWidthIsZero() {
        NineSliceTexture texture = NineSliceTexture.of(ResourceLocation.parse("minecraft:test"), 7, 9, 3, 4, 3, 3);
        AtomicInteger emittedQuads = new AtomicInteger();
        BatchableTexture.VertexEmitter emitter = new BatchableTexture.VertexEmitter() {
            @Override
            public void textured(ResourceLocation textureLocation, float x, float y, float w, float h, float uMin, float vMin, float uMax, float vMax, int color) {
                emittedQuads.incrementAndGet();
            }

            @Override
            public void colored(float x, float y, float w, float h, int color) {
            }
        };

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> texture.emit(emitter, 0f, 0f, 128f, 64f, 0xFFFFFFFF));
        assertTrue(emittedQuads.get() > 0);
        assertTrue(emittedQuads.get() < 1000, "unexpected tiled explosion: " + emittedQuads.get());
    }

    @Test
    void emitDoesNotLoopWhenMiddleSliceHeightIsZero() {
        NineSliceTexture texture = NineSliceTexture.of(ResourceLocation.parse("minecraft:test"), 8, 6, 2, 2, 3, 3);
        AtomicInteger emittedQuads = new AtomicInteger();
        BatchableTexture.VertexEmitter emitter = new BatchableTexture.VertexEmitter() {
            @Override
            public void textured(ResourceLocation textureLocation, float x, float y, float w, float h, float uMin, float vMin, float uMax, float vMax, int color) {
                emittedQuads.incrementAndGet();
            }

            @Override
            public void colored(float x, float y, float w, float h, int color) {
            }
        };

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> texture.emit(emitter, 0f, 0f, 72f, 92f, 0xFFFFFFFF));
        assertTrue(emittedQuads.get() > 0);
        assertTrue(emittedQuads.get() < 1200, "unexpected tiled explosion: " + emittedQuads.get());
    }
}
