package dev.vfyjxf.cirrus.ui.calc.custom;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JsonCustomRecipeStore implements CustomRecipeStore {
    private static final Gson GSON = new Gson();
    private final Path file;

    public JsonCustomRecipeStore(Path file) {
        this.file = file;
    }

    @Override
    public void save(CustomRecipeEntry entry) {
        List<CustomRecipeEntry> entries = loadAll();
        entries.add(entry);
        write(entries);
    }

    @Override
    public void remove(String id) {
        List<CustomRecipeEntry> entries = loadAll();
        entries.removeIf(e -> e.id().equals(id));
        write(entries);
    }

    @Override
    public List<CustomRecipeEntry> loadAll() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            var type = new TypeToken<List<CustomRecipeEntry>>() {}.getType();
            List<CustomRecipeEntry> data = GSON.fromJson(json, type);
            return data == null ? new ArrayList<>() : new ArrayList<>(data);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void write(List<CustomRecipeEntry> entries) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(entries), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
