package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.resources.ResourceLocation;

public enum WorkbenchThemeMode {
    obsidianBlueprint("obsidian_blueprint"), atelierStone("atelier_stone");

    private final String id;

    WorkbenchThemeMode(String id) {
        this.id = id;
    }

    /** The css theme directory under {@code assets/calculator_cirrus/ui/themes/}. */
    public ResourceLocation themeId() {
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, id);
    }

    public LangEntry labelKey() {
        return LangEntry.of("cirrus.ui.workbench.theme." + id);
    }

    public static WorkbenchThemeMode defaultMode() {
        return obsidianBlueprint;
    }
}
