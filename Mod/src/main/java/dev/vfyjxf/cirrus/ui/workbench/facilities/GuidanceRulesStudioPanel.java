package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSegmentedControlWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Guidance rules studio: scope/strength/type filter row, rule card list and a sentence builder for the eight rule kinds.
 */
public class GuidanceRulesStudioPanel extends FacilitiesAndRulesWorkspace.Panel {

    public static final int choiceWidth = 130;
    public static final int optionLimit = 128;
    private static final AtomicLong ruleIds = new AtomicLong();

    private final WidgetGroup<Widget> listGroup = new WidgetGroup<>();
    private final Map<String, String> fieldQueries = new LinkedHashMap<>();
    private String keyword = "";
    private String scopeFilter = "all";
    private @Nullable RuleStrength strengthFilter;
    private RuleSentenceBuilder.@Nullable RuleKind kindFilter;
    private boolean composerOpen;
    private @Nullable RuleSentenceBuilder draft;
    private GuidanceScope draftScope = GuidanceScope.session;

    public GuidanceRulesStudioPanel(FacilitiesHost host, Supplier<CirrusPalette> palette) {
        super(host, palette);
        listGroup.useStyle(flexColumn(), flexGrow(1), minWidth(0), gap(3));
        refresh();
    }

    public boolean composerOpen() {
        return composerOpen;
    }

    public RuleSentenceBuilder draft() {
        if (draft == null) {
            draft = RuleSentenceBuilder.of(RuleSentenceBuilder.RuleKind.recipe).scope(draftScope);
        }
        return draft;
    }

    public void openComposer(RuleSentenceBuilder.RuleKind kind) {
        composerOpen = true;
        draft = RuleSentenceBuilder.of(kind == null ? RuleSentenceBuilder.RuleKind.recipe : kind);
        draft.scope(draftScope);
        refresh();
    }

    public void closeComposer() {
        composerOpen = false;
        draft = null;
        refresh();
    }

    public List<GuidanceRule> visibleRules() {
        return filteredRules();
    }

    @Override
    protected void buildContent() {
        content().addWidget(header());
        content().addWidget(filterBar());
        if (composerOpen) {
            content().addWidget(composer());
        }
        content().addWidget(listGroup);
        rebuildList();
    }

    private Widget header() {
        WidgetGroup<Widget> head = row(
            value(CirrusLangs.Workbench.Facilities.rules),
            fixed(Component.literal(Integer.toString(filteredRules().size())), 20, CirrusPalette::textMuted)
        );
        head.addWidget(button(le("new_rule"), 68, () -> openComposer(RuleSentenceBuilder.RuleKind.recipe)));
        return head;
    }

    private Widget filterBar() {
        PanelCard card = card();
        card.addWidget(row(searchInput(le("rule_search"), this::setKeyword)));
        CirrusSegmentedControlWidget<String> scopeControl = new CirrusSegmentedControlWidget<String>(rowHeight)
                .option("all", le("scope_all"), 44).option("world", CirrusLangs.Workbench.Copilot.scopeWorld, 50)
                .option("session", CirrusLangs.Workbench.Copilot.scopeSession, 52)
                .option("default", le("scope_default"), 52).value(scopeFilter).onChanged(value -> {
                    scopeFilter = value == null ? "all" : value;
                    rebuildList();
                });
        card.addWidget(scopeControl);
        CirrusChoiceBoxWidget<String> strengthChoose = new CirrusChoiceBoxWidget<String>(choiceWidth)
                .option("any", tr("strength_any"))
                .option(RuleStrength.hardBan.name(), RuleSentenceBuilder.strengthLabel(RuleStrength.hardBan))
                .option(RuleStrength.hardPin.name(), RuleSentenceBuilder.strengthLabel(RuleStrength.hardPin))
                .option(RuleStrength.softPrefer.name(), RuleSentenceBuilder.strengthLabel(RuleStrength.softPrefer))
                .option(RuleStrength.softPenalize.name(), RuleSentenceBuilder.strengthLabel(RuleStrength.softPenalize))
                .value(strengthFilter == null ? "any" : strengthFilter.name()).onChanged(value -> {
                    strengthFilter = value == null || "any".equals(value) ? null : RuleStrength.valueOf(value);
                    rebuildList();
                });
        card.addWidget(strengthChoose);
        CirrusChoiceBoxWidget<String> kindChoose = new CirrusChoiceBoxWidget<String>(choiceWidth)
                .option("any", tr("type_any"));
        for (RuleSentenceBuilder.RuleKind kind : RuleSentenceBuilder.RuleKind.values()) {
            kindChoose.option(kind.id(), kind.labelKey());
        }
        kindChoose.value(kindFilter == null ? "any" : kindFilter.id());
        kindChoose.onChanged(value -> {
            kindFilter = value == null || "any".equals(value) ? null : kindOfId(value);
            rebuildList();
        });
        card.addWidget(kindChoose);
        return card;
    }

