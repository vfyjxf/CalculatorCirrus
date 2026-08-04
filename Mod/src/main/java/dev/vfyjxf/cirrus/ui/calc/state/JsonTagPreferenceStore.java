package dev.vfyjxf.cirrus.ui.calc.state;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonTagPreferenceStore implements TagPreferenceStore {
    private static final Gson GSON = new Gson();
    private final Path file;

    public JsonTagPreferenceStore(Path file) {
        this.file = file;
    }

    @Override
    public void saveDefault(TagChoice choice) {
        Map<Namespace, TagChoice> current = loadDefaults();
        current.put(choice.tagId(), choice);
        write(current);
    }

    @Override
    public void removeDefault(Namespace tagId) {
        Map<Namespace, TagChoice> current = loadDefaults();
        current.remove(tagId);
        write(current);
    }

    @Override
    public Map<Namespace, TagChoice> loadDefaults() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            var type = new TypeToken<List<TagPreferenceRecord>>() {}.getType();
            List<TagPreferenceRecord> data = GSON.fromJson(json, type);
            Map<Namespace, TagChoice> result = new HashMap<>();
            if (data != null) {
                for (TagPreferenceRecord record : data) {
                    Namespace tag = Namespace.parse(record.tagId());
                    result.put(tag, new TagChoice(tag, record.optionId(), TagChoiceScope.GLOBAL_DEFAULT));
                }
            }
            return result;
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private void write(Map<Namespace, TagChoice> data) {
        try {
            Files.createDirectories(file.getParent());
            List<TagPreferenceRecord> records = data.values().stream()
                .map(choice -> new TagPreferenceRecord(choice.tagId().toString(), choice.optionId()))
                .toList();
            Files.writeString(file, GSON.toJson(records), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private record TagPreferenceRecord(String tagId, String optionId) {}
}
