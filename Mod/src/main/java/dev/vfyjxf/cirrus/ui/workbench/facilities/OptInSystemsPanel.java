package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Opt-in system unlocks: scans the graph for systems enabled only on demand and writes them straight into the facility registry.
 */
public class OptInSystemsPanel extends FacilitiesAndRulesWorkspace.Panel {

    public OptInSystemsPanel(FacilitiesHost host, Supplier<CirrusPalette> palette) {
        super(host, palette);
        refresh();
    }

    @Override
    protected void buildContent() {
        FacilitySnapshot snapshot = host().facilitySnapshot();
        Map<Namespace, Integer> recipeCounts = recipeCountsBySystem();
        List<Namespace> optIn = optInSystems();
        content().addWidget(
            row(
                value(CirrusLangs.Workbench.Facilities.optIn),
                fixed(Component.literal(Integer.toString(optIn.size())), 20, CirrusPalette::textMuted)
            )
        );
        int visible = 0;
        for (Namespace systemId : optIn) {
            if (!matches(systemId.toString())) {
                continue;
            }
            content().addWidget(
                systemCard(
                    systemId,
                    recipeCounts.getOrDefault(systemId, 0),
                    snapshot.unlockedSystems().contains(systemId)
                )
            );
            visible++;
        }
        if (visible == 0) {
            content().addWidget(muted(le("empty_opt_in").component()));
        }
    }

    private Widget systemCard(Namespace systemId, int recipeCount, boolean unlocked) {
        PanelCard card = card();
        card.addWidget(row(value(Component.literal(systemId.toString())), muted(tr("recipe_count", recipeCount))));
        WidgetGroup<Widget> hint = row(muted(le("opt_in_hint").component()));
        hint.useStyle(flexGrow(1), minWidth(0));
        card.addWidget(hint);
        card.addWidget(
            row(muted(le("unlock").component()), toggle(() -> unlocked, enabled -> setUnlocked(systemId, enabled)))
        );
        return card;
    }

    private List<Namespace> optInSystems() {
        List<Namespace> known = host().systemIds();
        var graph = host().graph();
        if (graph.isEmpty()) {
            return List.of();
        }
        CompiledRecipeGraph compiled = graph.get();
        List<Namespace> optIn = new ArrayList<>();
        for (Namespace systemId : known) {
            if (compiled.admissionOf(systemId) == SystemAdmission.optInOnly) {
                optIn.add(systemId);
            }
        }
        return List.copyOf(optIn);
    }

    private Map<Namespace, Integer> recipeCountsBySystem() {
        Map<Namespace, Integer> counts = new HashMap<>();
        Set<ComponentStackKey> visited = new HashSet<>();
        host().graph().ifPresent(graph -> {
            for (ComponentStackKey resource : graph.allProducibleResources()) {
                if (!visited.add(resource)) {
                    continue;
                }
                for (CompiledRecipe recipe : graph.producersOf(resource)) {
                    counts.merge(recipe.key().systemId(), 1, Integer::sum);
                }
            }
        });
        return counts;
    }

    private void setUnlocked(Namespace systemId, boolean unlocked) {
        if (unlocked) {
            host().registry().unlockSystem(systemId);
        } else {
            host().registry().lockSystem(systemId);
        }
        refresh();
    }
}