    private void setKeyword(String value) {
        String next = value == null ? "" : value.trim();
        if (!next.equals(keyword)) {
            keyword = next;
            rebuildList();
        }
    }

    private void rebuildList() {
        listGroup.clear();
        List<GuidanceRule> rules = filteredRules();
        if (rules.isEmpty()) {
            listGroup.addWidget(muted(le("empty_rules").component()));
            return;
        }
        for (GuidanceRule rule : rules) {
            listGroup.addWidget(ruleCard(rule));
        }
    }

    private Widget ruleCard(GuidanceRule rule) {
        PanelCard card = card();
        RuleSentenceBuilder.RuleKind kind = RuleSentenceBuilder.kindOf(rule)
                .orElse(RuleSentenceBuilder.RuleKind.recipe);
        card.addWidget(
            row(
                fixed(Component.literal(kindLabelText(kind)), 96, CirrusPalette::textPrimary),
                fixed(
                    RuleSentenceBuilder.strengthLabel(rule.strength()),
                    76,
                    p -> p.ruleStrengthColor(rule.strength())
                ),
                fixed(RuleSentenceBuilder.scopeLabel(rule.scope()), 60, CirrusPalette::textMuted),
                button(le("delete"), 52, () -> deleteRule(rule))
            )
        );
        card.addWidget(new SentenceLine(rule, paletteSupplier()));
        return card;
    }

    private Widget composer() {
        PanelCard card = card();
        RuleSentenceBuilder builder = draft();
        CirrusChoiceBoxWidget<String> kindChoose = new CirrusChoiceBoxWidget<String>(choiceWidth);
        for (RuleSentenceBuilder.RuleKind kind : RuleSentenceBuilder.RuleKind.values()) {
            kindChoose.option(kind.id(), kind.labelKey());
        }
        kindChoose.value(builder.kind().id());
        kindChoose.onChanged(value -> openComposer(kindOfId(value)));
        card.addWidget(row(muted(le("rule_type").component()), kindChoose));
        for (RuleSentenceBuilder.Field field : builder.fields()) {
            card.addWidget(fieldRow(builder, field));
        }
        CirrusChoiceBoxWidget<String> scopeChoose = new CirrusChoiceBoxWidget<String>(choiceWidth)
                .option("session", CirrusLangs.Workbench.Copilot.scopeSession)
                .option("world", CirrusLangs.Workbench.Copilot.scopeWorld)
                .value(draftScope == GuidanceScope.worldSave ? "world" : "session").onChanged(value -> {
                    draftScope = "world".equals(value) ? GuidanceScope.worldSave : GuidanceScope.session;
                    draft().scope(draftScope);
                });
        card.addWidget(
            row(
                muted(le("rule_field.scope").component()),
                scopeChoose,
                button(le("save_rule"), 68, this::saveDraft),
                button(le("cancel"), 52, this::closeComposer)
            )
        );
        card.addWidget(new SentencePreview(this::draft, paletteSupplier()));
        return card;
    }

