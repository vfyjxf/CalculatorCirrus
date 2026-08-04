package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;

enum ExecutionTab {
    recipe(
        CirrusLangs.Execution.Overlay.Dock.recipe,
        CirrusTextures.Icon.Execution.Category.recipe,
        CirrusTextures.Icon.Execution.Category.recipeSelected
    ),
    timeline(
        CirrusLangs.Execution.Overlay.Dock.timeline,
        CirrusTextures.Icon.Execution.Category.timeline,
        CirrusTextures.Icon.Execution.Category.timelineSelected
    ),
    ingredients(
        CirrusLangs.Execution.Overlay.Dock.ingredients,
        CirrusTextures.Icon.Execution.Category.ingredients,
        CirrusTextures.Icon.Execution.Category.ingredientsSelected
    );

    //region state

    private final LangEntry title;
    private final ImageTexture icon;
    private final ImageTexture selectedIcon;
    //endregion

    ExecutionTab(LangEntry title, ImageTexture icon, ImageTexture selectedIcon) {
        this.title = title;
        this.icon = icon;
        this.selectedIcon = selectedIcon;
    }

    //region access

    LangEntry title() {
        return title;
    }

    ImageTexture icon() {
        return icon;
    }

    ImageTexture selectedIcon() {
        return selectedIcon;
    }
    //endregion
}
