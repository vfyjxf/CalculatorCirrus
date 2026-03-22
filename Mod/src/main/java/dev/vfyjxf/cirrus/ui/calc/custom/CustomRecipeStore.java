package dev.vfyjxf.cirrus.ui.calc.custom;

import java.util.List;

public interface CustomRecipeStore {
    void save(CustomRecipeEntry entry);

    void remove(String id);

    List<CustomRecipeEntry> loadAll();
}