    private Widget fieldRow(RuleSentenceBuilder builder, RuleSentenceBuilder.Field field) {
        WidgetGroup<Widget> row = row(muted(field.labelKey().component()));
        switch (field.type()) {
            case strength -> {
                CirrusChoiceBoxWidget<String> choose = new CirrusChoiceBoxWidget<String>(choiceWidth);
                for (RuleStrength strength : RuleStrength.values()) {
                    choose.option(strength.name(), RuleSentenceBuilder.strengthLabel(strength));
                }
                Object current = builder.valueOf(field.key());
                choose.value(current instanceof RuleStrength strength ? strength.name() : null);
                choose.onChanged(value -> builder.strength(value == null ? null : RuleStrength.valueOf(value)));
                row.addWidget(choose);
            }
            case bool -> row.addWidget(
                toggle(
                    () -> Boolean.TRUE.equals(builder.valueOf(field.key())),
                    value -> builder.flag(field.key(), value)
                )
            );
            case number -> {
                NumberInputWidget input = NumberInputWidget.create(0L).setRange(0, 1_000_000);
                input.useStyle(sizeOf(72, rowHeight), flexShrink(0));
                Object current = builder.valueOf(field.key());
                if (current instanceof Number number) {
                    input.setValue(Math.round(number.doubleValue()));
                }
                input.onValueChanged(value -> builder.number(field.key(), value));
                row.addWidget(input);
            }
            default -> row.addWidget(indexedChoice(builder, field));
        }
        return row;
    }

    private Widget indexedChoice(RuleSentenceBuilder builder, RuleSentenceBuilder.Field field) {
        WidgetGroup<Widget> group = row();
        group.useStyle(flexRow(), alignItemsCenter(), flexGrow(1), minWidth(0), gap(3));
        CirrusChoiceBoxWidget<String> choose = new CirrusChoiceBoxWidget<String>(choiceWidth);
        populate(choose, builder, field);
        choose.onChanged(value -> applyChoice(builder, field, value));
        group.addWidget(searchInput(le("field_search"), query -> {
            fieldQueries.put(field.key(), query == null ? "" : query.trim());
            populate(choose, builder, field);
        }));
        group.addWidget(choose);
        return group;
    }

    private void populate(
        CirrusChoiceBoxWidget<String> choose,
        RuleSentenceBuilder builder,
        RuleSentenceBuilder.Field field
    ) {
        List<Choice> options = optionsFor(field);
        String query = fieldQueries.getOrDefault(field.key(), "");
        String current = displayOf(field, builder.valueOf(field.key()));
        choose.clearOptions();
        if (current != null && !current.isBlank()) {
            choose.option(current, Component.literal(current));
        }
        int shown = 0;
        for (Choice option : options) {
            if (shown >= optionLimit) {
                break;
            }
            if (option.display().equals(current) || !matchesAny(query, option.display(), option.search())) {
                continue;
            }
            choose.option(option.display(), Component.literal(option.display()));
            shown++;
        }
        choose.value(current);
    }

    private void applyChoice(RuleSentenceBuilder builder, RuleSentenceBuilder.Field field, String display) {
        if (display == null || display.isBlank()) {
            return;
        }
        for (Choice option : optionsFor(field)) {
            if (!option.display().equals(display)) {
                continue;
            }
            switch (field.type()) {
                case resource -> {
                    ComponentStackKey resource = (ComponentStackKey) option.value();
                    if (RuleSentenceBuilder.fieldMember.equals(field.key())) {
                        builder.preferredMember(resource);
                    } else if (RuleSentenceBuilder.fieldTargetResource.equals(field.key())) {
                        builder.targetResource(resource);
                    } else {
                        builder.resource(resource);
                    }
                }
                case recipe -> {
                    CompiledRecipeKey recipeKey = (CompiledRecipeKey) option.value();
                    if (RuleSentenceBuilder.fieldBannedRecipe.equals(field.key())) {
                        builder.bannedSourceRecipe(recipeKey);
                    } else {
                        builder.recipe(recipeKey);
                    }
                }
                case system -> builder.system((Namespace) option.value());
                case machine -> builder.machine((MachineEntry) option.value());
                case tag -> builder.tag((TagKey<?>) option.value());
                case mod -> builder.mod((String) option.value());
                default -> {}
            }
            return;
        }
    }

