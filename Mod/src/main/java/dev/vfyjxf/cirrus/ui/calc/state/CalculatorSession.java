package dev.vfyjxf.cirrus.ui.calc.state;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CalculatorSession {
    private final Map<Namespace, TagChoice> runChoices = new ConcurrentHashMap<>();
    private final Map<Namespace, TagChoice> globalDefaults = new ConcurrentHashMap<>();

    public void setRunChoice(Namespace tag, TagChoice choice) {
        runChoices.put(tag, choice);
    }

    public void setGlobalDefault(Namespace tag, TagChoice choice) {
        globalDefaults.put(tag, choice);
    }

    public void applyDefaults(Map<Namespace, TagChoice> defaults) {
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        globalDefaults.putAll(defaults);
    }

    public @Nullable TagChoice resolveTagChoice(Namespace tag) {
        TagChoice run = runChoices.get(tag);
        return run != null ? run : globalDefaults.get(tag);
    }

    public Map<Namespace, TagChoice> runChoices() {
        return Map.copyOf(runChoices);
    }

    public Map<Namespace, TagChoice> globalDefaults() {
        return Map.copyOf(globalDefaults);
    }

    public void removeRunChoice(Namespace tagId) {
        runChoices.remove(tagId);
    }

    public void removeGlobalDefault(Namespace tagId) {
        globalDefaults.remove(tagId);
    }

    public void clearRunChoices() {
        runChoices.clear();
    }
}
