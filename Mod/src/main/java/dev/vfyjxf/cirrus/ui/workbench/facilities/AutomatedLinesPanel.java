package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Automated lines: output resource, throughput, buffer, pull duration and enabled state.
 */
public class AutomatedLinesPanel extends FacilitiesAndRulesWorkspace.Panel {

    public static final double defaultThroughputPerMinute = 60.0;
    public static final double defaultPullSeconds = 2.0;
    public static final long defaultBuffer = 64L;
    public static final double throughputStep = 10.0;
    public static final int pickerLimit = 96;

    private boolean pickerOpen;
    private String pickerQuery = "";

    public AutomatedLinesPanel(FacilitiesHost host, Supplier<CirrusPalette> palette) {
        super(host, palette);
        refresh();
    }

    public boolean pickerOpen() {
        return pickerOpen;
    }

    @Override
    protected void buildContent() {
        FacilitySnapshot snapshot = host().facilitySnapshot();
        List<AutomatedLine> lines = new ArrayList<>(snapshot.automatedLines());
        lines.sort((left, right) -> left.lineId().compareTo(right.lineId()));
        content().addWidget(header(lines.size()));
        if (pickerOpen) {
            content().addWidget(picker());
        }
        int visible = 0;
        for (AutomatedLine line : lines) {
            if (!matchesAny(filter(), line.lineId(), line.output().displayId())) {
                continue;
            }
            content().addWidget(lineCard(line));
            visible++;
        }
        if (visible == 0) {
            content().addWidget(muted(le("empty_lines").component()));
        }
    }

    private Widget header(int total) {
        WidgetGroup<Widget> head = row(
            value(CirrusLangs.Workbench.Facilities.lines),
            fixed(Component.literal(Integer.toString(total)), 20, CirrusPalette::textMuted)
        );
        head.addWidget(button(le("new_line"), 68, () -> {
            pickerOpen = !pickerOpen;
            refresh();
        }));
        return head;
    }

    private Widget picker() {
        PanelCard card = card();
        card.addWidget(row(searchInput(le("machine_search"), query -> {
            String next = query == null ? "" : query.trim();
            if (!next.equals(pickerQuery)) {
                pickerQuery = next;
                refresh();
            }
        })));
        List<ComponentStackKey> outputs = new ArrayList<>();
        host().graph().ifPresent(graph -> outputs.addAll(graph.allProducibleResources()));
        outputs.sort(ComponentStackKey::compareTo);
        WidgetGroup<Widget> grid = new WidgetGroup<>();
        grid.useStyle(flexRow(), flexWrap(), gap(2), minWidth(0));
        int shown = 0;
        for (ComponentStackKey output : outputs) {
            if (shown >= pickerLimit) {
                break;
            }
            if (!matchesAny(pickerQuery, output.displayId())) {
                continue;
            }
            grid.addWidget(button(Component.literal(output.displayId()), 96, () -> addLine(output)));
            shown++;
        }
        if (shown == 0) {
            card.addWidget(muted(le("no_graph").component()));
        } else {
            card.addWidget(grid);
        }
        return card;
    }

    private Widget lineCard(AutomatedLine line) {
        PanelCard card = card();
        card.addWidget(
            row(
                new Glyph(line.output(), paletteSupplier(), glyphSize),
                value(Component.literal(line.lineId())),
                fixed(
                    tr("throughput", WorkbenchFormatters.formatSigned(line.throughputPerMinute(), 1)),
                    64,
                    CirrusPalette::costS
                )
            )
        );
        card.addWidget(
            row(
                muted(tr("buffer_cap", Long.toString(line.bufferedStock()))),
                muted(tr("pull_cost", WorkbenchFormatters.formatSeconds(line.humanAttentionSecondsPerPull())))
            )
        );
        card.addWidget(
            row(
                microButton("-", 14, () -> rescale(line, -throughputStep)),
                microButton("+", 14, () -> rescale(line, throughputStep)),
                muted(le("line_enabled").component()),
                toggle(line::continuous, enabled -> setContinuous(line, enabled)),
                button(le("delete"), 52, () -> remove(line))
            )
        );
        return card;
    }

    private void addLine(ComponentStackKey output) {
        String suffix = output.displayId().replace(':', '/');
        String baseId = "line:" + suffix;
        FacilitySnapshot snapshot = host().facilitySnapshot();
        String lineId = baseId;
        int index = 1;
        while (isLineIdTaken(snapshot, lineId)) {
            index++;
            lineId = baseId + "#" + index;
        }
        host().registry().putAutomatedLine(
            new AutomatedLine(lineId, output, defaultThroughputPerMinute, defaultPullSeconds, defaultBuffer, true)
        );
        pickerOpen = false;
        refresh();
    }

    private static boolean isLineIdTaken(FacilitySnapshot snapshot, String lineId) {
        for (AutomatedLine existing : snapshot.automatedLines()) {
            if (existing.lineId().equals(lineId)) {
                return true;
            }
        }
        return false;
    }

    private void rescale(AutomatedLine line, double delta) {
        double next = Math.max(1.0, line.throughputPerMinute() + delta);
        host().registry().putAutomatedLine(
            new AutomatedLine(
                line.lineId(),
                line.output(),
                next,
                line.humanAttentionSecondsPerPull(),
                line.bufferedStock(),
                line.continuous()
            )
        );
        refresh();
    }

    private void setContinuous(AutomatedLine line, boolean enabled) {
        host().registry().putAutomatedLine(
            new AutomatedLine(
                line.lineId(),
                line.output(),
                line.throughputPerMinute(),
                line.humanAttentionSecondsPerPull(),
                line.bufferedStock(),
                enabled
            )
        );
        refresh();
    }

    private void remove(AutomatedLine line) {
        host().registry().removeAutomatedLine(line.lineId());
        refresh();
    }
}
