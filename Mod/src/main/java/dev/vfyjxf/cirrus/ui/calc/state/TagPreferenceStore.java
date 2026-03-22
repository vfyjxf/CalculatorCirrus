package dev.vfyjxf.cirrus.ui.calc.state;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface TagPreferenceStore {
    void saveDefault(@NotNull TagChoice choice);

    void removeDefault(@NotNull Namespace tagId);

    Map<Namespace, TagChoice> loadDefaults();
}
