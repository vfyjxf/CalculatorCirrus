package dev.vfyjxf.cirrus.testmod;

import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Classpath manifest emitted by {@code datagen/TestDataGenerator}: every raw
 * content id (item, fluid, recipe system) referenced by the replayed export
 * pack. {@link TestMod} registers a mimic entry per id at mod construction;
 * {@link TestDataPlugin} registers one in-memory recipe system per manifest
 * system.
 */
public record MimicManifest(
    @Nullable String pack,
    @Nullable List<String> items,
    @Nullable List<String> fluids,
    @Nullable List<MimicSystem> systems
) {

    public record MimicSystem(String id, @Nullable String machine) {
    }

    private static final MimicManifest EMPTY = new MimicManifest(null, List.of(), List.of(), List.of());

    private static volatile MimicManifest cached;

    public static MimicManifest load() {
        MimicManifest current = cached;
        if (current == null) {
            synchronized (MimicManifest.class) {
                current = cached;
                if (current == null) {
                    current = read();
                    cached = current;
                }
            }
        }
        return current;
    }

    public List<String> itemIds() {
        return items == null ? List.of() : items;
    }

    public List<String> fluidIds() {
        return fluids == null ? List.of() : fluids;
    }

    public List<MimicSystem> systemList() {
        return systems == null ? List.of() : systems;
    }

    private static MimicManifest read() {
        InputStream stream = MimicManifest.class.getResourceAsStream("/data/cirrus_test/mimic_manifest.json");
        if (stream == null) {
            return EMPTY;
        }
        try (stream) {
            MimicManifest manifest = new Gson().fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8), MimicManifest.class
            );
            return manifest == null ? EMPTY : manifest;
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load mimic_manifest.json", exception);
        }
    }
}
