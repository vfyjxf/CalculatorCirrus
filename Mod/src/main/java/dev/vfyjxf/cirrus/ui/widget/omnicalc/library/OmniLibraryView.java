package dev.vfyjxf.cirrus.ui.widget.omnicalc.library;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Recipe Library / Database page with three-column layout:
 * filter tree (left), recipe card grid (center), detail panel (right).
 */
public class OmniLibraryView extends CompositeWidget<Widget> {

    // ── Sample recipe data ──
    private static final String[][] RECIPES = {
            {"Electronic Circuit", "Assembler", "Industrial", "2 in \u2192 1 out"},
            {"Advanced Circuit", "Assembler", "Industrial", "3 in \u2192 1 out"},
            {"Integrated Processor", "Assembly Line", "GregTech", "4 in \u2192 1 out"},
            {"NAND Chip", "Laser Engraver", "GregTech", "2 in \u2192 1 out"},
            {"Data Module", "Assembler", "Industrial", "3 in \u2192 1 out"},
            {"Quantum Core", "Cleanroom Assembler", "GregTech", "6 in \u2192 1 out"},
            {"ULV Motor", "Assembly Line", "GregTech", "5 in \u2192 1 out"},
            {"Certus Crystal", "Inscriber", "Applied E.", "2 in \u2192 1 out"},
    };
    private static final int[] DOMAIN_COLORS = {ACCENT_LIGHT, ORANGE, PURPLE};

