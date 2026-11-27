package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Factory floor: owned machine cards grouped by machine capability, with count nudges, speed readouts, auto-feed and new-machine declaration.
 */
public class OwnedMachinesFloorPanel extends FacilitiesAndRulesWorkspace.Panel {

    public static final int gaugeSize = 22;
    public static final int spinnerButtonWidth = 18;
    public static final int pickerLimit = 96;
    public static final int machineCardWidth = 310;
    private static final double speedGaugeCeiling = 4.0;

    private boolean pickerOpen;
    private String pickerQuery = "";

    public OwnedMachinesFloorPanel(FacilitiesHost host, Supplier<CirrusPalette> palette) {
        super(host, palette);
        refresh();
    }

    public boolean pickerOpen() {
        return pickerOpen;
    }

    public void setPickerQuery(String query) {
        String next = query == null ? "" : query.trim();
        if (!next.equals(pickerQuery)) {
            pickerQuery = next;
            refresh();
        }
    }

    @Override
    protected void buildContent() {
        FacilitySnapshot snapshot = host().facilitySnapshot();
        List<OwnedMachineGroup> groups = snapshot.ownedMachines();
        content().addWidget(header(groups.size()));
        if (pickerOpen) {
            content().addWidget(picker());
        }
        int visible = 0;
        for (Map.Entry<String, List<OwnedMachineGroup>> entry : byCapability(groups).entrySet()) {
            List<OwnedMachineGroup> matched = new ArrayList<>();
            for (OwnedMachineGroup group : entry.getValue()) {
                if (matchesAny(filter(), group.machineId(), entry.getKey())) {
                    matched.add(group);
                }
            }
            if (matched.isEmpty()) {
                continue;
            }
            content().addWidget(caption(Component.literal(CoPilotPaint.humanizeId(entry.getKey()))));
            WidgetGroup<Widget> grid = new WidgetGroup<>();
            grid.useStyle(flexRow(), flexWrap(), gap(6), minWidth(0));
            for (OwnedMachineGroup group : matched) {
                grid.addWidget(machineCard(group));
                visible++;
            }
            content().addWidget(grid);
        }
        if (visible == 0) {
            content().addWidget(muted(le("empty_machines").component()));
        }
    }

    private Widget header(int total) {
        WidgetGroup<Widget> head = row(
            value(CirrusLangs.Workbench.Facilities.floorMachines),
            fixed(Component.literal(Integer.toString(total)), 20, CirrusPalette::textMuted)
        );
        head.addWidget(button(le("declare_machine"), 96, () -> {
            pickerOpen = !pickerOpen;
            refresh();
        }));
        return head;
    }

    private Widget picker() {
        PanelCard card = card();
        card.addWidget(row(searchInput(le("machine_search"), this::setPickerQuery)));
        List<MachineCandidate> candidates = machineCandidates();
        WidgetGroup<Widget> grid = new WidgetGroup<>();
        grid.useStyle(flexRow(), flexWrap(), gap(2), minWidth(0));
        int shown = 0;
        for (MachineCandidate candidate : candidates) {
            if (shown >= pickerLimit) {
                break;
            }
            if (!matchesAny(pickerQuery, candidate.machineId())) {
                continue;
            }
            grid.addWidget(button(Component.literal(candidate.shortId()), 84, () -> declare(candidate)));
            shown++;
        }
        if (shown == 0) {
            card.addWidget(muted(le("no_graph").component()));
        } else {
            card.addWidget(grid);
        }
        return card;
    }

