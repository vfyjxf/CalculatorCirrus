package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;

/**
 * Rule editor page, kept as an empty shell so the calculator tab container still has a stable
 * registration point.
 */
public final class RulesPage extends CirrusScrollablePageWidget {
    private static final int listMinWidth = 124;
    private static final int inspectorMinWidth = 132;
    private static final int detailRowHeight = 18;
    private static final int profileMetricHeight = 24;
    private static final int ruleSentenceHeight = 16;
    private static final int clauseMaterialPreviewLimit = 6;
    private static final int materialPickerPanelMinWidth = 176;
    private static final int materialPickerPanelOuterPadding = 4;
    private static final int materialPickerControlsMinWidth = 160;
    private static final int materialPickerControlsInnerPadding = 4;
    private static final int materialPickerGridGap = 1;
    private static final int materialPickerGridViewportInset = 3;

    public RulesPage() {
        super();
        // TODO(planning-ui): 重建规则编辑器（v3 RuleDocument/RuleSpec 编辑与档案切换）
    }

    public int debugRuleCount() {
        return 0;
    }

    public int debugSelectedComposableRuleCount() {
        return 0;
    }

    public boolean debugCanEditSelectedRule() {
        return false;
    }

    public int debugPlayerRuleCount() {
        return 0;
    }

    public int debugSelectedSourceRuleCount() {
        return 0;
    }

    public int debugSelectedScoreRuleCount() {
        return 0;
    }

    public int debugSelectedClauseMaterialCount(int index) {
        return 0;
    }

    public void debugCreatePlayerRule(String name) {
    }

    public void debugCopySelectedToPlayer() {
    }

    public void debugRemoveSelectedRule() {
    }

    public void debugRenameSelectedRule(String name) {
    }

    public void debugAddSourceFilter(String tag) {
    }

    public void debugAddScoreRule(String material, double amount) {
    }

    public void debugToggleSelectedClause(int index) {
    }

    public void debugRemoveSelectedClause(int index) {
    }

    public boolean debugAdvancedBuilderOpen() {
        return false;
    }

    public int debugExpandedClauseIndex() {
        return -1;
    }

    public int debugClauseMaterialPreviewLimit() {
        return clauseMaterialPreviewLimit;
    }

    public void debugToggleClauseDetails(int index) {
    }

    public void debugOpenAdvancedBuilder() {
    }

    public void debugAddSimpleRejectRule(String material) {
    }

    public void debugAddSimpleStockSensitiveRule(String material, double amount) {
    }

    public boolean debugCreatingRule() {
        return false;
    }

    public boolean debugMaterialPickerOpen() {
        return false;
    }

    public int debugMaterialPickerCandidateCount() {
        return 0;
    }

    public int debugVisibleMaterialPickerCandidateCount() {
        return 0;
    }

    public int debugFilteredMaterialPickerCandidateCount() {
        return 0;
    }

    public int debugPickedMaterialCount() {
        return 0;
    }

    public float debugMaterialPickerScrollY() {
        return 0.0f;
    }

    public void debugSetMaterialPickerScrollY(float scrollY) {
    }

    public void debugSetMaterialPickerQuery(String query) {
    }

    public boolean debugUndoMaterialPickerSelection() {
        return false;
    }

    public void debugOpenCreateRuleEditor() {
    }

    public void debugOpenMaterialPicker() {
    }

    public void debugPickMaterial(String material) {
    }

    public void debugSelectFilteredMaterials() {
    }

    public void debugClearPickedMaterials() {
    }

    public void debugApplyPickedMaterials() {
    }

    public String debugMaterialPickerPresentation() {
        return "";
    }

    public int debugMaterialPickerGridColumnsForWidth(int width) {
        return 0;
    }

    public int debugMaterialPickerGridGap() {
        return materialPickerGridGap;
    }

    public int debugMaterialPickerGridViewportInset() {
        return materialPickerGridViewportInset;
    }

    public int debugMaterialPickerGridVisibleCapacity(int width, int height) {
        return 0;
    }

    public int debugMaterialPickerControlsMinHeight() {
        return 0;
    }

    public int debugMaterialPickerControlsBodyMinHeight() {
        return 0;
    }

    public void debugSelectMaterialPickerGridRange(int from, int to, boolean selected) {
    }

    public static int ruleListMinWidth() {
        return listMinWidth;
    }

    public static int materialPickerPanelMinWidth() {
        return materialPickerPanelMinWidth;
    }

    public static int materialPickerPanelOuterPadding() {
        return materialPickerPanelOuterPadding;
    }

    public static int materialPickerControlsMinWidth() {
        return materialPickerControlsMinWidth;
    }

    public static int materialPickerControlsInnerPadding() {
        return materialPickerControlsInnerPadding;
    }

    public static int ruleInspectorMinWidth() {
        return inspectorMinWidth;
    }

    public static int detailRowHeight() {
        return detailRowHeight;
    }

    public static int profileMetricHeight() {
        return profileMetricHeight;
    }

    public static int ruleSentenceHeight() {
        return ruleSentenceHeight;
    }
}
