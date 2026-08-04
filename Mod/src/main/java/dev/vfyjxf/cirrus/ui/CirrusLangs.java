package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CirrusLangs {
    private static final String root = "cirrus.ui.";
    private static final ArrayList<LangEntry> entries = new ArrayList<>();

    private CirrusLangs() {
    }

    public static List<LangEntry> entries() {
        return List.copyOf(entries);
    }

    private static LangEntry ui(String path) {
        return key(root + path);
    }

    private static LangEntry key(String key) {
        LangEntry entry = LangEntry.reference(key);
        entries.add(entry);
        return entry;
    }

    private static LangEntry dynamic(String path) {
        return LangEntry.reference(root + path);
    }

    private static void include(LangEntry... ignored) {}

    public static final class Calc {
        public static final LangEntry setValue = key("cirrus.calculator.set_value");
        public static final LangEntry calculate = ui("calc.calculate");
        public static final LangEntry calculateDesc = ui("calc.calculate.desc");
        public static final LangEntry calculating = ui("calc.calculating");
        public static final LangEntry empty = ui("calc.empty");
        public static final LangEntry amount = ui("calc.amount");
        public static final LangEntry amountDesc = ui("calc.amount.desc");
        public static final LangEntry amountMiddleClick = ui("calc.amount.middle_click");
        public static final LangEntry targetSlot = ui("calc.target_slot");
        public static final LangEntry targetSlotEmpty = ui("calc.target_slot.empty");
        public static final LangEntry targetSlotDesc = ui("calc.target_slot.desc");
        public static final LangEntry actionMaterials = ui("calc.action.materials");
        public static final LangEntry actionHome = ui("calc.action.home");
        public static final LangEntry actionTreeExpand = ui("calc.action.tree_expand");
        public static final LangEntry actionExecutionPath = ui("calc.action.execution_path");
        public static final LangEntry actionExecutionPathDesc = ui("calc.action.execution_path.desc");
        public static final LangEntry actionSettings = ui("calc.action.settings");
        public static final LangEntry actionSettingsDesc = ui("calc.action.settings.desc");

        private Calc() {
        }

        public static final class View {
            public static final LangEntry tree = ui("calc.view.tree");
            public static final LangEntry matrix = ui("calc.view.matrix");
            public static final LangEntry milestones = ui("calc.view.milestones");

            private View() {}
        }

        public static final class Tree {
            public static final LangEntry noRecipeData = ui("calc.tree.no_recipe_data");
            public static final LangEntry expand = ui("calc.tree.expand");
            public static final LangEntry collapse = ui("calc.tree.collapse");
            public static final LangEntry childCount = ui("calc.tree.child_count");
            public static final LangEntry durationLine = ui("calc.tree.duration_line");
            public static final LangEntry machineCount = ui("calc.tree.machine_count");
            public static final LangEntry outputCount = ui("calc.tree.output_count");
            public static final LangEntry materialList = ui("calc.tree.material_list");
            public static final LangEntry materialSearchPlaceholder = ui("calc.tree.material_search.placeholder");
            public static final LangEntry totalTime = ui("calc.tree.total_time");
            public static final LangEntry totalEnergy = ui("calc.tree.total_energy");
            public static final LangEntry stepCount = ui("calc.tree.step_count");
            public static final LangEntry needShort = ui("calc.tree.need.short");
            public static final LangEntry producedShort = ui("calc.tree.produced.short");
            public static final LangEntry missingShort = ui("calc.tree.missing.short");
            public static final LangEntry chanceShort = ui("calc.tree.chance.short");
            public static final LangEntry toolShort = ui("calc.tree.tool.short");
            public static final LangEntry category = ui("calc.tree.category");
            public static final LangEntry required = ui("calc.tree.required");
            public static final LangEntry consumed = ui("calc.tree.consumed");
            public static final LangEntry produced = ui("calc.tree.produced");
            public static final LangEntry missing = ui("calc.tree.missing");
            public static final LangEntry usage = ui("calc.tree.usage");
            public static final LangEntry machineCandidates = ui("calc.tree.machine_candidates");
            public static final LangEntry current = ui("calc.tree.current");
            public static final LangEntry candidate = ui("calc.tree.candidate");
            public static final LangEntry candidates = ui("calc.tree.candidates");
            public static final LangEntry more = ui("calc.tree.more");
            public static final LangEntry close = ui("calc.tree.close");
            public static final LangEntry clickSet = ui("calc.tree.click_set");

            private Tree() {}

            public static final class Material {
                public static final LangEntry externalInput = ui("calc.tree.material.external_input");
                public static final LangEntry byproduct = ui("calc.tree.material.byproduct");
                public static final LangEntry catalyst = ui("calc.tree.material.catalyst");
                public static final LangEntry intermediate = ui("calc.tree.material.intermediate");

                private Material() {}
            }
        }

        public static final class Matrix {
            public static final LangEntry title = ui("calc.matrix.title");
            public static final LangEntry count = ui("calc.matrix.count");
            public static final LangEntry output = ui("calc.matrix.output");
            public static final LangEntry process = ui("calc.matrix.process");
            public static final LangEntry input = ui("calc.matrix.input");
            public static final LangEntry machine = ui("calc.matrix.machine");
            public static final LangEntry duration = ui("calc.matrix.duration");
            public static final LangEntry energy = ui("calc.matrix.energy");

            private Matrix() {}
        }

        public static final class Milestones {
            public static final LangEntry title = ui("calc.milestones.title");
            public static final LangEntry defaultPhase = ui("calc.milestones.default_phase");
            public static final LangEntry collect = ui("calc.milestones.collect");
            public static final LangEntry make = ui("calc.milestones.make");
            public static final LangEntry step = ui("calc.milestones.step");
            public static final LangEntry source = ui("calc.milestones.source");

            private Milestones() {}
        }
    }

    public static final class Summary {
        public static final LangEntry title = ui("summary.title");
        public static final LangEntry inputs = ui("summary.inputs");
        public static final LangEntry catalysts = ui("summary.catalysts");
        public static final LangEntry outputs = ui("summary.outputs");
        public static final LangEntry specs = ui("summary.specs");

        private Summary() {}

        public static LangEntry spec(String type) {
            return dynamic("summary.spec." + type);
        }
    }

    public static final class Content {
        public static final LangEntry fluidContainer = ui("content.fluid_container");
        public static final LangEntry fluidContainerDesc = ui("content.fluid_container.desc");

        private Content() {}
    }

    public static final class Calculator {
        public static final LangEntry open = ui("calculator.open");

        private Calculator() {}

        public static final class Jei {
            private Jei() {}

            public static final class Open {
                public static final LangEntry tooltip = ui("calculator.jei.open.tooltip");

                private Open() {}
            }

            public static final class Target {
                public static final LangEntry tooltip = ui("calculator.jei.target.tooltip");
                public static final LangEntry unsupported = ui("calculator.jei.target.unsupported");

                private Target() {}
            }

            public static final class Database {
                public static final LangEntry tooltip = ui("calculator.jei.database.tooltip");
                public static final LangEntry tooltipCurrent = ui("calculator.jei.database.tooltip.current");
                public static final LangEntry added = ui("calculator.jei.database.added");
                public static final LangEntry alreadyPresent = ui("calculator.jei.database.already_present");
                public static final LangEntry unsupported = ui("calculator.jei.database.unsupported");
                public static final LangEntry failed = ui("calculator.jei.database.failed");

                private Database() {}
            }
        }

        public static final class Database {
            public static final LangEntry search = ui("calculator.database.search");
            public static final LangEntry searchDesc = ui("calculator.database.search.desc");
            public static final LangEntry searchPredicateHasDesc = ui("calculator.database.search.predicate.has.desc");
            public static final LangEntry searchPredicateTextDesc = ui("calculator.database.search.predicate.text.desc");
            public static final LangEntry searchPredicateModDesc = ui("calculator.database.search.predicate.mod.desc");
            public static final LangEntry searchPredicateTagDesc = ui("calculator.database.search.predicate.tag.desc");
            public static final LangEntry searchPredicateTooltipDesc = ui("calculator.database.search.predicate.tooltip.desc");
            public static final LangEntry searchPredicateIdDesc = ui("calculator.database.search.predicate.id.desc");
            public static final LangEntry searchPredicateNameDesc = ui("calculator.database.search.predicate.name.desc");
            public static final LangEntry searchPredicateOutDesc = ui("calculator.database.search.predicate.out.desc");
            public static final LangEntry searchPredicateInDesc = ui("calculator.database.search.predicate.in.desc");
            public static final LangEntry searchPredicateSystemDesc = ui("calculator.database.search.predicate.system.desc");
            public static final LangEntry searchPredicateDbDesc = ui("calculator.database.search.predicate.db.desc");
            public static final LangEntry searchPredicateMachineDesc = ui("calculator.database.search.predicate.machine.desc");
            public static final LangEntry searchPredicateTimeDesc = ui("calculator.database.search.predicate.time.desc");
            public static final LangEntry searchPredicateEnergyDesc = ui("calculator.database.search.predicate.energy.desc");
            public static final LangEntry searchPredicateCatDesc = ui("calculator.database.search.predicate.cat.desc");
            public static final LangEntry searchPredicateByproductDesc = ui("calculator.database.search.predicate.byproduct.desc");
            public static final LangEntry searchPredicateChanceDesc = ui("calculator.database.search.predicate.chance.desc");
            public static final LangEntry searchPredicateTierDesc = ui("calculator.database.search.predicate.tier.desc");
            public static final LangEntry searchPredicateLevelDesc = ui("calculator.database.search.predicate.level.desc");
            public static final LangEntry searchPredicateContextDesc = ui("calculator.database.search.predicate.context.desc");
            public static final LangEntry searchPredicatePlanDesc = ui("calculator.database.search.predicate.plan.desc");
            public static final LangEntry searchPredicateProfileDesc = ui("calculator.database.search.predicate.profile.desc");
            public static final LangEntry searchPredicateCompareDesc = ui("calculator.database.search.predicate.compare.desc");
            public static final LangEntry searchPredicateExactDesc = ui("calculator.database.search.predicate.exact.desc");
            public static final LangEntry searchPredicateWildcardDesc = ui("calculator.database.search.predicate.wildcard.desc");
            public static final LangEntry searchPredicateNotDesc = ui("calculator.database.search.predicate.not.desc");
            public static final LangEntry searchPredicateQuoteDesc = ui("calculator.database.search.predicate.quote.desc");
            public static final LangEntry searchPredicateOrDesc = ui("calculator.database.search.predicate.or.desc");
            public static final LangEntry searchPredicateGroupDesc = ui("calculator.database.search.predicate.group.desc");
            public static final LangEntry empty = ui("calculator.database.empty");
            public static final LangEntry indexing = ui("calculator.database.indexing");
            public static final LangEntry invalidQuery = ui("calculator.database.invalid_query");
            public static final LangEntry results = ui("calculator.database.results");
            public static final LangEntry sources = ui("calculator.database.sources");
            public static final LangEntry sourcesDesc = ui("calculator.database.sources.desc");
            public static final LangEntry sourcesBuiltinDesc = ui("calculator.database.sources.builtin.desc");
            public static final LangEntry sourceAvailable = ui("calculator.database.source.available");
            public static final LangEntry sourceEnabled = ui("calculator.database.source.enabled");
            public static final LangEntry sourceDisabled = ui("calculator.database.source.disabled");
            public static final LangEntry builtin = ui("calculator.database.builtin");
            public static final LangEntry readOnlyBuiltinDesc = ui("calculator.database.builtin.read_only.desc");
            public static final LangEntry select = ui("calculator.database.select");
            public static final LangEntry selectDesc = ui("calculator.database.select.desc");
            public static final LangEntry create = ui("calculator.database.create");
            public static final LangEntry createDesc = ui("calculator.database.create.desc");
            public static final LangEntry createCancel = ui("calculator.database.create.cancel");
            public static final LangEntry createCancelDesc = ui("calculator.database.create.cancel.desc");
            public static final LangEntry createNamePlaceholder = ui("calculator.database.create.name.placeholder");
            public static final LangEntry createNameDesc = ui("calculator.database.create.name.desc");
            public static final LangEntry createConfirm = ui("calculator.database.create.confirm");
            public static final LangEntry createConfirmDesc = ui("calculator.database.create.confirm.desc");
            public static final LangEntry delete = ui("calculator.database.delete");
            public static final LangEntry deleteDesc = ui("calculator.database.delete.desc");
            public static final LangEntry deleteBuiltinDesc = ui("calculator.database.delete.builtin.desc");
            public static final LangEntry deleteConfirm = ui("calculator.database.delete.confirm");
            public static final LangEntry deleteConfirmDesc = ui("calculator.database.delete.confirm.desc");
            public static final LangEntry systemRecipes = ui("calculator.database.system_recipes");
            public static final LangEntry allResults = ui("calculator.database.all_results");
            public static final LangEntry allResultsDesc = ui("calculator.database.all_results.desc");
            public static final LangEntry includeSystem = ui("calculator.database.include_system");
            public static final LangEntry sourceRecipesDesc = ui("calculator.database.source_recipes.desc");
            public static final LangEntry recipeAvailable = ui("calculator.database.recipe.available");
            public static final LangEntry recipeSelected = ui("calculator.database.recipe.selected");
            public static final LangEntry recipeIncludedBySource = ui("calculator.database.recipe.included_by_source");
            public static final LangEntry recipeStatusAvailable = ui("calculator.database.recipe.status.available");
            public static final LangEntry recipeStatusSelected = ui("calculator.database.recipe.status.selected");
            public static final LangEntry recipeStatusIncludedBySource = ui("calculator.database.recipe.status.included_by_source");
            public static final LangEntry bulkSelect = ui("calculator.database.bulk.select");
            public static final LangEntry bulkSelectDesc = ui("calculator.database.bulk.select.desc");
            public static final LangEntry bulkClear = ui("calculator.database.bulk.clear");
            public static final LangEntry bulkClearDesc = ui("calculator.database.bulk.clear.desc");
            public static final LangEntry recipesMore = ui("calculator.database.recipes.more");
            public static final LangEntry deleteCancel = ui("calculator.database.delete.cancel");
            public static final LangEntry deleteCancelDesc = ui("calculator.database.delete.cancel.desc");
            public static final LangEntry sortAscending = ui("calculator.database.sort.ascending");
            public static final LangEntry sortDescending = ui("calculator.database.sort.descending");
            public static final LangEntry viewGrid = ui("calculator.database.view.grid");
            public static final LangEntry viewList = ui("calculator.database.view.list");
            public static final LangEntry viewGridDesc = ui("calculator.database.view.grid.desc");
            public static final LangEntry viewListDesc = ui("calculator.database.view.list.desc");
            public static final LangEntry sort = ui("calculator.database.sort");
            public static final LangEntry sortDesc = ui("calculator.database.sort.desc");
            public static final LangEntry sortRelevance = ui("calculator.database.sort.relevance");
            public static final LangEntry sortName = ui("calculator.database.sort.name");
            public static final LangEntry sortDatabase = ui("calculator.database.sort.database");
            public static final LangEntry sortSystem = ui("calculator.database.sort.system");
            public static final LangEntry current = ui("calculator.database.current");
            public static final LangEntry next = ui("calculator.database.next");
            public static final LangEntry systemGroup = ui("calculator.database.group.system");
            public static final LangEntry active = ui("calculator.database.active");
            public static final LangEntry details = ui("calculator.database.details");
            public static final LangEntry preview = ui("calculator.database.preview");
            public static final LangEntry actionSetCalculator = ui("calculator.database.action.set_calculator");
            public static final LangEntry actionSetCalculatorDesc = ui("calculator.database.action.set_calculator.desc");
            public static final LangEntry actionSetExecution = ui("calculator.database.action.set_execution");
            public static final LangEntry actionSetExecutionDesc = ui("calculator.database.action.set_execution.desc");
            public static final LangEntry shortcutHeader = ui("calculator.database.shortcut.header");
            public static final LangEntry shortcutDefaultClick = ui("calculator.database.shortcut.default_click");
            public static final LangEntry shortcutSetCalculatorTarget = ui("calculator.database.shortcut.set_calculator_target");
            public static final LangEntry shortcutOpenCalculator = ui("calculator.database.shortcut.open_calculator");
            public static final LangEntry detailInputs = ui("calculator.database.detail.inputs");
            public static final LangEntry detailOutputs = ui("calculator.database.detail.outputs");
            public static final LangEntry contentsMore = ui("calculator.database.contents.more");
            public static final LangEntry database = ui("calculator.database.database");
            public static final LangEntry system = ui("calculator.database.system");
            public static final LangEntry effects = ui("calculator.database.effects");
            public static final LangEntry effectsNone = ui("calculator.database.effects.none");
            public static final LangEntry effectsMore = ui("calculator.database.effects.more");
            public static final LangEntry effectsMerge = ui("calculator.database.effects.merge");
            public static final LangEntry effectsMergeDesc = ui("calculator.database.effects.merge.desc");
            public static final LangEntry effectsMergedCount = ui("calculator.database.effects.merged_count");
            public static final LangEntry effectSettings = ui("calculator.database.effect_settings");
            public static final LangEntry effectContext = ui("calculator.database.effect.context");
            public static final LangEntry effectContextDefault = ui("calculator.database.effect.context.default");
            public static final LangEntry effectRunnable = ui("calculator.database.effect.runnable");
            public static final LangEntry effectRunnableYes = ui("calculator.database.effect.runnable.yes");
            public static final LangEntry effectRunnableNo = ui("calculator.database.effect.runnable.no");
            public static final LangEntry effectSource = ui("calculator.database.effect.source");
            public static final LangEntry effectPhase = ui("calculator.database.effect.phase");
            public static final LangEntry effectBefore = ui("calculator.database.effect.before");
            public static final LangEntry effectAfter = ui("calculator.database.effect.after");
            public static final LangEntry effectDelta = ui("calculator.database.effect.delta");
            public static final LangEntry effectFactor = ui("calculator.database.effect.factor");
            public static final LangEntry effectInputChanges = ui("calculator.database.effect.input_changes");
            public static final LangEntry effectOutputChanges = ui("calculator.database.effect.output_changes");
            public static final LangEntry projection = ui("calculator.database.projection");
            public static final LangEntry candidates = ui("calculator.database.candidates");
            public static final LangEntry candidatesOriginal = ui("calculator.database.candidates.original");
            public static final LangEntry candidatesProjected = ui("calculator.database.candidates.projected");
            public static final LangEntry plugins = ui("calculator.database.plugins");
            public static final LangEntry pluginsNone = ui("calculator.database.plugins.none");
            public static final LangEntry pluginBuiltin = ui("calculator.database.plugin.builtin");
            public static final LangEntry pluginBuiltinDesc = ui("calculator.database.plugin.builtin.desc");
            public static final LangEntry pluginEffects = ui("calculator.database.plugin.effects");
            public static final LangEntry pluginClause = ui("calculator.database.plugin.clause");
            public static final LangEntry pluginResolve = ui("calculator.database.plugin.resolve");
            public static final LangEntry pluginOrder = ui("calculator.database.plugin.order");
            public static final LangEntry parameters = ui("calculator.database.parameters");
            public static final LangEntry parametersNone = ui("calculator.database.parameters.none");
            public static final LangEntry debugPlugin = ui("calculator.database.debug_plugin");
            public static final LangEntry debugPluginDesc = ui("calculator.database.debug_plugin.desc");
            public static final LangEntry debugPluginScale = ui("calculator.database.debug_plugin.scale");
            public static final LangEntry debugPluginScaleDesc = ui("calculator.database.debug_plugin.scale.desc");
            public static final LangEntry debugPluginExtraOutput = ui("calculator.database.debug_plugin.extra_output");
            public static final LangEntry debugPluginExtraOutputDesc = ui("calculator.database.debug_plugin.extra_output.desc");
            public static final LangEntry debugPluginChannel = ui("calculator.database.debug_plugin.channel");
            public static final LangEntry debugPluginChannelDesc = ui("calculator.database.debug_plugin.channel.desc");
            public static final LangEntry debugPluginChannelInput = ui("calculator.database.debug_plugin.channel.input");
            public static final LangEntry debugPluginChannelOutput = ui("calculator.database.debug_plugin.channel.output");

            private Database() {}

            public static final class Effect {
                public static final LangEntry input = ui("calculator.database.effect.input");
                public static final LangEntry output = ui("calculator.database.effect.output");
                public static final LangEntry added = ui("calculator.database.effect.added");
                public static final LangEntry removed = ui("calculator.database.effect.removed");
                public static final LangEntry replaced = ui("calculator.database.effect.replaced");
                public static final LangEntry projectionTransformer = ui("calculator.database.effect.phase.projection_transformer");
                public static final LangEntry contentTransformer = ui("calculator.database.effect.phase.content_transformer");
                public static final LangEntry resolveEffect = ui("calculator.database.effect.phase.resolve_effect");

                private Effect() {}
            }
        }

        public static final class Rules {
            public static final LangEntry empty = ui("calculator.rules.empty");
            public static final LangEntry sectionSummary = ui("calculator.rules.section.summary");
            public static final LangEntry sectionProfile = ui("calculator.rules.section.profile");
            public static final LangEntry sectionSourcePolicy = ui("calculator.rules.section.source_policy");
            public static final LangEntry sectionClauses = ui("calculator.rules.section.clauses");
            public static final LangEntry sectionBuilder = ui("calculator.rules.section.builder");
            public static final LangEntry materialPicker = ui("calculator.rules.material_picker");
            public static final LangEntry materialPickerResults = ui("calculator.rules.material_picker.results");
            public static final LangEntry materialSearchPlaceholder = ui("calculator.rules.material_picker.search.placeholder");
            public static final LangEntry materialSelected = ui("calculator.rules.material_picker.selected");
            public static final LangEntry materialVisible = ui("calculator.rules.material_picker.visible");
            public static final LangEntry advancedEditor = ui("calculator.rules.advanced_editor");
            public static final LangEntry sourceFilter = ui("calculator.rules.source_filter");
            public static final LangEntry scoreRule = ui("calculator.rules.score_rule");
            public static final LangEntry rejectRule = ui("calculator.rules.reject_rule");
            public static final LangEntry penaltyRule = ui("calculator.rules.penalty_rule");
            public static final LangEntry rewardRule = ui("calculator.rules.reward_rule");
            public static final LangEntry stockRule = ui("calculator.rules.stock_rule");
            public static final LangEntry noClauses = ui("calculator.rules.no_clauses");
            public static final LangEntry noSourceFilters = ui("calculator.rules.no_source_filters");
            public static final LangEntry readOnly = ui("calculator.rules.read_only");
            public static final LangEntry enabled = ui("calculator.rules.enabled");
            public static final LangEntry disabled = ui("calculator.rules.disabled");
            public static final LangEntry ifLabel = ui("calculator.rules.if");
            public static final LangEntry thenLabel = ui("calculator.rules.then");
            public static final LangEntry fieldId = ui("calculator.rules.field.id");
            public static final LangEntry fieldName = ui("calculator.rules.field.name");
            public static final LangEntry fieldCollection = ui("calculator.rules.field.collection");
            public static final LangEntry fieldEditable = ui("calculator.rules.field.editable");
            public static final LangEntry fieldPreset = ui("calculator.rules.field.preset");
            public static final LangEntry fieldUnresolved = ui("calculator.rules.field.unresolved");
            public static final LangEntry fieldShortlist = ui("calculator.rules.field.shortlist");
            public static final LangEntry fieldRollback = ui("calculator.rules.field.rollback");
            public static final LangEntry fieldTieEps = ui("calculator.rules.field.tie_eps");
            public static final LangEntry fieldSoftAmp = ui("calculator.rules.field.soft_amp");
            public static final LangEntry fieldHardAmp = ui("calculator.rules.field.hard_amp");
            public static final LangEntry fieldPolicy = ui("calculator.rules.field.policy");
            public static final LangEntry fieldAction = ui("calculator.rules.field.action");
            public static final LangEntry fieldWhen = ui("calculator.rules.field.when");
            public static final LangEntry fieldOverride = ui("calculator.rules.field.override");
            public static final LangEntry fieldSourceTag = ui("calculator.rules.field.source_tag");
            public static final LangEntry fieldMaterial = ui("calculator.rules.field.material");
            public static final LangEntry fieldScoreKey = ui("calculator.rules.field.score_key");
            public static final LangEntry fieldAmount = ui("calculator.rules.field.amount");
            public static final LangEntry fieldType = ui("calculator.rules.field.type");
            public static final LangEntry fieldScope = ui("calculator.rules.field.scope");
            public static final LangEntry fieldStrength = ui("calculator.rules.field.strength");
            public static final LangEntry conditionAlways = ui("calculator.rules.condition.always");
            public static final LangEntry conditionTag = ui("calculator.rules.condition.tag");
            public static final LangEntry conditionAll = ui("calculator.rules.condition.all");
            public static final LangEntry conditionAny = ui("calculator.rules.condition.any");
            public static final LangEntry conditionNone = ui("calculator.rules.condition.none");
            public static final LangEntry conditionNot = ui("calculator.rules.condition.not");
            public static final LangEntry conditionInputMaterial = ui("calculator.rules.condition.input_material");
            public static final LangEntry conditionOutputMaterial = ui("calculator.rules.condition.output_material");
            public static final LangEntry conditionAnyMaterial = ui("calculator.rules.condition.any_material");
            public static final LangEntry effectPenalty = ui("calculator.rules.effect.penalty");
            public static final LangEntry effectReward = ui("calculator.rules.effect.reward");
            public static final LangEntry effectReject = ui("calculator.rules.effect.reject");
            public static final LangEntry effectStock = ui("calculator.rules.effect.stock");
            public static final LangEntry simpleReject = ui("calculator.rules.simple.reject");
            public static final LangEntry simplePenalty = ui("calculator.rules.simple.penalty");
            public static final LangEntry simpleReward = ui("calculator.rules.simple.reward");
            public static final LangEntry simpleStock = ui("calculator.rules.simple.stock");
            public static final LangEntry scopeAny = ui("calculator.rules.scope.any");
            public static final LangEntry scopeInput = ui("calculator.rules.scope.input");
            public static final LangEntry scopeOutput = ui("calculator.rules.scope.output");
            public static final LangEntry hintReject = ui("calculator.rules.hint.reject");
            public static final LangEntry hintPenalty = ui("calculator.rules.hint.penalty");
            public static final LangEntry hintReward = ui("calculator.rules.hint.reward");
            public static final LangEntry hintStock = ui("calculator.rules.hint.stock");
            public static final LangEntry actionExclude = ui("calculator.rules.action.exclude");
            public static final LangEntry actionInclude = ui("calculator.rules.action.include");
            public static final LangEntry actionOnly = ui("calculator.rules.action.only");
            public static final LangEntry actionOverride = ui("calculator.rules.action.override");
            public static final LangEntry actionExcludeShort = ui("calculator.rules.action.exclude.short");
            public static final LangEntry actionIncludeShort = ui("calculator.rules.action.include.short");
            public static final LangEntry actionOnlyShort = ui("calculator.rules.action.only.short");
            public static final LangEntry actionNew = ui("calculator.rules.action.new");
            public static final LangEntry actionCopy = ui("calculator.rules.action.copy");
            public static final LangEntry actionAdd = ui("calculator.rules.action.add");
            public static final LangEntry actionApply = ui("calculator.rules.action.apply");
            public static final LangEntry actionRemove = ui("calculator.rules.action.remove");
            public static final LangEntry actionPick = ui("calculator.rules.action.pick");
            public static final LangEntry actionSelectFiltered = ui("calculator.rules.action.select_filtered");
            public static final LangEntry actionClear = ui("calculator.rules.action.clear");
            public static final LangEntry actionAdvanced = ui("calculator.rules.action.advanced");
            public static final LangEntry actionDetails = ui("calculator.rules.action.details");
            public static final LangEntry actionBack = ui("calculator.rules.action.back");
            public static final LangEntry actionPrevious = ui("calculator.rules.action.previous");
            public static final LangEntry actionNext = ui("calculator.rules.action.next");
            public static final LangEntry newNamePlaceholder = ui("calculator.rules.new_name.placeholder");

            private Rules() {}
        }

        public static final class Settings {
            public static final LangEntry categoryExecution = ui("calculator.settings.category.execution");
            public static final LangEntry categoryPlanning = ui("calculator.settings.category.planning");
            public static final LangEntry categoryDatabase = ui("calculator.settings.category.database");
            public static final LangEntry categoryRules = ui("calculator.settings.category.rules");
            public static final LangEntry trace = ui("calculator.settings.trace");
            public static final LangEntry traceDesc = ui("calculator.settings.trace.desc");
            public static final LangEntry jeiTargetAutoClose = ui("calculator.settings.jei_target_auto_close");
            public static final LangEntry jeiTargetAutoCloseDesc = ui("calculator.settings.jei_target_auto_close.desc");
            public static final LangEntry databaseAutoRefresh = ui("calculator.settings.database_auto_refresh");
            public static final LangEntry databaseAutoRefreshDesc = ui("calculator.settings.database_auto_refresh.desc");
            public static final LangEntry projectionUsesGlobalDatabase = ui("calculator.settings.projection_uses_global_database");
            public static final LangEntry projectionUsesGlobalDatabaseDesc = ui("calculator.settings.projection_uses_global_database.desc");
            public static final LangEntry databaseLimit = ui("calculator.settings.database_limit");
            public static final LangEntry databaseLimitDesc = ui("calculator.settings.database_limit.desc");
            public static final LangEntry readOtherWorlds = ui("calculator.settings.read_other_worlds");
            public static final LangEntry readOtherWorldsDesc = ui("calculator.settings.read_other_worlds.desc");
            public static final LangEntry calculatorRule = ui("calculator.settings.calculator_rule");
            public static final LangEntry calculatorRuleDesc = ui("calculator.settings.calculator_rule.desc");
            public static final LangEntry calculatorRulePreview = ui("calculator.settings.calculator_rule.preview");
            public static final LangEntry calculatorRuleActive = ui("calculator.settings.calculator_rule.active");
            public static final LangEntry calculatorRuleExecution = ui("calculator.settings.calculator_rule.execution");
            public static final LangEntry calculatorRuleStrict = ui("calculator.settings.calculator_rule.strict");
            public static final LangEntry calculatorRuleAssured = ui("calculator.settings.calculator_rule.assured");
            public static final LangEntry plannerMode = ui("calculator.settings.planner_mode");
            public static final LangEntry plannerModeDesc = ui("calculator.settings.planner_mode.desc");
            public static final LangEntry plannerModeStandard = ui("calculator.settings.planner_mode.standard");
            public static final LangEntry plannerModeAssuredBounded = ui("calculator.settings.planner_mode.assured_bounded");
            public static final LangEntry plannerModeAssuredProven = ui("calculator.settings.planner_mode.assured_proven");
            public static final LangEntry useLiveStock = ui("calculator.settings.use_live_stock");
            public static final LangEntry useLiveStockDesc = ui("calculator.settings.use_live_stock.desc");
            public static final LangEntry inventorySensitivity = ui("calculator.settings.inventory_sensitivity");
            public static final LangEntry inventorySensitivityDesc = ui("calculator.settings.inventory_sensitivity.desc");
            public static final LangEntry inventorySensitivityHigh = ui("calculator.settings.inventory_sensitivity.high");
            public static final LangEntry inventorySensitivityBalanced = ui("calculator.settings.inventory_sensitivity.balanced");
            public static final LangEntry inventorySensitivityLow = ui("calculator.settings.inventory_sensitivity.low");
            public static final LangEntry stockPolicy = ui("calculator.settings.stock_policy");
            public static final LangEntry stockPolicyDesc = ui("calculator.settings.stock_policy.desc");
            public static final LangEntry stockPolicyConsume = ui("calculator.settings.stock_policy.consume");
            public static final LangEntry stockPolicyPreserve = ui("calculator.settings.stock_policy.preserve");
            public static final LangEntry executionUseCalculatorPath = ui("calculator.settings.execution_use_calculator_path");
            public static final LangEntry executionUseCalculatorPathDesc = ui("calculator.settings.execution_use_calculator_path.desc");
            public static final LangEntry executionPlannerMode = ui("calculator.settings.execution_planner_mode");
            public static final LangEntry executionPlannerModeDesc = ui("calculator.settings.execution_planner_mode.desc");
            public static final LangEntry switchState = ui("calculator.settings.switch.state");
            public static final LangEntry switchOn = ui("calculator.settings.switch.on");
            public static final LangEntry switchOff = ui("calculator.settings.switch.off");
            public static final LangEntry currentValue = ui("calculator.settings.current");
            public static final LangEntry range = ui("calculator.settings.range");
            public static final LangEntry options = ui("calculator.settings.options");

            private Settings() {}
        }
    }

    public static final class Execution {
        private Execution() {}

        public static final class Overlay {
            public static final LangEntry empty = ui("execution.overlay.empty");
            public static final LangEntry calculating = ui("execution.overlay.calculating");
            public static final LangEntry calculatingShort = ui("execution.overlay.calculating.short");
            public static final LangEntry currentRecipe = ui("execution.overlay.current_recipe");
            public static final LangEntry currentRecipeNone = ui("execution.overlay.current_recipe.none");

            private Overlay() {}

            public static LangEntry status(String status) {
                if (status.isBlank()) {
                    return dynamic("execution.overlay.status.pending");
                }
                return dynamic("execution.overlay.status." + status.toLowerCase(Locale.ROOT));
            }

            public static final class Dock {
                public static final LangEntry recipe = ui("execution.overlay.dock.recipe");
                public static final LangEntry timeline = ui("execution.overlay.dock.timeline");
                public static final LangEntry ingredients = ui("execution.overlay.dock.ingredients");
                public static final LangEntry pagePrev = ui("execution.overlay.dock.page.prev");
                public static final LangEntry pageNext = ui("execution.overlay.dock.page.next");

                private Dock() {}
            }

            public static final class Material {
                public static final LangEntry title = ui("execution.overlay.material.title");
                public static final LangEntry unique = ui("execution.overlay.material.unique");
                public static final LangEntry need = ui("execution.overlay.material.need");
                public static final LangEntry have = ui("execution.overlay.material.have");
                public static final LangEntry miss = ui("execution.overlay.material.miss");
                public static final LangEntry none = ui("execution.overlay.material.none");
                public static final LangEntry noneMissing = ui("execution.overlay.material.none_missing");
                public static final LangEntry more = ui("execution.overlay.material.more");
                public static final LangEntry viewMissing = ui("execution.overlay.material.view.missing");
                public static final LangEntry viewTotal = ui("execution.overlay.material.view.total");

                private Material() {}
            }

            public static final class Summary {
                public static final LangEntry target = ui("execution.overlay.summary.target");
                public static final LangEntry execution = ui("execution.overlay.summary.execution");
                public static final LangEntry alert = ui("execution.overlay.summary.alert");
                public static final LangEntry need = ui("execution.overlay.summary.need");
                public static final LangEntry rate = ui("execution.overlay.summary.rate");

                private Summary() {}
            }

            public static final class Target {
                public static final LangEntry none = ui("execution.overlay.target.none");

                private Target() {}
            }

            public static final class Tooltip {
                public static final LangEntry current = ui("execution.overlay.tooltip.current");
                public static final LangEntry range = ui("execution.overlay.tooltip.range");
                public static final LangEntry edit = ui("execution.overlay.tooltip.edit");
                public static final LangEntry progress = ui("execution.overlay.tooltip.progress");
                public static final LangEntry steps = ui("execution.overlay.tooltip.steps");
                public static final LangEntry status = ui("execution.overlay.tooltip.status");
                public static final LangEntry executing = ui("execution.overlay.tooltip.executing");
                public static final LangEntry next = ui("execution.overlay.tooltip.next");
                public static final LangEntry step = ui("execution.overlay.tooltip.step");
                public static final LangEntry batch = ui("execution.overlay.tooltip.batch");

                private Tooltip() {}
            }

            public static final class Rate {
                public static final LangEntry hintDrag = ui("execution.overlay.rate.hint.drag");
                public static final LangEntry hintType = ui("execution.overlay.rate.hint.type");

                private Rate() {}
            }

            public static final class Trace {
                public static final LangEntry title = ui("execution.overlay.trace");
                public static final LangEntry enable = ui("execution.overlay.trace.enable");
                public static final LangEntry disable = ui("execution.overlay.trace.disable");
                public static final LangEntry forceReopen = ui("execution.overlay.trace.force_reopen");
                public static final LangEntry stateEnabled = ui("execution.overlay.trace.state.enabled");
                public static final LangEntry stateDisabled = ui("execution.overlay.trace.state.disabled");
                public static final LangEntry slotCount = ui("execution.overlay.trace.slot_count");
                public static final LangEntry phaseOpen = ui("execution.overlay.trace.phase.open");
                public static final LangEntry phasePrepare = ui("execution.overlay.trace.phase.prepare");
                public static final LangEntry phaseExecute = ui("execution.overlay.trace.phase.execute");
                public static final LangEntry phaseCollect = ui("execution.overlay.trace.phase.collect");

                private Trace() {}
            }

            public static final class Ingredients {
                public static final LangEntry sort = ui("execution.overlay.ingredients.sort");
                public static final LangEntry sortDesc = ui("execution.overlay.ingredients.sort.desc");
                public static final LangEntry sortModeMissing = ui("execution.overlay.ingredients.sort.mode.missing");
                public static final LangEntry sortModeAvailable = ui("execution.overlay.ingredients.sort.mode.available");
                public static final LangEntry sortModeName = ui("execution.overlay.ingredients.sort.mode.name");
                public static final LangEntry viewGrid = ui("execution.overlay.ingredients.view.grid");
                public static final LangEntry viewList = ui("execution.overlay.ingredients.view.list");
                public static final LangEntry viewGridDesc = ui("execution.overlay.ingredients.view.grid.desc");
                public static final LangEntry viewListDesc = ui("execution.overlay.ingredients.view.list.desc");
                public static final LangEntry groupMissing = ui("execution.overlay.ingredients.group.missing");
                public static final LangEntry groupAvailable = ui("execution.overlay.ingredients.group.available");
                public static final LangEntry groupCatalyst = ui("execution.overlay.ingredients.group.catalyst");

                private Ingredients() {}
            }

            public static final class Control {
                public static final LangEntry playback = ui("execution.overlay.control.playback");
                public static final LangEntry playbackStart = ui("execution.overlay.control.playback.start");
                public static final LangEntry playbackPause = ui("execution.overlay.control.playback.pause");
                public static final LangEntry step = ui("execution.overlay.control.step");
                public static final LangEntry stepDesc = ui("execution.overlay.control.step.desc");
                public static final LangEntry panelOpen = ui("execution.overlay.control.panel.open");
                public static final LangEntry panelClose = ui("execution.overlay.control.panel.close");

                private Control() {}
            }
        }

        public static final class Jei {
            private Jei() {}

            public static final class Target {
                public static final LangEntry tooltip = ui("execution.jei.target.tooltip");
                public static final LangEntry prefix = ui("execution.jei.target.prefix");
                public static final LangEntry count = ui("execution.jei.target.count");
                public static final LangEntry unsupported = ui("execution.jei.target.unsupported");

                private Target() {}
            }

        }
    }

    static {
        include(
                Calc.calculate,
                Calc.calculateDesc,
                Calc.setValue,
                Calc.calculating,
                Calc.empty,
                Calc.amount,
                Calc.amountDesc,
                Calc.amountMiddleClick,
                Calc.targetSlot,
                Calc.targetSlotEmpty,
                Calc.targetSlotDesc,
                Calc.actionMaterials,
                Calc.actionHome,
                Calc.actionTreeExpand,
                Calc.actionExecutionPath,
                Calc.actionExecutionPathDesc,
                Calc.actionSettings,
                Calc.actionSettingsDesc,
                Calc.View.tree,
                Calc.View.matrix,
                Calc.View.milestones,
                Calc.Tree.noRecipeData,
                Calc.Tree.expand,
                Calc.Tree.collapse,
                Calc.Tree.childCount,
                Calc.Tree.durationLine,
                Calc.Tree.machineCount,
                Calc.Tree.outputCount,
                Calc.Tree.materialList,
                Calc.Tree.materialSearchPlaceholder,
                Calc.Tree.totalTime,
                Calc.Tree.totalEnergy,
                Calc.Tree.stepCount,
                Calc.Tree.needShort,
                Calc.Tree.producedShort,
                Calc.Tree.missingShort,
                Calc.Tree.chanceShort,
                Calc.Tree.toolShort,
                Calc.Tree.category,
                Calc.Tree.required,
                Calc.Tree.consumed,
                Calc.Tree.produced,
                Calc.Tree.missing,
                Calc.Tree.usage,
                Calc.Tree.machineCandidates,
                Calc.Tree.current,
                Calc.Tree.candidate,
                Calc.Tree.candidates,
                Calc.Tree.more,
                Calc.Tree.close,
                Calc.Tree.clickSet,
                Calc.Tree.Material.externalInput,
                Calc.Tree.Material.byproduct,
                Calc.Tree.Material.catalyst,
                Calc.Tree.Material.intermediate,
                Calc.Matrix.title,
                Calc.Matrix.count,
                Calc.Matrix.output,
                Calc.Matrix.process,
                Calc.Matrix.input,
                Calc.Matrix.machine,
                Calc.Matrix.duration,
                Calc.Matrix.energy,
                Calc.Milestones.title,
                Calc.Milestones.defaultPhase,
                Calc.Milestones.collect,
                Calc.Milestones.make,
                Calc.Milestones.step,
                Calc.Milestones.source,
                Summary.title,
                Summary.inputs,
                Summary.catalysts,
                Summary.outputs,
                Summary.specs,
                Content.fluidContainer,
                Content.fluidContainerDesc,
                Calculator.open,
                Calculator.Jei.Open.tooltip,
                Calculator.Jei.Target.tooltip,
                Calculator.Jei.Target.unsupported,
                Calculator.Jei.Database.tooltip,
                Calculator.Jei.Database.tooltipCurrent,
                Calculator.Jei.Database.added,
                Calculator.Jei.Database.alreadyPresent,
                Calculator.Jei.Database.unsupported,
                Calculator.Jei.Database.failed,
                Calculator.Database.search,
                Calculator.Database.searchDesc,
                Calculator.Database.searchPredicateHasDesc,
                Calculator.Database.searchPredicateTextDesc,
                Calculator.Database.searchPredicateModDesc,
                Calculator.Database.searchPredicateTagDesc,
                Calculator.Database.searchPredicateTooltipDesc,
                Calculator.Database.searchPredicateIdDesc,
                Calculator.Database.searchPredicateNameDesc,
                Calculator.Database.searchPredicateOutDesc,
                Calculator.Database.searchPredicateInDesc,
                Calculator.Database.searchPredicateSystemDesc,
                Calculator.Database.searchPredicateDbDesc,
                Calculator.Database.searchPredicateMachineDesc,
                Calculator.Database.searchPredicateTimeDesc,
                Calculator.Database.searchPredicateEnergyDesc,
                Calculator.Database.searchPredicateCatDesc,
                Calculator.Database.searchPredicateByproductDesc,
                Calculator.Database.searchPredicateChanceDesc,
                Calculator.Database.searchPredicateTierDesc,
                Calculator.Database.searchPredicateLevelDesc,
                Calculator.Database.searchPredicateContextDesc,
                Calculator.Database.searchPredicatePlanDesc,
                Calculator.Database.searchPredicateProfileDesc,
                Calculator.Database.searchPredicateCompareDesc,
                Calculator.Database.searchPredicateExactDesc,
                Calculator.Database.searchPredicateWildcardDesc,
                Calculator.Database.searchPredicateNotDesc,
                Calculator.Database.searchPredicateQuoteDesc,
                Calculator.Database.searchPredicateOrDesc,
                Calculator.Database.searchPredicateGroupDesc,
                Calculator.Database.empty,
                Calculator.Database.invalidQuery,
                Calculator.Database.results,
                Calculator.Database.sources,
                Calculator.Database.sourcesDesc,
                Calculator.Database.sourcesBuiltinDesc,
                Calculator.Database.sourceAvailable,
                Calculator.Database.sourceEnabled,
                Calculator.Database.sourceDisabled,
                Calculator.Database.builtin,
                Calculator.Database.readOnlyBuiltinDesc,
                Calculator.Database.select,
                Calculator.Database.selectDesc,
                Calculator.Database.create,
                Calculator.Database.createDesc,
                Calculator.Database.createCancel,
                Calculator.Database.createCancelDesc,
                Calculator.Database.createNamePlaceholder,
                Calculator.Database.createNameDesc,
                Calculator.Database.createConfirm,
                Calculator.Database.createConfirmDesc,
                Calculator.Database.delete,
                Calculator.Database.deleteDesc,
                Calculator.Database.deleteBuiltinDesc,
                Calculator.Database.deleteConfirm,
                Calculator.Database.deleteConfirmDesc,
                Calculator.Database.systemRecipes,
                Calculator.Database.allResults,
                Calculator.Database.allResultsDesc,
                Calculator.Database.includeSystem,
                Calculator.Database.sourceRecipesDesc,
                Calculator.Database.recipeAvailable,
                Calculator.Database.recipeSelected,
                Calculator.Database.recipeIncludedBySource,
                Calculator.Database.recipeStatusAvailable,
                Calculator.Database.recipeStatusSelected,
                Calculator.Database.recipeStatusIncludedBySource,
                Calculator.Database.bulkSelect,
                Calculator.Database.bulkSelectDesc,
                Calculator.Database.bulkClear,
                Calculator.Database.bulkClearDesc,
                Calculator.Database.recipesMore,
                Calculator.Database.deleteCancel,
                Calculator.Database.deleteCancelDesc,
                Calculator.Database.sortAscending,
                Calculator.Database.sortDescending,
                Calculator.Database.viewGrid,
                Calculator.Database.viewList,
                Calculator.Database.viewGridDesc,
                Calculator.Database.viewListDesc,
                Calculator.Database.sort,
                Calculator.Database.sortDesc,
                Calculator.Database.sortRelevance,
                Calculator.Database.sortName,
                Calculator.Database.sortDatabase,
                Calculator.Database.sortSystem,
                Calculator.Database.current,
                Calculator.Database.next,
                Calculator.Database.systemGroup,
                Calculator.Database.active,
                Calculator.Database.details,
                Calculator.Database.preview,
                Calculator.Database.actionSetCalculator,
                Calculator.Database.actionSetCalculatorDesc,
                Calculator.Database.actionSetExecution,
                Calculator.Database.actionSetExecutionDesc,
                Calculator.Database.shortcutHeader,
                Calculator.Database.shortcutDefaultClick,
                Calculator.Database.shortcutSetCalculatorTarget,
                Calculator.Database.shortcutOpenCalculator,
                Calculator.Database.detailInputs,
                Calculator.Database.detailOutputs,
                Calculator.Database.contentsMore,
                Calculator.Database.database,
                Calculator.Database.system,
                Calculator.Database.effects,
                Calculator.Database.effectsNone,
                Calculator.Database.effectsMore,
                Calculator.Database.effectSettings,
                Calculator.Database.effectContext,
                Calculator.Database.effectContextDefault,
                Calculator.Database.effectRunnable,
                Calculator.Database.effectRunnableYes,
                Calculator.Database.effectRunnableNo,
                Calculator.Database.effectSource,
                Calculator.Database.effectPhase,
                Calculator.Database.effectBefore,
                Calculator.Database.effectAfter,
                Calculator.Database.effectDelta,
                Calculator.Database.effectFactor,
                Calculator.Database.candidates,
                Calculator.Database.candidatesOriginal,
                Calculator.Database.candidatesProjected,
                Calculator.Database.plugins,
                Calculator.Database.pluginsNone,
                Calculator.Database.pluginClause,
                Calculator.Database.pluginResolve,
                Calculator.Database.pluginOrder,
                Calculator.Database.parameters,
                Calculator.Database.parametersNone,
                Calculator.Database.Effect.input,
                Calculator.Database.Effect.output,
                Calculator.Database.Effect.added,
                Calculator.Database.Effect.removed,
                Calculator.Database.Effect.replaced,
                Calculator.Database.Effect.projectionTransformer,
                Calculator.Database.Effect.contentTransformer,
                Calculator.Database.Effect.resolveEffect,
                Calculator.Rules.empty,
                Calculator.Rules.sectionSummary,
                Calculator.Rules.sectionProfile,
                Calculator.Rules.sectionSourcePolicy,
                Calculator.Rules.sectionClauses,
                Calculator.Rules.sectionBuilder,
                Calculator.Rules.materialPicker,
                Calculator.Rules.materialPickerResults,
                Calculator.Rules.materialSearchPlaceholder,
                Calculator.Rules.materialSelected,
                Calculator.Rules.materialVisible,
                Calculator.Rules.advancedEditor,
                Calculator.Rules.sourceFilter,
                Calculator.Rules.scoreRule,
                Calculator.Rules.rejectRule,
                Calculator.Rules.penaltyRule,
                Calculator.Rules.rewardRule,
                Calculator.Rules.stockRule,
                Calculator.Rules.noClauses,
                Calculator.Rules.noSourceFilters,
                Calculator.Rules.readOnly,
                Calculator.Rules.enabled,
                Calculator.Rules.disabled,
                Calculator.Rules.ifLabel,
                Calculator.Rules.thenLabel,
                Calculator.Rules.fieldId,
                Calculator.Rules.fieldName,
                Calculator.Rules.fieldCollection,
                Calculator.Rules.fieldEditable,
                Calculator.Rules.fieldPreset,
                Calculator.Rules.fieldUnresolved,
                Calculator.Rules.fieldShortlist,
                Calculator.Rules.fieldRollback,
                Calculator.Rules.fieldTieEps,
                Calculator.Rules.fieldSoftAmp,
                Calculator.Rules.fieldHardAmp,
                Calculator.Rules.fieldPolicy,
                Calculator.Rules.fieldAction,
                Calculator.Rules.fieldWhen,
                Calculator.Rules.fieldOverride,
                Calculator.Rules.fieldSourceTag,
                Calculator.Rules.fieldMaterial,
                Calculator.Rules.fieldScoreKey,
                Calculator.Rules.fieldAmount,
                Calculator.Rules.fieldType,
                Calculator.Rules.fieldScope,
                Calculator.Rules.fieldStrength,
                Calculator.Rules.conditionAlways,
                Calculator.Rules.conditionTag,
                Calculator.Rules.conditionAll,
                Calculator.Rules.conditionAny,
                Calculator.Rules.conditionNone,
                Calculator.Rules.conditionNot,
                Calculator.Rules.conditionInputMaterial,
                Calculator.Rules.conditionOutputMaterial,
                Calculator.Rules.conditionAnyMaterial,
                Calculator.Rules.effectPenalty,
                Calculator.Rules.effectReward,
                Calculator.Rules.effectReject,
                Calculator.Rules.effectStock,
                Calculator.Rules.simpleReject,
                Calculator.Rules.simplePenalty,
                Calculator.Rules.simpleReward,
                Calculator.Rules.simpleStock,
                Calculator.Rules.scopeAny,
                Calculator.Rules.scopeInput,
                Calculator.Rules.scopeOutput,
                Calculator.Rules.hintReject,
                Calculator.Rules.hintPenalty,
                Calculator.Rules.hintReward,
                Calculator.Rules.hintStock,
                Calculator.Rules.actionExclude,
                Calculator.Rules.actionInclude,
                Calculator.Rules.actionOnly,
                Calculator.Rules.actionOverride,
                Calculator.Rules.actionExcludeShort,
                Calculator.Rules.actionIncludeShort,
                Calculator.Rules.actionOnlyShort,
                Calculator.Rules.actionNew,
                Calculator.Rules.actionCopy,
                Calculator.Rules.actionAdd,
                Calculator.Rules.actionApply,
                Calculator.Rules.actionRemove,
                Calculator.Rules.actionPick,
                Calculator.Rules.actionSelectFiltered,
                Calculator.Rules.actionClear,
                Calculator.Rules.actionAdvanced,
                Calculator.Rules.actionDetails,
                Calculator.Rules.actionBack,
                Calculator.Rules.actionPrevious,
                Calculator.Rules.actionNext,
                Calculator.Rules.newNamePlaceholder,
                Calculator.Settings.categoryExecution,
                Calculator.Settings.categoryPlanning,
                Calculator.Settings.categoryDatabase,
                Calculator.Settings.categoryRules,
                Calculator.Settings.trace,
                Calculator.Settings.traceDesc,
                Calculator.Settings.jeiTargetAutoClose,
                Calculator.Settings.jeiTargetAutoCloseDesc,
                Calculator.Settings.databaseAutoRefresh,
                Calculator.Settings.databaseAutoRefreshDesc,
                Calculator.Settings.projectionUsesGlobalDatabase,
                Calculator.Settings.projectionUsesGlobalDatabaseDesc,
                Calculator.Settings.databaseLimit,
                Calculator.Settings.databaseLimitDesc,
                Calculator.Settings.readOtherWorlds,
                Calculator.Settings.readOtherWorldsDesc,
                Calculator.Settings.calculatorRule,
                Calculator.Settings.calculatorRuleDesc,
                Calculator.Settings.calculatorRulePreview,
                Calculator.Settings.calculatorRuleActive,
                Calculator.Settings.calculatorRuleExecution,
                Calculator.Settings.calculatorRuleStrict,
                Calculator.Settings.calculatorRuleAssured,
                Calculator.Settings.useLiveStock,
                Calculator.Settings.useLiveStockDesc,
                Calculator.Settings.inventorySensitivity,
                Calculator.Settings.inventorySensitivityDesc,
                Calculator.Settings.inventorySensitivityHigh,
                Calculator.Settings.inventorySensitivityBalanced,
                Calculator.Settings.inventorySensitivityLow,
                Calculator.Settings.stockPolicy,
                Calculator.Settings.stockPolicyDesc,
                Calculator.Settings.stockPolicyConsume,
                Calculator.Settings.stockPolicyPreserve,
                Calculator.Settings.switchState,
                Calculator.Settings.switchOn,
                Calculator.Settings.switchOff,
                Calculator.Settings.currentValue,
                Calculator.Settings.range,
                Calculator.Settings.options,
                Execution.Overlay.empty,
                Execution.Overlay.calculating,
                Execution.Overlay.calculatingShort,
                Execution.Overlay.currentRecipe,
                Execution.Overlay.currentRecipeNone,
                Execution.Overlay.Dock.recipe,
                Execution.Overlay.Dock.timeline,
                Execution.Overlay.Dock.ingredients,
                Execution.Overlay.Dock.pagePrev,
                Execution.Overlay.Dock.pageNext,
                Execution.Overlay.Material.title,
                Execution.Overlay.Material.unique,
                Execution.Overlay.Material.need,
                Execution.Overlay.Material.have,
                Execution.Overlay.Material.miss,
                Execution.Overlay.Material.none,
                Execution.Overlay.Material.noneMissing,
                Execution.Overlay.Material.more,
                Execution.Overlay.Material.viewMissing,
                Execution.Overlay.Material.viewTotal,
                Execution.Overlay.Summary.target,
                Execution.Overlay.Summary.execution,
                Execution.Overlay.Summary.alert,
                Execution.Overlay.Summary.need,
                Execution.Overlay.Summary.rate,
                Execution.Overlay.Target.none,
                Execution.Overlay.Tooltip.current,
                Execution.Overlay.Tooltip.range,
                Execution.Overlay.Tooltip.edit,
                Execution.Overlay.Tooltip.progress,
                Execution.Overlay.Tooltip.steps,
                Execution.Overlay.Tooltip.status,
                Execution.Overlay.Tooltip.executing,
                Execution.Overlay.Tooltip.next,
                Execution.Overlay.Rate.hintDrag,
                Execution.Overlay.Rate.hintType,
                Execution.Overlay.Trace.title,
                Execution.Overlay.Trace.enable,
                Execution.Overlay.Trace.disable,
                Execution.Overlay.Trace.forceReopen,
                Execution.Overlay.Trace.stateEnabled,
                Execution.Overlay.Trace.stateDisabled,
                Execution.Overlay.Trace.slotCount,
                Execution.Overlay.Trace.phaseOpen,
                Execution.Overlay.Trace.phasePrepare,
                Execution.Overlay.Trace.phaseExecute,
                Execution.Overlay.Trace.phaseCollect,
                Execution.Overlay.Ingredients.sort,
                Execution.Overlay.Ingredients.sortDesc,
                Execution.Overlay.Ingredients.sortModeMissing,
                Execution.Overlay.Ingredients.sortModeAvailable,
                Execution.Overlay.Ingredients.sortModeName,
                Execution.Overlay.Ingredients.viewGrid,
                Execution.Overlay.Ingredients.viewList,
                Execution.Overlay.Ingredients.viewGridDesc,
                Execution.Overlay.Ingredients.viewListDesc,
                Execution.Overlay.Ingredients.groupMissing,
                Execution.Overlay.Ingredients.groupAvailable,
                Execution.Overlay.Ingredients.groupCatalyst,
                Execution.Overlay.Control.playback,
                Execution.Overlay.Control.playbackStart,
                Execution.Overlay.Control.playbackPause,
                Execution.Overlay.Control.step,
                Execution.Overlay.Control.stepDesc,
                Execution.Overlay.Control.panelOpen,
                Execution.Overlay.Control.panelClose,
                Execution.Jei.Target.tooltip,
                Execution.Jei.Target.prefix,
                Execution.Jei.Target.count,
                Execution.Jei.Target.unsupported
        );
    }
}
