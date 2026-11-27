package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.planning.context.AutomatedRecipeCapability;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Automated recipes: input-to-output thumbnail, per-batch duration and parallel slot count, importable from the current session.
 */
public class AutomatedRecipesPanel extends FacilitiesAndRulesWorkspace.Panel {

    public static final double defaultPassiveSeconds = 5.0;
    public static final int defaultParallelCrafters = 1;
    public static final int maxPreviewGlyphs = 2;

    public AutomatedRecipesPanel(FacilitiesHost host, Supplier<CirrusPalette> palette) {
        super(host, palette);
        refresh();
    }

    @Override
    protected void buildContent() {
        FacilitySnapshot snapshot = host().facilitySnapshot();
        List<AutomatedRecipeCapability> recipes = new ArrayList<>(snapshot.automatedRecipes());
        recipes.sort((left, right) -> left.recipeKey().compareTo(right.recipeKey()));
        content().addWidget(header(recipes.size()));
        int visible = 0;
        for (AutomatedRecipeCapability capability : recipes) {
            CompiledRecipeKey key = capability.recipeKey();
            if (!matchesAny(filter(), key.shortString(), key.systemId())) {
                continue;
            }
            content().addWidget(recipeCard(capability));
            visible++;
        }
        if (visible == 0) {
            content().addWidget(muted(le("empty_recipes").component()));
        }
    }

    private Widget header(int total) {
        WidgetGroup<Widget> head = row(
            value(CirrusLangs.Workbench.Facilities.autoRecipes),
            fixed(Component.literal(Integer.toString(total)), 20, CirrusPalette::textMuted)
        );
        head.addWidget(button(le("import_session"), 116, this::importSession));
        return head;
    }

    private Widget recipeCard(AutomatedRecipeCapability capability) {
        PanelCard card = card();
        card.addWidget(
            row(
                value(Component.literal(capability.recipeKey().shortString())),
                muted(tr("parallel_slots", capability.parallelCrafters()))
            )
        );
        card.addWidget(preview(capability.recipeKey()));
        card.addWidget(
            row(
                muted(tr("batch_time", WorkbenchFormatters.formatSeconds(capability.passiveSecondsPerBatch()))),
                microButton("-", 14, () -> adjustParallel(capability, -1)),
                microButton("+", 14, () -> adjustParallel(capability, 1)),
                button(le("delete"), 52, () -> remove(capability))
            )
        );
        return card;
    }

    private Widget preview(CompiledRecipeKey key) {
        WidgetGroup<Widget> preview = new WidgetGroup<>();
        preview.useStyle(flexRow(), alignItemsCenter(), gap(2), minWidth(0));
        List<ResourceKey> inputs = new ArrayList<>();
        List<ResourceKey> outputs = new ArrayList<>();
        Optional<CompiledRecipe> recipe = host().graph().flatMap(graph -> graph.recipeByKey(key));
        recipe.ifPresent(found -> {
            for (InputSlot slot : found.inputs()) {
                if (!slot.candidates().isEmpty()) {
                    inputs.add(slot.candidates().getFirst());
                }
            }
            for (OutputSlot slot : found.outputs()) {
                outputs.add(slot.resource());
            }
        });
        for (int index = 0; index < Math.min(maxPreviewGlyphs, inputs.size()); index++) {
            preview.addWidget(new Glyph(inputs.get(index).resourceId(), paletteSupplier(), glyphSize));
        }
        preview.addWidget(fixed(Component.literal("->"), 14, CirrusPalette::textMuted));
        for (int index = 0; index < Math.min(maxPreviewGlyphs, outputs.size()); index++) {
            preview.addWidget(new Glyph(outputs.get(index).resourceId(), paletteSupplier(), glyphSize));
        }
        if (inputs.isEmpty() && outputs.isEmpty()) {
            preview.addWidget(muted(le("no_graph").component()));
        }
        return preview;
    }

    private void importSession() {
        FacilitySnapshot snapshot = host().facilitySnapshot();
        Set<CompiledRecipeKey> automated = new LinkedHashSet<>();
        for (AutomatedRecipeCapability capability : snapshot.automatedRecipes()) {
            automated.add(capability.recipeKey());
        }
        int imported = 0;
        for (CompiledRecipeKey key : host().sessionRecipeKeys()) {
            if (key == null || !automated.add(key)) {
                continue;
            }
            host().registry().putAutomatedRecipe(
                new AutomatedRecipeCapability(key, defaultPassiveSeconds, defaultParallelCrafters)
            );
            imported++;
        }
        host().toast(imported > 0 ? tr("import_ok_count", imported) : le("empty_recipes").component());
        refresh();
    }

    private void adjustParallel(AutomatedRecipeCapability capability, int delta) {
        int next = Math.max(1, capability.parallelCrafters() + delta);
        host().registry().putAutomatedRecipe(
            new AutomatedRecipeCapability(capability.recipeKey(), capability.passiveSecondsPerBatch(), next)
        );
        refresh();
    }

    private void remove(AutomatedRecipeCapability capability) {
        host().registry().removeAutomatedRecipe(capability.recipeKey());
        refresh();
    }
}
