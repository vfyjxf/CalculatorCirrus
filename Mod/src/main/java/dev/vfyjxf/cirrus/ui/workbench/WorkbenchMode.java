package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import java.util.Locale;

/**
 * The four top-level workbench workspaces.
 */
public enum WorkbenchMode {
    planner, facilities, database, settings;

    private final LangEntry labelKey = LangEntry.of("cirrus.ui.workbench.mode." + name().toLowerCase(Locale.ROOT));

    public LangEntry labelKey() {
        return labelKey;
    }
}
