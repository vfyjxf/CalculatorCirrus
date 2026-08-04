package dev.vfyjxf.cirrus.api.recipe.codec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CodecJsonIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CodecJsonIO() {
    }

    public static <T> T read(Path file, Codec<T> codec) throws IOException {
        JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        return codec.parse(JsonOps.INSTANCE, element)
            .result()
            .orElseThrow(() -> new IOException("Failed to decode " + file));
    }

    public static <T> void write(Path file, Codec<T> codec, T value) throws IOException {
        JsonElement element = codec.encodeStart(JsonOps.INSTANCE, value)
            .result()
            .orElseThrow(() -> new IOException("Failed to encode " + file));
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, GSON.toJson(element), StandardCharsets.UTF_8);
    }
}
