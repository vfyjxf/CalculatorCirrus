package dev.vfyjxf.cirrus.ui.calc.target;

import dev.vfyjxf.cirrus.ui.calc.database.DatabaseSelectionState;

import java.util.HashMap;
import java.util.Map;

public final class TargetSelectionState {
    private final DatabaseSelectionState databaseState;
    private final Map<String, Boolean> foldedBySystem = new HashMap<>();
    private String searchText = "";
    private RecipeCandidate selected;

    public TargetSelectionState(DatabaseSelectionState databaseState) {
        this.databaseState = databaseState;
    }

    public DatabaseSelectionState databaseState() {
        return databaseState;
    }

    public String searchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText == null ? "" : searchText;
    }

    public RecipeCandidate selected() {
        return selected;
    }

    public void setSelected(RecipeCandidate selected) {
        this.selected = selected;
    }

    public void setGroupFolded(String systemId, boolean folded) {
        foldedBySystem.put(systemId, folded);
    }

    public boolean isGroupFolded(String systemId) {
        return foldedBySystem.getOrDefault(systemId, false);
    }
}