    private List<Choice> optionsFor(RuleSentenceBuilder.Field field) {
        List<Choice> options = new ArrayList<>();
        switch (field.type()) {
            case resource -> {
                Map<String, ComponentStackKey> resources = new TreeMap<>();
                host().graph().ifPresent(graph -> {
                    for (ComponentStackKey resource : graph.allProducibleResources()) {
                        resources.putIfAbsent(resource.canonicalKey(), resource);
                    }
                });
                resources.forEach(
                    (display, resource) -> options.add(new Choice(display, display + " " + resource.displayId(), resource))
                );
            }
            case recipe -> {
                Map<String, CompiledRecipeKey> keys = new TreeMap<>();
                host().graph().ifPresent(graph -> {
                    for (ComponentStackKey resource : graph.allProducibleResources()) {
                        for (CompiledRecipe recipe : graph.producersOf(resource)) {
                            keys.putIfAbsent(recipe.key().shortString(), recipe.key());
                        }
                    }
                });
                keys.forEach((display, key) -> options.add(new Choice(display, display + " " + key.systemId(), key)));
            }
            case machine -> {
                TreeSet<MachineEntry> machines = new TreeSet<>(MachineEntry.comparator);
                host().graph().ifPresent(graph -> {
                    for (ComponentStackKey resource : graph.allProducibleResources()) {
                        for (CompiledRecipe recipe : graph.producersOf(resource)) {
                            MachineRequirement requirement = recipe.machineRequirement();
                            machines.addAll(requirement.compatibleMachines());
                        }
                    }
                });
                machines.forEach(
                    machine -> options.add(new Choice(machine.canonicalKey(), machine.canonicalKey(), machine))
                );
            }
            case system -> host().systemIds()
                    .forEach(system -> options.add(new Choice(system.toString(), system.toString(), system)));
            case tag -> {
                TreeSet<TagKey<?>> tags = new TreeSet<>(PlanningTagKeys.comparator);
                host().graph().ifPresent(graph -> {
                    for (ComponentStackKey resource : graph.allProducibleResources()) {
                        for (CompiledRecipe recipe : graph.producersOf(resource)) {
                            for (InputSlot slot : recipe.inputs()) {
                                slot.tagKey().ifPresent(tags::add);
                            }
                        }
                    }
                });
                tags.forEach(tag -> {
                    String formatted = PlanningTagKeys.format(tag);
                    options.add(new Choice(formatted, formatted, tag));
                });
            }
            case mod -> {
                TreeSet<String> mods = new TreeSet<>();
                host().graph().ifPresent(graph -> {
                    for (ComponentStackKey resource : graph.allProducibleResources()) {
                        String ns = resource.namespace();
                        if (ns != null && !ns.isEmpty()) {
                            mods.add(ns);
                        }
                    }
                });
                mods.forEach(mod -> options.add(new Choice(mod, mod, mod)));
            }
            default -> {}
        }
        return options;
    }

    private static @Nullable String displayOf(RuleSentenceBuilder.Field field, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return switch (field.type()) {
            case resource -> ((ComponentStackKey) value).canonicalKey();
            case recipe -> ((CompiledRecipeKey) value).shortString();
            case machine -> ((MachineEntry) value).canonicalKey();
            case tag -> PlanningTagKeys.format((TagKey<?>) value);
            default -> String.valueOf(value);
        };
    }

