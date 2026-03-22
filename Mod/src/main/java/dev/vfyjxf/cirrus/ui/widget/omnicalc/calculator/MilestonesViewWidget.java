package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Factory Kanban Board / Milestones view.
 * Styled with rounded phase headers, step cards with manual checkboxes,
 * and an overall progress bar matching omnicalc-pro's milestones view.
 */
public class MilestonesViewWidget extends CompositeWidget<Widget> {

    private static final String[] PHASE_NAMES = {
            "RAW EXTRACTION", "PROCESSING", "SUB-ASSEMBLY",
            "INTEGRATION", "FINAL ASSEMBLY"
    };

    public MilestonesViewWidget() {
        useStyle(
                flexColumn(),
                sizeFull(),
                paddingHorizontal(6),
                paddingVertical(4)
        );

        onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, width(), height(), BG_PRIMARY));

        // ── Header row ──
        var titleRow = new WidgetGroup<>();
        titleRow.useStyle(flexRow(), alignItemsCenter(), gap(4), marginBottom(1));

        var title = LabelWidget.of(Component.literal("\u2611 Factory Kanban Board"))
                .setColor(TEXT_PRIMARY).setShadow(true);
        title.useStyle(heightOf(10));
        titleRow.addWidget(title);

        titleRow.addWidget(SpacerWidget.create());

        var pctLabel = LabelWidget.of(Component.literal("0%"))
                .setColor(TEXT_SECONDARY).setShadow(true);
        pctLabel.useStyle(heightOf(8));
        titleRow.addWidget(pctLabel);
        addWidget(titleRow);

        // Step count
        var stepsLabel = LabelWidget.of(Component.literal("0 / 145 STEPS"))
                .setColor(TEXT_WEAK).setShadow(true);
        stepsLabel.useStyle(heightOf(7), marginBottom(2));
        addWidget(stepsLabel);

        // Overall progress bar (rounded)
        var progressWidget = new Widget();
        progressWidget.useStyle(heightOf(3), marginBottom(4));
        progressWidget.onRender((canvas, mx, my, pt, self, ctx) ->
                OmniCalcColors.drawProgressBar(canvas, 0, 0, self.width(), 3, 0f, BG_HOVER, ACCENT));
        addWidget(progressWidget);

        // ── Phase sections ──
        int[][] phaseConfig = {{35, 5}, {28, 4}, {22, 3}};
        for (int p = 0; p < phaseConfig.length; p++) {
            addWidget(createPhaseSection(p, phaseConfig[p][0], phaseConfig[p][1]));
        }
    }

    private Widget createPhaseSection(int phase, int totalSteps, int visibleSteps) {
        var section = new WidgetGroup<>();
        section.useStyle(flexColumn(), marginBottom(3));

        // Phase header — rounded with step count
        final String phaseName = PHASE_NAMES[phase % PHASE_NAMES.length];
        var phaseHeader = new Widget();
        phaseHeader.useStyle(heightOf(12));
        phaseHeader.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.roundedRect(0, 0, w, 12, 3f, BG_HOVER, 1f, BORDER_MAIN);
            canvas.drawString("PHASE " + (phase + 1) + ": " + phaseName, 4, 3, TEXT_PRIMARY);
            String count = "0/" + totalSteps;
            canvas.drawString(count, w - count.length() * 6 - 4, 3, TEXT_SECONDARY);
        });
        section.addWidget(phaseHeader);

        // Phase progress bar
        var phaseProgress = new Widget();
        phaseProgress.useStyle(heightOf(2), marginHorizontal(2), marginTop(1));
        phaseProgress.onRender((canvas, mx, my, pt, self, ctx) ->
                OmniCalcColors.drawProgressBar(canvas, 0, 0, self.width(), 2, 0f, BG_PRIMARY, GREEN));
        section.addWidget(phaseProgress);

        // Step cards
        for (int s = 0; s < visibleSteps; s++) {
            final int stepNum = phase * 10 + s + 1;
            var stepWidget = new Widget();
            stepWidget.useStyle(heightOf(11), marginHorizontal(2), marginTop(1));
            stepWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
                int w = self.width();
                int bg = self.hovered() ? BG_HOVER : BG_PANEL;
                canvas.roundedRect(0, 0, w, 11, 2f, bg, 1f, BORDER_MAIN);
                // Checkbox (rounded square)
                canvas.roundedRect(3, 2, 7, 7, 1.5f, BG_PRIMARY, 1f, BORDER_SEC);
                // Machine name
                canvas.drawString("Machine-" + stepNum, 13, 2, TEXT_PRIMARY);
                // Tier badge
                OmniCalcColors.drawBadge(canvas, w - 38, 2, "ULV", GREEN_DARK_BG, GREEN_TEXT);
                // Step number
                canvas.drawString("#" + stepNum, w - 14, 2, TEXT_WEAK);
            });
            section.addWidget(stepWidget);
        }

        return section;
    }
}