    private Widget machineCard(OwnedMachineGroup group) {
        PanelCard card = card();
        card.useStyle(widthOf(machineCardWidth), flexShrink(0));
        card.richTooltip(
            () -> OreUiRichTooltip.builder(StepNodeCardRenderer.machineNameOf(group.machineId(), null))
                    .icon(ResourceKey.item(group.machineId()))
                    .badge(
                        group.automatedFeeding() ? "AUTO FEED" : "MANUAL",
                        group.automatedFeeding() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                    ).subtitle(CoPilotPaint.humanizeId(group.capability().id()))
                    .meter(
                        "Passive Speed",
                        Math.min(1.0, group.passiveSpeedMultiplier() / speedGaugeCeiling),
                        "\u00d7" + formatMultiplier(group.passiveSpeedMultiplier()),
                        group.passiveSpeedMultiplier() >= 2.0
                                ? OreUiRichTooltip.Tone.positive
                                : OreUiRichTooltip.Tone.costP
                    ).keyValue("Owned Units", String.valueOf(group.count()), OreUiRichTooltip.Tone.primary)
                    .footer("Adjust unit count or toggle auto-feed below").build()
        );
        RingWidget gauge = RingWidget
                .of(() -> Math.max(0.0, Math.min(1.0, group.passiveSpeedMultiplier() / speedGaugeCeiling)))
                .setThickness(3);
        gauge.addStyleClass(group.passiveSpeedMultiplier() >= 2.0 ? "ok" : "info");
        gauge.useStyle(sizeOf(gaugeSize, gaugeSize), flexShrink(0));

        WidgetGroup<Widget> titleCol = new WidgetGroup<>();
        titleCol.useStyle(flexColumn(), flexGrow(1), heightOf(24), minWidth(0), gap(2));
        var nameLabel = value(StepNodeCardRenderer.machineNameOf(group.machineId(), null));
        nameLabel.useStyle(heightOf(11), flexShrink(0), flexGrow(0));
        var speedLabel = muted(tr("speed_multiplier", formatMultiplier(group.passiveSpeedMultiplier())));
        speedLabel.useStyle(heightOf(10), flexShrink(0), flexGrow(0));
        titleCol.addWidget(nameLabel);
        titleCol.addWidget(speedLabel);

        WidgetGroup<Widget> topRow = row(new Glyph(group.machineId(), paletteSupplier(), glyphSize), titleCol, gauge);
        topRow.useStyle(heightOf(26));
        card.addWidget(topRow);

        WidgetGroup<Widget> controls = row(
            microButton("-", spinnerButtonWidth, () -> setCount(group, group.count() - 1)),
            fixed(Component.literal(Integer.toString(group.count())), 18, CirrusPalette::textPrimary),
            microButton("+", spinnerButtonWidth, () -> setCount(group, group.count() + 1)),
            fixed(tr("units", group.count()), 44, CirrusPalette::textMuted),
            fixed(le("auto_feed").component(), 52, CirrusPalette::textSecondary),
            toggle(group::automatedFeeding, enabled -> setFeeding(group, enabled)),
            button(le("delete"), 48, () -> remove(group))
        );
        card.addWidget(controls);
        return card;
    }

    private void setCount(OwnedMachineGroup group, int count) {
        host().registry().declareOwnedMachine(
            group.machineId(),
            group.capability(),
            Math.max(0, count),
            group.passiveSpeedMultiplier(),
            group.automatedFeeding()
        );
        refresh();
    }

    private void setFeeding(OwnedMachineGroup group, boolean enabled) {
        host().registry().declareOwnedMachine(
            group.machineId(),
            group.capability(),
            group.count(),
            group.passiveSpeedMultiplier(),
            enabled
        );
        refresh();
    }

    private void remove(OwnedMachineGroup group) {
        host().registry().declareOwnedMachine(group.machineId(), group.capability(), 0, 1.0, false);
        refresh();
    }

    private void declare(MachineCandidate candidate) {
        host().registry().declareOwnedMachine(candidate.machineId(), candidate.capability(), 1, 1.0, false);
        pickerOpen = false;
        refresh();
    }

    private List<MachineCandidate> machineCandidates() {
        Map<String, MachineCandidate> candidates = new LinkedHashMap<>();
        host().graph().ifPresent(graph -> {
            for (ResourceKey resource : graph.allProducibleResources()) {
                for (CompiledRecipe recipe : graph.producersOf(resource)) {
                    MachineRequirement requirement = recipe.machineRequirement();
                    MachineCapabilityId capability = requirement.capabilityId().orElse(null);
                    for (String machineId : requirement.compatibleMachineIds()) {
                        if (machineId == null || machineId.isBlank()) {
                            continue;
                        }
                        MachineCapabilityId resolved = capability == null ? inferredCapability(machineId) : capability;
                        candidates.putIfAbsent(machineId, new MachineCandidate(machineId, resolved));
                    }
                }
            }
        });
        return List.copyOf(candidates.values());
    }

    private static MachineCapabilityId inferredCapability(String machineId) {
        return new MachineCapabilityId("cirrus:capability/machine/" + machineId.replace(':', '/'));
    }

    private static Map<String, List<OwnedMachineGroup>> byCapability(List<OwnedMachineGroup> groups) {
        Map<String, List<OwnedMachineGroup>> grouped = new TreeMap<>();
        for (OwnedMachineGroup group : groups) {
            grouped.computeIfAbsent(group.capability().id(), ignored -> new ArrayList<>()).add(group);
        }
        for (List<OwnedMachineGroup> value : grouped.values()) {
            value.sort((left, right) -> left.machineId().compareTo(right.machineId()));
        }
        return grouped;
    }

    private static String formatMultiplier(double multiplier) {
        return WorkbenchFormatters.formatSigned(multiplier, 2);
    }

    private record MachineCandidate(String machineId, MachineCapabilityId capability) {
        private String shortId() {
            int colon = machineId.indexOf(':');
            return colon < 0 ? machineId : machineId.substring(colon + 1);
        }
    }
}