    private void saveDraft() {
        RuleSentenceBuilder builder = draft();
        RuleSentenceBuilder.Validation validation = builder.validate();
        if (!validation.ok()) {
            List<String> fields = new ArrayList<>();
            validation.missing().forEach(field -> fields.add(field.key()));
            validation.invalid().forEach(field -> fields.add(field.key()));
            host().toast(tr("missing_field", String.join(", ", fields)));
            return;
        }
        builder.scope(draftScope);
        String ruleId = "cirrus:workbench/" + builder.kind().id() + "/" + ruleIds.incrementAndGet();
        GuidanceRule rule = builder.build(ruleId);
        host().putRule(rule);
        composerOpen = false;
        draft = null;
        refresh();
    }

    private void deleteRule(GuidanceRule rule) {
        host().removeRule(rule.ruleId());
        refresh();
    }

    private List<GuidanceRule> filteredRules() {
        List<GuidanceRule> rules = new ArrayList<>(host().rules());
        rules.sort((left, right) -> left.ruleId().compareTo(right.ruleId()));
        List<GuidanceRule> filtered = new ArrayList<>();
        for (GuidanceRule rule : rules) {
            if (!scopeMatches(rule) || !strengthMatches(rule) || !kindMatches(rule)) {
                continue;
            }
            String sentence = RuleSentenceBuilder.sentenceOf(rule).getString();
            if (!matchesAny(keyword, rule.ruleId(), sentence, rule.strength().name())) {
                continue;
            }
            filtered.add(rule);
        }
        return filtered;
    }

    private boolean scopeMatches(GuidanceRule rule) {
        return switch (scopeFilter) {
            case "world" -> rule.scope() == GuidanceScope.worldSave;
            case "session" -> rule.scope() == GuidanceScope.session;
            case "default" -> rule.scope() == GuidanceScope.modpackDefault;
            default -> true;
        };
    }

    private boolean strengthMatches(GuidanceRule rule) {
        return strengthFilter == null || rule.strength() == strengthFilter;
    }

    private boolean kindMatches(GuidanceRule rule) {
        return kindFilter == null || RuleSentenceBuilder.kindOf(rule).filter(kindFilter::equals).isPresent();
    }

    private static String kindLabelText(RuleSentenceBuilder.RuleKind kind) {
        return kind.labelKey().component().getString();
    }

    private static RuleSentenceBuilder.RuleKind kindOfId(@Nullable String id) {
        for (RuleSentenceBuilder.RuleKind kind : RuleSentenceBuilder.RuleKind.values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return RuleSentenceBuilder.RuleKind.recipe;
    }

    private record Choice(String display, String search, Object value) {}

    /**
     * One-sentence semantic preview of a rule card: reads the rule itself at render time.
     */
    private static final class SentenceLine extends Widget {

        private final GuidanceRule rule;
        private final Supplier<CirrusPalette> palette;

        private SentenceLine(GuidanceRule rule, Supplier<CirrusPalette> palette) {
            this.rule = rule;
            this.palette = palette;
            useStyle(flexGrow(1), minWidth(0), heightOf(12));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            ChromeText.drawFitted(
                canvas,
                RuleSentenceBuilder.sentenceOf(rule),
                0,
                ChromeText.centeredY(0, height(), canvas),
                width(),
                palette.get().textSecondary()
            );
        }
    }

    /**
     * Live sentence preview of the builder: reads the draft at render time.
     */
    private static final class SentencePreview extends Widget {

        private final Supplier<RuleSentenceBuilder> draft;
        private final Supplier<CirrusPalette> palette;

        private SentencePreview(Supplier<RuleSentenceBuilder> draft, Supplier<CirrusPalette> palette) {
            this.draft = draft;
            this.palette = palette;
            useStyle(flexGrow(1), minWidth(0), heightOf(12));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            RuleSentenceBuilder builder = draft.get();
            if (builder == null) {
                return;
            }
            ChromeText.drawFitted(
                canvas,
                builder.previewSentence(),
                0,
                ChromeText.centeredY(0, height(), canvas),
                width(),
                palette.get().focusRing()
            );
        }
    }
}