    public OmniLibraryView() {
        useStyle(flexRow(), sizeFull());

        // ═══════════ Left filter panel ═══════════
        var filterPanel = new WidgetGroup<>();
        filterPanel.useStyle(flexColumn(), widthOf(55), paddingHorizontal(3), paddingVertical(4));
        filterPanel.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.fill(0, 0, w, h, BG_PANEL);
            canvas.fill(w - 1, 0, 1, h, BORDER_MAIN);
        });

        // Title
        var libTitle = LabelWidget.of(Component.literal("LIBRARY"))
                .setColor(TEXT_PRIMARY).setShadow(true);
        libTitle.useStyle(heightOf(10), marginBottom(4));
        filterPanel.addWidget(libTitle);

        // "All Recipes" active button
        var allBtn = new Widget();
        allBtn.useStyle(heightOf(10), marginBottom(3));
        allBtn.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 10, 3f, ACCENT_BG, 1f, ACCENT_BORDER);
            canvas.drawString("\u25C9 All Recipes", 3, 2, ACCENT_TEXT);
            canvas.drawString("42", w - 12, 2, TEXT_WEAK);
        });
        filterPanel.addWidget(allBtn);

        // Divider
        var div = new Widget();
        div.useStyle(heightOf(1), marginVertical(2));
        div.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, self.width(), 1, BORDER_MAIN));
        filterPanel.addWidget(div);

        // "DOMAINS" section header
        var domainsLabel = LabelWidget.of(Component.literal("DOMAINS"))
                .setColor(TEXT_WEAK).setShadow(true);
        domainsLabel.useStyle(heightOf(7), marginBottom(2));
        filterPanel.addWidget(domainsLabel);

        // Domain tree items with counts
        String[][] domains = {
                {"Industrial Tech", "18"}, {"GregTech", "15"}, {"Applied E.", "9"}
        };
        int[] domainColorList = {ACCENT_LIGHT, ORANGE_TEXT, PURPLE_TEXT};
        for (int i = 0; i < domains.length; i++) {
            final int di = i;
            var domainItem = new Widget();
            domainItem.useStyle(heightOf(9), marginLeft(2), marginBottom(1));
            final String dName = domains[i][0];
            final String dCount = domains[i][1];
            domainItem.onRender((canvas, mx, my, pt, self, ctx) -> {
                int w = self.width();
                if (self.hovered()) {
                    canvas.roundedRect(0, 0, w, 9, 2f, 0x15FFFFFF, 0, 0);
                }
                canvas.circle(5f, 4.5f, 2f, domainColorList[di]);
                canvas.drawString(dName, 10, 1, TEXT_SECONDARY);
                canvas.drawString(dCount, w - 10, 1, TEXT_WEAK);
            });
            filterPanel.addWidget(domainItem);
        }

        addWidget(filterPanel);

        // ═══════════ Center content panel ═══════════
        var centerPanel = new WidgetGroup<>();
        centerPanel.useStyle(flexColumn(), flexGrow(1), minWidth(0), paddingHorizontal(4), paddingVertical(4));

        // Search bar (rounded)
        var searchField = TextFieldWidget.create("").setPlaceholder("\u2315 Ctrl+K  Search recipes...");
        searchField.useStyle(heightOf(12), marginBottom(3));
        centerPanel.addWidget(searchField);

        // Toolbar: sort + view + filter
        var toolbar = new WidgetGroup<>();
        toolbar.useStyle(flexRow(), alignItemsCenter(), gap(2), marginBottom(3));

        var sortWidget = new Widget();
        sortWidget.useStyle(sizeOf(36, 9));
        sortWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.roundedRect(0, 0, 36, 9, 2f, BG_HOVER, 1f, BORDER_SEC);
            canvas.drawString("Sort: A-Z \u25BE", 3, 1, TEXT_SECONDARY);
        });
        toolbar.addWidget(sortWidget);

        toolbar.addWidget(SpacerWidget.create());

        var countLabel = LabelWidget.of(Component.literal("42 recipes found"))
                .setColor(TEXT_WEAK).setShadow(true);
        countLabel.useStyle(heightOf(7));
        toolbar.addWidget(countLabel);
        centerPanel.addWidget(toolbar);

        // Recipe card grid (2 cards per row)
        var cardGrid = new WidgetGroup<>();
        cardGrid.useStyle(flexColumn(), gap(3), flexGrow(1), minHeight(0));

        for (int r = 0; r < (RECIPES.length + 1) / 2; r++) {
            var row = new WidgetGroup<>();
            row.useStyle(flexRow(), gap(3), heightOf(32), flexShrink(0));
            row.addWidget(createRecipeCard(r * 2));
            if (r * 2 + 1 < RECIPES.length) {
                row.addWidget(createRecipeCard(r * 2 + 1));
            }
            cardGrid.addWidget(row);
        }
        centerPanel.addWidget(cardGrid);

        addWidget(centerPanel);

        // ═══════════ Right detail panel ═══════════
        var detailPanel = new WidgetGroup<>();
        detailPanel.useStyle(
                flexColumn(), widthOf(65), paddingHorizontal(4), paddingVertical(4),
                alignItemsCenter(), justifyCenter()
        );
        detailPanel.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            canvas.fill(0, 0, w, h, BG_PANEL);
            canvas.fill(0, 0, 1, h, BORDER_MAIN);
        });

        // Placeholder icon
        var iconWidget = new Widget();
        iconWidget.useStyle(sizeOf(16, 16), marginBottom(4));
        iconWidget.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.roundedRect(2, 2, 12, 12, 3f, BG_HOVER, 1f, BORDER_SEC));
        detailPanel.addWidget(iconWidget);

        var detailLabel = LabelWidget.of(Component.literal("Select a recipe"))
                .setColor(TEXT_WEAK).setShadow(true);
        detailLabel.useStyle(heightOf(8), marginBottom(2));
        detailPanel.addWidget(detailLabel);

        var detailLabel2 = LabelWidget.of(Component.literal("to view details"))
                .setColor(TEXT_WEAK).setShadow(true);
        detailLabel2.useStyle(heightOf(8));
        detailPanel.addWidget(detailLabel2);

        addWidget(detailPanel);
    }

    private Widget createRecipeCard(int index) {
        String name = RECIPES[index][0];
        String machine = RECIPES[index][1];
        String domain = RECIPES[index][2];
        String io = RECIPES[index][3];
        int domainIdx = domain.equals("Industrial") ? 0 : domain.equals("GregTech") ? 1 : 2;
        int topBorderColor = DOMAIN_COLORS[domainIdx];

        var card = new Widget();
        // Each card fills half the row width
        card.useStyle(flexGrow(1), heightOf(32), minWidth(0));
        card.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            int border = self.hovered() ? BORDER_SEC : BORDER_MAIN;
            // Card background
            canvas.roundedRect(0, 0, w, h, 3f, BG_HOVER, 1f, border);
            // Colored top border
            canvas.roundedRect(0, 0, w, 2, 3f, 3f, 0f, 0f, topBorderColor, 0, 0);
            // Recipe name
            canvas.drawString(name, 3, 5, TEXT_PRIMARY);
            // Machine
            canvas.drawString(machine, 3, 14, TEXT_SECONDARY);
            // IO summary
            canvas.drawString(io, 3, 22, TEXT_WEAK);
            // Domain badge
            OmniCalcColors.drawBadge(canvas, w - domain.length() * 4 - 6, 5, domain,
                    domainIdx == 0 ? ACCENT_BG : domainIdx == 1 ? 0x26F97316 : PURPLE_BG,
                    topBorderColor);
        });

        return card;
    }
}
