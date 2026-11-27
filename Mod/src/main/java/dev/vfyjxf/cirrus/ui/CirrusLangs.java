package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CirrusLangs {
    private static final String root = "cirrus.ui.";
    private static final ArrayList<LangEntry> entries = new ArrayList<>();

    private CirrusLangs() {}

    public static List<LangEntry> entries() {
        return List.copyOf(entries);
    }

    private static LangEntry ui(String path) {
        return key(root + path);
    }

    private static LangEntry key(String key) {
        LangEntry entry = LangEntry.of(key);
        entries.add(entry);
        return entry;
    }

    private static LangEntry dynamic(String path) {
        return LangEntry.of(root + path);
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
        public static final LangEntry actionDeepen = ui("calc.action.deepen");
        public static final LangEntry actionDeepenDesc = ui("calc.action.deepen.desc");
        public static final LangEntry pendingBanner = ui("calc.pending_banner");

        private Calc() {}

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

    public static final class Planning {
        private Planning() {}

        public static final class Acquire {
            public static final LangEntry base = key("cirrus.acquire.base");
            public static final LangEntry costOverride = key("cirrus.acquire.cost_override");
            public static final LangEntry natural = key("cirrus.acquire.natural");
            public static final LangEntry playerFree = key("cirrus.acquire.player_free");
            public static final LangEntry unknown = key("cirrus.acquire.unknown");

            private Acquire() {}
        }

        public static final class Conflict {
            public static final LangEntry relaxRule = key("cirrus.conflict.relax_rule");
            public static final LangEntry relaxRuleSet = key("cirrus.conflict.relax_rule_set");
            public static final LangEntry tooManyRulesToLocalize = key("cirrus.conflict.too_many_rules_to_localize");

            private Conflict() {}
        }

        public static final class Prompt {
            private Prompt() {}

            public static final class MachineFork {
                public static final LangEntry title = key("cirrus.prompt.machine_fork.title");
                public static final LangEntry explanation = key("cirrus.prompt.machine_fork.explanation");
                public static final LangEntry optionAcceptBuild = key("cirrus.prompt.machine_fork.option.accept_build");
                public static final LangEntry optionAlreadyOwned = key(
                    "cirrus.prompt.machine_fork.option.already_owned"
                );
                public static final LangEntry optionRejectBuild = key("cirrus.prompt.machine_fork.option.reject_build");

                private MachineFork() {}
            }

            public static final class TagChoice {
                public static final LangEntry title = key("cirrus.prompt.tag_choice.title");
                public static final LangEntry explanation = key("cirrus.prompt.tag_choice.explanation");
                public static final LangEntry optionPick = key("cirrus.prompt.tag_choice.option.pick");

                private TagChoice() {}
            }

            public static final class ExpensiveLeaf {
                public static final LangEntry title = key("cirrus.prompt.expensive_leaf.title");
                public static final LangEntry explanation = key("cirrus.prompt.expensive_leaf.explanation");
                public static final LangEntry optionTreatFree = key("cirrus.prompt.expensive_leaf.option.treat_free");
                public static final LangEntry optionAvoidResource = key(
                    "cirrus.prompt.expensive_leaf.option.avoid_resource"
                );
                public static final LangEntry optionKeepGather = key("cirrus.prompt.expensive_leaf.option.keep_gather");

                private ExpensiveLeaf() {}
            }

            public static final class Suspicious {
                public static final LangEntry title = key("cirrus.prompt.suspicious.title");
                public static final LangEntry explanation = key("cirrus.prompt.suspicious.explanation");
                public static final LangEntry optionAcceptRecipe = key("cirrus.prompt.suspicious.option.accept_recipe");
                public static final LangEntry optionBanRecipe = key("cirrus.prompt.suspicious.option.ban_recipe");
                public static final LangEntry optionBanSystem = key("cirrus.prompt.suspicious.option.ban_system");

                private Suspicious() {}
            }
        }

        public static final class Strategy {
            public static final LangEntry balanced = key("cirrus.strategy.balanced");
            public static final LangEntry minHumanTime = key("cirrus.strategy.min_human_time");
            public static final LangEntry noNewMachines = key("cirrus.strategy.no_new_machines");
            public static final LangEntry minRawMaterials = key("cirrus.strategy.min_raw_materials");
            public static final LangEntry investAutomation = key("cirrus.strategy.invest_automation");
            public static final LangEntry alternative = key("cirrus.strategy.alternative");

            private Strategy() {}
        }
    }

    public static final class Workbench {
        private Workbench() {}

        public static final class Mode {
            public static final LangEntry planner = ui("workbench.mode.planner");
            public static final LangEntry facilities = ui("workbench.mode.facilities");
            public static final LangEntry database = ui("workbench.mode.database");
            public static final LangEntry settings = ui("workbench.mode.settings");

            private Mode() {}
        }

        public static final class Theme {
            public static final LangEntry obsidianBlueprint = ui("workbench.theme.obsidian_blueprint");
            public static final LangEntry atelierStone = ui("workbench.theme.atelier_stone");

            private Theme() {}
        }

        public static final class Tab {
            public static final LangEntry newTab = ui("workbench.tab.new_tab");
            public static final LangEntry fork = ui("workbench.tab.fork");
            public static final LangEntry rename = ui("workbench.tab.rename");
            public static final LangEntry close = ui("workbench.tab.close");
            public static final LangEntry closeOthers = ui("workbench.tab.close_others");
            public static final LangEntry basketBadge = ui("workbench.tab.basket_badge");
            public static final LangEntry maxTabsHint = ui("workbench.tab.max_tabs_hint");

            private Tab() {}
        }

        public static final class Topbar {
            public static final LangEntry searchPlaceholder = ui("workbench.topbar.search_placeholder");
            public static final LangEntry basket = ui("workbench.topbar.basket");
            public static final LangEntry addTarget = ui("workbench.topbar.add_target");
            public static final LangEntry preset = ui("workbench.topbar.preset");
            public static final LangEntry customWeights = ui("workbench.topbar.custom_weights");
            public static final LangEntry liveStock = ui("workbench.topbar.live_stock");
            public static final LangEntry liveStockOnDesc = ui("workbench.topbar.live_stock_on_desc");
            public static final LangEntry liveStockOffDesc = ui("workbench.topbar.live_stock_off_desc");
            public static final LangEntry liveStockFooter = ui("workbench.topbar.live_stock_footer");
            public static final LangEntry refreshStock = ui("workbench.topbar.refresh_stock");
            public static final LangEntry undo = ui("workbench.topbar.undo");
            public static final LangEntry redo = ui("workbench.topbar.redo");
            public static final LangEntry resetSession = ui("workbench.topbar.reset_session");
            public static final LangEntry deepen = ui("workbench.topbar.deepen");
            public static final LangEntry sendExecution = ui("workbench.topbar.send_execution");
            public static final LangEntry sentToast = ui("workbench.topbar.sent_toast");
            public static final LangEntry sentNoRecipe = ui("workbench.topbar.sent_no_recipe");
            public static final LangEntry sentNoRecipeTitle = ui("workbench.topbar.sent_no_recipe_title");
            public static final LangEntry sentToastTitle = ui("workbench.topbar.sent_toast_title");
            public static final LangEntry sentToastBadge = ui("workbench.topbar.sent_toast_badge");
            public static final LangEntry sentToastBody = ui("workbench.topbar.sent_toast_body");
            public static final LangEntry sentToastDetail = ui("workbench.topbar.sent_toast_detail");
            public static final LangEntry sentToastSimple = ui("workbench.topbar.sent_toast_simple");
            public static final LangEntry zenEnter = ui("workbench.topbar.zen_enter");
            public static final LangEntry zenExit = ui("workbench.topbar.zen_exit");
            public static final LangEntry cmdPaletteHint = ui("workbench.topbar.cmd_palette_hint");
            public static final LangEntry planNow = ui("workbench.topbar.plan_now");
            public static final LangEntry quickAdd = ui("workbench.topbar.quick_add");
            public static final LangEntry basketSummary = ui("workbench.topbar.basket_summary");
            public static final LangEntry autoReplan = ui("workbench.topbar.auto_replan");
            public static final LangEntry replanning = ui("workbench.topbar.replanning");

            private Topbar() {}
        }

        public static final class Axis {
            public static final LangEntry work = ui("workbench.axis.work");
            public static final LangEntry waitTime = ui("workbench.axis.wait");
            public static final LangEntry equip = ui("workbench.axis.equip");
            public static final LangEntry stock = ui("workbench.axis.stock");
            public static final LangEntry steps = ui("workbench.axis.steps");

            private Axis() {}
        }

        public static final class Palette {
            public static final LangEntry placeholder = ui("workbench.palette.placeholder");
            public static final LangEntry emptyRecent = ui("workbench.palette.empty_recent");
            public static final LangEntry emptyPrompts = ui("workbench.palette.empty_prompts");
            public static final LangEntry emptyCommands = ui("workbench.palette.empty_commands");
            public static final LangEntry hintAddBasket = ui("workbench.palette.hint_add_basket");
            public static final LangEntry hintPersistWorld = ui("workbench.palette.hint_persist_world");
            public static final LangEntry hintCommand = ui("workbench.palette.hint_command");
            public static final LangEntry hintResource = ui("workbench.palette.hint_resource");
            public static final LangEntry hintPrompt = ui("workbench.palette.hint_prompt");
            public static final LangEntry hintStep = ui("workbench.palette.hint_step");
            public static final LangEntry hintRecent = ui("workbench.palette.hint_recent");
            public static final LangEntry hintEscClose = ui("workbench.palette.hint_esc_close");
            public static final LangEntry noResults = ui("workbench.palette.no_results");
            public static final LangEntry categoryCurrentNode = ui("workbench.palette.category.current_node");
            public static final LangEntry categorySetTarget = ui("workbench.palette.category.set_target");
            public static final LangEntry categoryCommand = ui("workbench.palette.category.command");
            public static final LangEntry categoryPrompt = ui("workbench.palette.category.prompt");
            public static final LangEntry categoryRecentTarget = ui("workbench.palette.category.recent_target");

            private Palette() {}
        }

        public static final class Toast {
            public static final LangEntry worldPersistUnavailable = ui("workbench.toast.world_persist_unavailable");

            private Toast() {}
        }

        public static final class Flow {
            public static final LangEntry dag = ui("workbench.flow.dag");
            public static final LangEntry ledger = ui("workbench.flow.ledger");
            public static final LangEntry checklist = ui("workbench.flow.checklist");
            public static final LangEntry layoutLr = ui("workbench.flow.layout_lr");
            public static final LangEntry layoutTb = ui("workbench.flow.layout_tb");
            public static final LangEntry layoutRl = ui("workbench.flow.layout_rl");
            public static final LangEntry layoutBt = ui("workbench.flow.layout_bt");
            public static final LangEntry layoutSwimlane = ui("workbench.flow.layout_swimlane");
            public static final LangEntry edgeOrthogonal = ui("workbench.flow.edge_orthogonal");
            public static final LangEntry edgeStraight = ui("workbench.flow.edge_straight");
            public static final LangEntry edgeCurved = ui("workbench.flow.edge_curved");
            public static final LangEntry lodMacro = ui("workbench.flow.lod_macro");
            public static final LangEntry lodBlueprint = ui("workbench.flow.lod_blueprint");
            public static final LangEntry lodMicro = ui("workbench.flow.lod_micro");
            public static final LangEntry fit = ui("workbench.flow.fit");
            public static final LangEntry home = ui("workbench.flow.home");
            public static final LangEntry autoLayout = ui("workbench.flow.auto_layout");
            public static final LangEntry minimap = ui("workbench.flow.minimap");
            public static final LangEntry expandLoops = ui("workbench.flow.expand_loops");
            public static final LangEntry collapseLoops = ui("workbench.flow.collapse_loops");
            public static final LangEntry zoomPercent = ui("workbench.flow.zoom_percent");
            public static final LangEntry bannerPlanning = ui("workbench.flow.banner.planning");
            public static final LangEntry bannerNoTarget = ui("workbench.flow.banner.no_target");
            public static final LangEntry bannerConflict = ui("workbench.flow.banner.conflict");
            public static final LangEntry bannerUnreachable = ui("workbench.flow.banner.unreachable");
            public static final LangEntry bannerSatisfiedByStock = ui("workbench.flow.banner.satisfied_by_stock");
            public static final LangEntry locatorRuler = ui("workbench.flow.locator_ruler");
            public static final LangEntry locatorCollapseHint = ui("workbench.flow.locator_collapse_hint");
            public static final LangEntry locatorExpandHint = ui("workbench.flow.locator_expand_hint");
            public static final LangEntry emptyView = ui("workbench.flow.empty_view");
            public static final LangEntry nodeHandCraft = ui("workbench.flow.node.hand_craft");
            public static final LangEntry nodeAuto = ui("workbench.flow.node.auto");
            public static final LangEntry nodeByproducts = ui("workbench.flow.node.byproducts");
            public static final LangEntry nodeCraftedFirst = ui("workbench.flow.node.crafted_first");
            public static final LangEntry nodePlayerPrepared = ui("workbench.flow.node.player_prepared");
            public static final LangEntry nodeSetupLine = ui("workbench.flow.node.setup_line");
            public static final LangEntry nodeLinePull = ui("workbench.flow.node.line_pull");
            public static final LangEntry nodeReversiblePack = ui("workbench.flow.node.reversible_pack");
            public static final LangEntry nodeIterations = ui("workbench.flow.node.iterations");
            public static final LangEntry nodeWorkingSeed = ui("workbench.flow.node.working_seed");
            public static final LangEntry nodeNetDelta = ui("workbench.flow.node.net_delta");
            public static final LangEntry edgeSource = ui("workbench.flow.edge.source");
            public static final LangEntry edgeTarget = ui("workbench.flow.edge.target");
            public static final LangEntry edgeProduced = ui("workbench.flow.edge.produced");
            public static final LangEntry edgeConsumed = ui("workbench.flow.edge.consumed");
            public static final LangEntry selectionSummary = ui("workbench.flow.selection.summary");
            public static final LangEntry alignLeft = ui("workbench.flow.align_left");
            public static final LangEntry alignTop = ui("workbench.flow.align_top");
            public static final LangEntry alignRow = ui("workbench.flow.align_row");
            public static final LangEntry clearSelection = ui("workbench.flow.clear_selection");
            public static final LangEntry laneAcquireLines = ui("workbench.flow.lane.acquire_lines");
            public static final LangEntry laneMachinesLines = ui("workbench.flow.lane.machines_lines");
            public static final LangEntry kindAcquire = ui("workbench.flow.kind.acquire");
            public static final LangEntry kindCraft = ui("workbench.flow.kind.craft");
            public static final LangEntry kindConvert = ui("workbench.flow.kind.convert");
            public static final LangEntry kindCycle = ui("workbench.flow.kind.cycle");
            public static final LangEntry kindBuild = ui("workbench.flow.kind.build");
            public static final LangEntry kindLine = ui("workbench.flow.kind.line");
            public static final LangEntry kindPull = ui("workbench.flow.kind.pull");
            public static final LangEntry timeInstant = ui("workbench.flow.time.instant");
            public static final LangEntry timeWork = ui("workbench.flow.time.work");
            public static final LangEntry timeWait = ui("workbench.flow.time.wait");
            public static final LangEntry timeBoth = ui("workbench.flow.time.both");
            public static final LangEntry inputsPrefix = ui("workbench.flow.inputs_prefix");
            public static final LangEntry fromPrefix = ui("workbench.flow.from_prefix");
            public static final LangEntry buildCount = ui("workbench.flow.build_count");
            public static final LangEntry batchesSuffix = ui("workbench.flow.batches_suffix");

            private Flow() {}
        }

        public static final class Ledger {
            public static final LangEntry acquire = ui("workbench.ledger.acquire");
            public static final LangEntry stockReserved = ui("workbench.ledger.stock_reserved");
            public static final LangEntry machinesLines = ui("workbench.ledger.machines_lines");
            public static final LangEntry surplus = ui("workbench.ledger.surplus");
            public static final LangEntry whatIfStock = ui("workbench.ledger.what_if_stock");
            public static final LangEntry stockCoverage = ui("workbench.ledger.stock_coverage");
            public static final LangEntry stackBreakdown = ui("workbench.ledger.stack_breakdown");
            public static final LangEntry available = ui("workbench.ledger.available");
            public static final LangEntry consumed = ui("workbench.ledger.consumed");
            public static final LangEntry reserved = ui("workbench.ledger.reserved");
            public static final LangEntry need = ui("workbench.ledger.need");
            public static final LangEntry have = ui("workbench.ledger.have");
            public static final LangEntry missing = ui("workbench.ledger.missing");
            public static final LangEntry done = ui("workbench.ledger.done");
            public static final LangEntry haveAll = ui("workbench.ledger.have_all");
            public static final LangEntry ownedStatus = ui("workbench.ledger.owned_status");
            public static final LangEntry needBuildStatus = ui("workbench.ledger.need_build_status");
            public static final LangEntry emptySection = ui("workbench.ledger.empty_section");

            private Ledger() {}
        }

        public static final class Checklist {
            public static final LangEntry probeAuto = ui("workbench.checklist.probe_auto");
            public static final LangEntry probeGuide = ui("workbench.checklist.probe_guide");
            public static final LangEntry probeManual = ui("workbench.checklist.probe_manual");
            public static final LangEntry preTasks = ui("workbench.checklist.pre_tasks");
            public static final LangEntry closeOnSend = ui("workbench.checklist.close_on_send");
            public static final LangEntry summary = ui("workbench.checklist.summary");
            public static final LangEntry phasePrepare = ui("workbench.checklist.phase.prepare");
            public static final LangEntry phaseBuild = ui("workbench.checklist.phase.build");
            public static final LangEntry phaseCraft = ui("workbench.checklist.phase.craft");
            public static final LangEntry phaseFinal = ui("workbench.checklist.phase.final");
            public static final LangEntry stepAcquire = ui("workbench.checklist.step.acquire");
            public static final LangEntry stepCraft = ui("workbench.checklist.step.craft");
            public static final LangEntry stepConvert = ui("workbench.checklist.step.convert");
            public static final LangEntry stepProvision = ui("workbench.checklist.step.provision");
            public static final LangEntry stepSetupLine = ui("workbench.checklist.step.setup_line");
            public static final LangEntry stepLinePull = ui("workbench.checklist.step.line_pull");
            public static final LangEntry stepCycle = ui("workbench.checklist.step.cycle");

            private Checklist() {}
        }

        public static final class Pareto {
            public static final LangEntry cardsMode = ui("workbench.pareto.cards_mode");
            public static final LangEntry scatterMode = ui("workbench.pareto.scatter_mode");
            public static final LangEntry xAxis = ui("workbench.pareto.x_axis");
            public static final LangEntry yAxis = ui("workbench.pareto.y_axis");
            public static final LangEntry betterDir = ui("workbench.pareto.better_dir");
            public static final LangEntry compareSlotHint = ui("workbench.pareto.compare_slot_hint");
            public static final LangEntry compareSlotSingleHint = ui("workbench.pareto.compare_slot_single_hint");
            public static final LangEntry compareSlotSamePlanHint = ui("workbench.pareto.compare_slot_same_plan_hint");
            public static final LangEntry compareSlotTitle = ui("workbench.pareto.compare_slot_title");
            public static final LangEntry compareSlotTooltipBody = ui("workbench.pareto.compare_slot_tooltip_body");
            public static final LangEntry compareSlotTooltipSingle = ui("workbench.pareto.compare_slot_tooltip_single");
            public static final LangEntry compareSlotTooltipClear = ui("workbench.pareto.compare_slot_tooltip_clear");
            public static final LangEntry cardFooterActive = ui("workbench.pareto.card_footer_active");
            public static final LangEntry cardFooterCandidate = ui("workbench.pareto.card_footer_candidate");
            public static final LangEntry diffAdded = ui("workbench.pareto.diff_added");
            public static final LangEntry diffRemoved = ui("workbench.pareto.diff_removed");
            public static final LangEntry certValid = ui("workbench.pareto.cert_valid");
            public static final LangEntry certInvalid = ui("workbench.pareto.cert_invalid");
            public static final LangEntry badgeReady = ui("workbench.pareto.badge_ready");
            public static final LangEntry badgeWarn = ui("workbench.pareto.badge_warn");
            public static final LangEntry cardSummary = ui("workbench.pareto.card_summary");
            public static final LangEntry cardSummaryNoMachines = ui("workbench.pareto.card_summary_no_machines");

            private Pareto() {}
        }

        public static final class Copilot {
            public static final LangEntry tabPrompts = ui("workbench.copilot.tab_prompts");
            public static final LangEntry tabInspector = ui("workbench.copilot.tab_inspector");
            public static final LangEntry tabDrop = ui("workbench.copilot.tab_drop");
            public static final LangEntry leverage = ui("workbench.copilot.leverage");
            public static final LangEntry delta = ui("workbench.copilot.delta");
            public static final LangEntry scopeSession = ui("workbench.copilot.scope_session");
            public static final LangEntry scopeWorld = ui("workbench.copilot.scope_world");
            public static final LangEntry skip = ui("workbench.copilot.skip");
            public static final LangEntry allTagCandidates = ui("workbench.copilot.all_tag_candidates");
            public static final LangEntry collapseTagCandidates = ui("workbench.copilot.collapse_tag_candidates");
            public static final LangEntry tagCandidateTooltip = ui("workbench.copilot.tag_candidate_tooltip");
            public static final LangEntry ownedCountLabel = ui("workbench.copilot.owned_count_label");
            public static final LangEntry declareOwnedAction = ui("workbench.copilot.declare_owned_action");
            public static final LangEntry declareStockLabel = ui("workbench.copilot.declare_stock_label");
            public static final LangEntry declareStockApply = ui("workbench.copilot.declare_stock_apply");
            public static final LangEntry switchAltRecipe = ui("workbench.copilot.switch_alt_recipe");
            public static final LangEntry dropFreeHint = ui("workbench.copilot.drop_free_hint");
            public static final LangEntry dropBanHint = ui("workbench.copilot.drop_ban_hint");
            public static final LangEntry dropEffectFree = ui("workbench.copilot.drop_effect_free");
            public static final LangEntry dropEffectOwned = ui("workbench.copilot.drop_effect_owned");
            public static final LangEntry dropEffectBan = ui("workbench.copilot.drop_effect_ban");
            public static final LangEntry emptyClean = ui("workbench.copilot.empty_clean");
            public static final LangEntry impactPercent = ui("workbench.copilot.impact_percent");
            public static final LangEntry promptKindMachine = ui("workbench.copilot.prompt_kind.machine");
            public static final LangEntry promptKindTag = ui("workbench.copilot.prompt_kind.tag");
            public static final LangEntry promptKindLeaf = ui("workbench.copilot.prompt_kind.leaf");
            public static final LangEntry promptKindRecipe = ui("workbench.copilot.prompt_kind.recipe");
            public static final LangEntry tagSearchPlaceholder = ui("workbench.copilot.tag_search_placeholder");
            public static final LangEntry tagSelectHint = ui("workbench.copilot.tag_select_hint");
            public static final LangEntry tagNoMatch = ui("workbench.copilot.tag_no_match");
            public static final LangEntry applyOption = ui("workbench.copilot.apply_option");
            public static final LangEntry appliedOption = ui("workbench.copilot.applied_option");
            public static final LangEntry alternativeRecipes = ui("workbench.copilot.alternative_recipes");
            public static final LangEntry useRecipe = ui("workbench.copilot.use_recipe");
            public static final LangEntry activeRecipe = ui("workbench.copilot.active_recipe");
            public static final LangEntry noAlternatives = ui("workbench.copilot.no_alternatives");
            public static final LangEntry selectStepHint = ui("workbench.copilot.select_step_hint");
            public static final LangEntry inputsLabel = ui("workbench.copilot.inputs_label");
            public static final LangEntry outputLabel = ui("workbench.copilot.output_label");
            public static final LangEntry machineLabel = ui("workbench.copilot.machine_label");
            public static final LangEntry routeActions = ui("workbench.copilot.route_actions");

            private Copilot() {}
        }

        public static final class Dnd {
            public static final LangEntry dropAddTarget = ui("workbench.dnd.drop_add_target");
            public static final LangEntry dropReplaceTarget = ui("workbench.dnd.drop_replace_target");
            public static final LangEntry dropOverrideStock = ui("workbench.dnd.drop_override_stock");
            public static final LangEntry dropFreeSource = ui("workbench.dnd.drop_free_source");
            public static final LangEntry dropBanResource = ui("workbench.dnd.drop_ban_resource");
            public static final LangEntry dropOwnMachine = ui("workbench.dnd.drop_own_machine");
            public static final LangEntry dropPinRecipe = ui("workbench.dnd.drop_pin_recipe");
            public static final LangEntry dropComparePlan = ui("workbench.dnd.drop_compare_plan");
            public static final LangEntry dropInvalid = ui("workbench.dnd.drop_invalid");

            private Dnd() {}
        }

        public static final class Timeline {
            public static final LangEntry initial = ui("workbench.timeline.initial");
            public static final LangEntry promoteWorld = ui("workbench.timeline.promote_world");
            public static final LangEntry promoteAllWorld = ui("workbench.timeline.promote_all_world");
            public static final LangEntry saveCountWorld = ui("workbench.timeline.save_count_world");
            public static final LangEntry removeRule = ui("workbench.timeline.remove_rule");
            public static final LangEntry jumpHint = ui("workbench.timeline.jump_hint");

            private Timeline() {}
        }

        public static final class Action {
            public static final LangEntry replaceRecipe = ui("workbench.action.replace_recipe");
            public static final LangEntry banRecipe = ui("workbench.action.ban_recipe");
            public static final LangEntry banSystem = ui("workbench.action.ban_system");
            public static final LangEntry breakCycle = ui("workbench.action.break_cycle");
            public static final LangEntry declareOwned = ui("workbench.action.declare_owned");
            public static final LangEntry declareOwnedMulti = ui("workbench.action.declare_owned_multi");
            public static final LangEntry modPrefer = ui("workbench.action.mod_prefer");
            public static final LangEntry modPenalize = ui("workbench.action.mod_penalize");
            public static final LangEntry modBan = ui("workbench.action.mod_ban");
            public static final LangEntry overrideStock = ui("workbench.action.override_stock");
            public static final LangEntry treatFree = ui("workbench.action.treat_free");
            public static final LangEntry costOverride = ui("workbench.action.cost_override");
            public static final LangEntry banResource = ui("workbench.action.ban_resource");
            public static final LangEntry relaxOne = ui("workbench.action.relax_one");
            public static final LangEntry relaxAll = ui("workbench.action.relax_all");
            public static final LangEntry locateDag = ui("workbench.action.locate_dag");
            public static final LangEntry openInDb = ui("workbench.action.open_in_db");
            public static final LangEntry batchBan = ui("workbench.action.batch_ban");
            public static final LangEntry batchOwn = ui("workbench.action.batch_own");

            private Action() {}
        }

        public static final class Facilities {
            public static final LangEntry floorMachines = ui("workbench.facilities.floor_machines");
            public static final LangEntry lines = ui("workbench.facilities.lines");
            public static final LangEntry autoRecipes = ui("workbench.facilities.auto_recipes");
            public static final LangEntry optIn = ui("workbench.facilities.opt_in");
            public static final LangEntry rules = ui("workbench.facilities.rules");
            public static final LangEntry add = ui("workbench.facilities.add");
            public static final LangEntry edit = ui("workbench.facilities.edit");
            public static final LangEntry delete = ui("workbench.facilities.delete");
            public static final LangEntry exportJson = ui("workbench.facilities.export_json");
            public static final LangEntry importJson = ui("workbench.facilities.import_json");
            public static final LangEntry detachedBanner = ui("workbench.facilities.detached_banner");
            public static final LangEntry sentencePrefix = ui("workbench.facilities.sentence_prefix");
            public static final LangEntry searchPlaceholder = ui("workbench.facilities.search_placeholder");
            public static final LangEntry boundWorld = ui("workbench.facilities.bound_world");
            public static final LangEntry exportOk = ui("workbench.facilities.export_ok");
            public static final LangEntry exportFailed = ui("workbench.facilities.export_failed");
            public static final LangEntry importOk = ui("workbench.facilities.import_ok");
            public static final LangEntry importFailed = ui("workbench.facilities.import_failed");
            public static final LangEntry declareMachine = ui("workbench.facilities.declare_machine");
            public static final LangEntry machineSearch = ui("workbench.facilities.machine_search");
            public static final LangEntry noGraph = ui("workbench.facilities.no_graph");
            public static final LangEntry speedMultiplier = ui("workbench.facilities.speed_multiplier");
            public static final LangEntry units = ui("workbench.facilities.units");
            public static final LangEntry autoFeed = ui("workbench.facilities.auto_feed");
            public static final LangEntry emptyMachines = ui("workbench.facilities.empty_machines");
            public static final LangEntry newLine = ui("workbench.facilities.new_line");
            public static final LangEntry throughput = ui("workbench.facilities.throughput");
            public static final LangEntry bufferCap = ui("workbench.facilities.buffer_cap");
            public static final LangEntry pullCost = ui("workbench.facilities.pull_cost");
            public static final LangEntry lineEnabled = ui("workbench.facilities.line_enabled");
            public static final LangEntry emptyLines = ui("workbench.facilities.empty_lines");
            public static final LangEntry importSession = ui("workbench.facilities.import_session");
            public static final LangEntry importOkCount = ui("workbench.facilities.import_ok_count");
            public static final LangEntry emptyRecipes = ui("workbench.facilities.empty_recipes");
            public static final LangEntry batchTime = ui("workbench.facilities.batch_time");
            public static final LangEntry parallelSlots = ui("workbench.facilities.parallel_slots");
            public static final LangEntry optInHint = ui("workbench.facilities.opt_in_hint");
            public static final LangEntry recipeCount = ui("workbench.facilities.recipe_count");
            public static final LangEntry unlock = ui("workbench.facilities.unlock");
            public static final LangEntry emptyOptIn = ui("workbench.facilities.empty_opt_in");
            public static final LangEntry ruleSearch = ui("workbench.facilities.rule_search");
            public static final LangEntry scopeAll = ui("workbench.facilities.scope_all");
            public static final LangEntry scopeDefault = ui("workbench.facilities.scope_default");
            public static final LangEntry strengthAny = ui("workbench.facilities.strength_any");
            public static final LangEntry typeAny = ui("workbench.facilities.type_any");
            public static final LangEntry newRule = ui("workbench.facilities.new_rule");
            public static final LangEntry emptyRules = ui("workbench.facilities.empty_rules");
            public static final LangEntry ruleType = ui("workbench.facilities.rule_type");
            public static final LangEntry saveRule = ui("workbench.facilities.save_rule");
            public static final LangEntry cancel = ui("workbench.facilities.cancel");
            public static final LangEntry fieldSearch = ui("workbench.facilities.field_search");
            public static final LangEntry missingField = ui("workbench.facilities.missing_field");
            public static final LangEntry scopeSession = ui("workbench.facilities.scope.session");
            public static final LangEntry scopeWorld = ui("workbench.facilities.scope.world");
            public static final LangEntry scopePackDefault = ui("workbench.facilities.scope.default");
            public static final LangEntry scopeQuery = ui("workbench.facilities.scope.query");
            public static final LangEntry strengthHardBan = ui("workbench.facilities.strength.hard_ban");
            public static final LangEntry strengthHardPin = ui("workbench.facilities.strength.hard_pin");
            public static final LangEntry strengthSoftPrefer = ui("workbench.facilities.strength.soft_prefer");
            public static final LangEntry strengthSoftPenalize = ui("workbench.facilities.strength.soft_penalize");
            public static final LangEntry ruleKindRecipe = ui("workbench.facilities.rule_kind.recipe");
            public static final LangEntry ruleKindSystem = ui("workbench.facilities.rule_kind.system");
            public static final LangEntry ruleKindMachine = ui("workbench.facilities.rule_kind.machine");
            public static final LangEntry ruleKindResource = ui("workbench.facilities.rule_kind.resource");
            public static final LangEntry ruleKindTag = ui("workbench.facilities.rule_kind.tag");
            public static final LangEntry ruleKindCycle = ui("workbench.facilities.rule_kind.cycle");
            public static final LangEntry ruleKindCost = ui("workbench.facilities.rule_kind.cost");
            public static final LangEntry ruleKindMod = ui("workbench.facilities.rule_kind.mod");
            public static final LangEntry ruleFieldStrength = ui("workbench.facilities.rule_field.strength");
            public static final LangEntry ruleFieldScope = ui("workbench.facilities.rule_field.scope");
            public static final LangEntry ruleFieldResource = ui("workbench.facilities.rule_field.resource");
            public static final LangEntry ruleFieldRecipe = ui("workbench.facilities.rule_field.recipe");
            public static final LangEntry ruleFieldMachine = ui("workbench.facilities.rule_field.machine");
            public static final LangEntry ruleFieldSystem = ui("workbench.facilities.rule_field.system");
            public static final LangEntry ruleFieldTag = ui("workbench.facilities.rule_field.tag");
            public static final LangEntry ruleFieldMember = ui("workbench.facilities.rule_field.member");
            public static final LangEntry ruleFieldMod = ui("workbench.facilities.rule_field.mod");
            public static final LangEntry ruleFieldH = ui("workbench.facilities.rule_field.h");
            public static final LangEntry ruleFieldP = ui("workbench.facilities.rule_field.p");
            public static final LangEntry ruleFieldK = ui("workbench.facilities.rule_field.k");
            public static final LangEntry ruleFieldS = ui("workbench.facilities.rule_field.s");
            public static final LangEntry ruleFieldC = ui("workbench.facilities.rule_field.c");
            public static final LangEntry ruleFieldOptIn = ui("workbench.facilities.rule_field.opt_in");
            public static final LangEntry ruleFieldAllowBuild = ui("workbench.facilities.rule_field.allow_build");
            public static final LangEntry ruleFieldFreeBase = ui("workbench.facilities.rule_field.free_base");
            public static final LangEntry ruleFieldTargetResource = ui(
                "workbench.facilities.rule_field.target_resource"
            );
            public static final LangEntry ruleFieldBannedRecipe = ui("workbench.facilities.rule_field.banned_recipe");
            public static final LangEntry ruleFieldOn = ui("workbench.facilities.rule_field.on");
            public static final LangEntry ruleFieldOff = ui("workbench.facilities.rule_field.off");
            public static final LangEntry ruleSentenceRecipe = ui("workbench.facilities.rule_sentence.recipe");
            public static final LangEntry ruleSentenceSystem = ui("workbench.facilities.rule_sentence.system");
            public static final LangEntry ruleSentenceMachine = ui("workbench.facilities.rule_sentence.machine");
            public static final LangEntry ruleSentenceResource = ui("workbench.facilities.rule_sentence.resource");
            public static final LangEntry ruleSentenceTag = ui("workbench.facilities.rule_sentence.tag");
            public static final LangEntry ruleSentenceCycle = ui("workbench.facilities.rule_sentence.cycle");
            public static final LangEntry ruleSentenceCost = ui("workbench.facilities.rule_sentence.cost");
            public static final LangEntry ruleSentenceMod = ui("workbench.facilities.rule_sentence.mod");

            private Facilities() {}
        }

        public static final class Settings {
            public static final LangEntry appearance = ui("workbench.settings.appearance");
            public static final LangEntry planner = ui("workbench.settings.planner");
            public static final LangEntry execution = ui("workbench.settings.execution");
            public static final LangEntry database = ui("workbench.settings.database");
            public static final LangEntry diagnostics = ui("workbench.settings.diagnostics");
            public static final LangEntry reducedMotion = ui("workbench.settings.reduced_motion");
            public static final LangEntry gridSnap = ui("workbench.settings.grid_snap");
            public static final LangEntry softBudget = ui("workbench.settings.soft_budget");
            public static final LangEntry hardBudget = ui("workbench.settings.hard_budget");
            public static final LangEntry defaultScope = ui("workbench.settings.default_scope");
            public static final LangEntry progression = ui("workbench.settings.progression");
            public static final LangEntry recompile = ui("workbench.settings.recompile");
            public static final LangEntry exportStats = ui("workbench.settings.export_stats");
            public static final LangEntry theme = ui("workbench.settings.theme");
            public static final LangEntry dagLayout = ui("workbench.settings.dag_layout");
            public static final LangEntry dagEdgeStyle = ui("workbench.settings.dag_edge_style");
            public static final LangEntry defaultPreset = ui("workbench.settings.default_preset");
            public static final LangEntry persistWorld = ui("workbench.settings.persist_world");
            public static final LangEntry keepRate = ui("workbench.settings.keep_rate");
            public static final LangEntry compileRate = ui("workbench.settings.compile_rate");
            public static final LangEntry systemHealth = ui("workbench.settings.system_health");
            public static final LangEntry recompileRunning = ui("workbench.settings.recompile_running");
            public static final LangEntry recompileDone = ui("workbench.settings.recompile_done");
            public static final LangEntry exportStatsOk = ui("workbench.settings.export_stats_ok");
            public static final LangEntry exportStatsFailed = ui("workbench.settings.export_stats_failed");
            public static final LangEntry noGraph = ui("workbench.settings.no_graph");
            public static final LangEntry progressionOverworld = ui("workbench.settings.progression.overworld");
            public static final LangEntry progressionNether = ui("workbench.settings.progression.nether");
            public static final LangEntry progressionTheEnd = ui("workbench.settings.progression.the_end");
            public static final LangEntry statPublishedVersion = ui("workbench.settings.stat.published_version");
            public static final LangEntry statAttachEpoch = ui("workbench.settings.stat.attach_epoch");
            public static final LangEntry statTotalRaw = ui("workbench.settings.stat.total_raw");
            public static final LangEntry statCompiled = ui("workbench.settings.stat.compiled");
            public static final LangEntry statExcludedDisplay = ui("workbench.settings.stat.excluded_display");
            public static final LangEntry statReversibleEdges = ui("workbench.settings.stat.reversible_edges");
            public static final LangEntry statCycleMacros = ui("workbench.settings.stat.cycle_macros");
            public static final LangEntry statLargestScc = ui("workbench.settings.stat.largest_scc");
            public static final LangEntry statSystemsVisited = ui("workbench.settings.stat.systems_visited");
            public static final LangEntry statSystemsFailed = ui("workbench.settings.stat.systems_failed");
            public static final LangEntry statCaptureFailed = ui("workbench.settings.stat.capture_failed");

            private Settings() {}
        }
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

            public static final class PlanFlow {
                public static final LangEntry title = ui("calculator.jei.plan_flow.title");
                public static final LangEntry openCalc = ui("calculator.jei.plan_flow.open_calc");
                public static final LangEntry openCalcDesc = ui("calculator.jei.plan_flow.open_calc_desc");
                public static final LangEntry sendExec = ui("calculator.jei.plan_flow.send_exec");
                public static final LangEntry sendExecDesc = ui("calculator.jei.plan_flow.send_exec_desc");
                public static final LangEntry scaleMinus = ui("calculator.jei.plan_flow.scale_minus");
                public static final LangEntry scalePlus = ui("calculator.jei.plan_flow.scale_plus");
                public static final LangEntry scaleReset = ui("calculator.jei.plan_flow.scale_reset");
                public static final LangEntry scaleHint = ui("calculator.jei.plan_flow.scale_hint");
                public static final LangEntry tabFlow = ui("calculator.jei.plan_flow.tab_flow");
                public static final LangEntry tabStations = ui("calculator.jei.plan_flow.tab_stations");
                public static final LangEntry metaSteps = ui("calculator.jei.plan_flow.meta_steps");
                public static final LangEntry dagFit = ui("calculator.jei.plan_flow.dag_fit");
                public static final LangEntry dagToggleDir = ui("calculator.jei.plan_flow.dag_toggle_dir");
                public static final LangEntry dagZoomIn = ui("calculator.jei.plan_flow.dag_zoom_in");
                public static final LangEntry dagZoomOut = ui("calculator.jei.plan_flow.dag_zoom_out");
                public static final LangEntry dagActionRecipe = ui("calculator.jei.plan_flow.dag_action_recipe");
                public static final LangEntry dagActionDone = ui("calculator.jei.plan_flow.dag_action_done");
                public static final LangEntry dagActionMark = ui("calculator.jei.plan_flow.dag_action_mark");
                public static final LangEntry dagTooltipDeps = ui("calculator.jei.plan_flow.dag_tooltip_deps");
                public static final LangEntry dagTooltipRoot = ui("calculator.jei.plan_flow.dag_tooltip_root");
                public static final LangEntry stationsTitle = ui("calculator.jei.plan_flow.stations_title");
                public static final LangEntry stationsEmpty = ui("calculator.jei.plan_flow.stations_empty");
                public static final LangEntry stationsHandCraft = ui("calculator.jei.plan_flow.stations_hand_craft");
                public static final LangEntry stationsBuild = ui("calculator.jei.plan_flow.stations_build");
                public static final LangEntry stationsAuto = ui("calculator.jei.plan_flow.stations_auto");
                public static final LangEntry stationsMeta = ui("calculator.jei.plan_flow.stations_meta");
                public static final LangEntry stationsCatalysts = ui("calculator.jei.plan_flow.stations_catalysts");
                public static final LangEntry stationsSurplus = ui("calculator.jei.plan_flow.stations_surplus");
                public static final LangEntry stationsTipClick = ui("calculator.jei.plan_flow.stations_tip_click");
                public static final LangEntry dockTitle = ui("calculator.jei.plan_flow.dock_title");
                public static final LangEntry dockFilterAll = ui("calculator.jei.plan_flow.dock_filter_all");
                public static final LangEntry dockFilterMissing = ui("calculator.jei.plan_flow.dock_filter_missing");
                public static final LangEntry dockFilterSteps = ui("calculator.jei.plan_flow.dock_filter_steps");
                public static final LangEntry dockFilterTip = ui("calculator.jei.plan_flow.dock_filter_tip");
                public static final LangEntry dockSortMissing = ui("calculator.jei.plan_flow.dock_sort_missing");
                public static final LangEntry dockSortAmount = ui("calculator.jei.plan_flow.dock_sort_amount");
                public static final LangEntry dockSortReady = ui("calculator.jei.plan_flow.dock_sort_ready");
                public static final LangEntry dockViewGrid = ui("calculator.jei.plan_flow.dock_view_grid");
                public static final LangEntry dockViewList = ui("calculator.jei.plan_flow.dock_view_list");
                public static final LangEntry dockMenuTitle = ui("calculator.jei.plan_flow.dock_menu_title");
                public static final LangEntry dockMenuFilter = ui("calculator.jei.plan_flow.dock_menu_filter");
                public static final LangEntry dockMenuSort = ui("calculator.jei.plan_flow.dock_menu_sort");
                public static final LangEntry dockMenuView = ui("calculator.jei.plan_flow.dock_menu_view");
                public static final LangEntry dockMenuUnit = ui("calculator.jei.plan_flow.dock_menu_unit");
                public static final LangEntry dockMenuProgress = ui("calculator.jei.plan_flow.dock_menu_progress");
                public static final LangEntry dockSortShortLabel = ui("calculator.jei.plan_flow.dock_sort_short_label");
                public static final LangEntry dockSortQtyLabel = ui("calculator.jei.plan_flow.dock_sort_qty_label");
                public static final LangEntry dockSortReadyLabel = ui("calculator.jei.plan_flow.dock_sort_ready_label");
                public static final LangEntry dockViewGridLabel = ui("calculator.jei.plan_flow.dock_view_grid_label");
                public static final LangEntry dockViewListLabel = ui("calculator.jei.plan_flow.dock_view_list_label");
                public static final LangEntry dockUnitStack = ui("calculator.jei.plan_flow.dock_unit_stack");
                public static final LangEntry dockUnitRaw = ui("calculator.jei.plan_flow.dock_unit_raw");
                public static final LangEntry dockUnitCountTip = ui("calculator.jei.plan_flow.dock_unit_count_tip");
                public static final LangEntry dockUnitStackTip = ui("calculator.jei.plan_flow.dock_unit_stack_tip");
                public static final LangEntry dockUnitTip = ui("calculator.jei.plan_flow.dock_unit_tip");
                public static final LangEntry dockUnitStackSuffix = ui("calculator.jei.plan_flow.dock_unit_stack_suffix");
                public static final LangEntry dockResetSteps = ui("calculator.jei.plan_flow.dock_reset_steps");
                public static final LangEntry dockEmptyMissing = ui("calculator.jei.plan_flow.dock_empty_missing");
                public static final LangEntry dockEmptyAll = ui("calculator.jei.plan_flow.dock_empty_all");
                public static final LangEntry dockTipNeed = ui("calculator.jei.plan_flow.dock_tip_need");
                public static final LangEntry dockTipHave = ui("calculator.jei.plan_flow.dock_tip_have");
                public static final LangEntry dockTipMissing = ui("calculator.jei.plan_flow.dock_tip_missing");
                public static final LangEntry dockTipReady = ui("calculator.jei.plan_flow.dock_tip_ready");
                public static final LangEntry dockTipUnitHint = ui("calculator.jei.plan_flow.dock_tip_unit_hint");
                public static final LangEntry dockTipActions = ui("calculator.jei.plan_flow.dock_tip_actions");
                public static final LangEntry dockStepTipToggle = ui("calculator.jei.plan_flow.dock_step_tip_toggle");

                private PlanFlow() {}
            }
        }

        public static final class Database {
            public static final LangEntry search = ui("calculator.database.search");
            public static final LangEntry searchDesc = ui("calculator.database.search.desc");
            public static final LangEntry searchPredicateHasDesc = ui("calculator.database.search.predicate.has.desc");
            public static final LangEntry searchPredicateTextDesc = ui(
                "calculator.database.search.predicate.text.desc"
            );
            public static final LangEntry searchPredicateModDesc = ui("calculator.database.search.predicate.mod.desc");
            public static final LangEntry searchPredicateTagDesc = ui("calculator.database.search.predicate.tag.desc");
            public static final LangEntry searchPredicateTooltipDesc = ui(
                "calculator.database.search.predicate.tooltip.desc"
            );
            public static final LangEntry searchPredicateIdDesc = ui("calculator.database.search.predicate.id.desc");
            public static final LangEntry searchPredicateNameDesc = ui(
                "calculator.database.search.predicate.name.desc"
            );
            public static final LangEntry searchPredicateOutDesc = ui("calculator.database.search.predicate.out.desc");
            public static final LangEntry searchPredicateInDesc = ui("calculator.database.search.predicate.in.desc");
            public static final LangEntry searchPredicateSystemDesc = ui(
                "calculator.database.search.predicate.system.desc"
            );
            public static final LangEntry searchPredicateDbDesc = ui("calculator.database.search.predicate.db.desc");
            public static final LangEntry searchPredicateMachineDesc = ui(
                "calculator.database.search.predicate.machine.desc"
            );
            public static final LangEntry searchPredicateTimeDesc = ui(
                "calculator.database.search.predicate.time.desc"
            );
            public static final LangEntry searchPredicateEnergyDesc = ui(
                "calculator.database.search.predicate.energy.desc"
            );
            public static final LangEntry searchPredicateCatDesc = ui("calculator.database.search.predicate.cat.desc");
            public static final LangEntry searchPredicateByproductDesc = ui(
                "calculator.database.search.predicate.byproduct.desc"
            );
            public static final LangEntry searchPredicateChanceDesc = ui(
                "calculator.database.search.predicate.chance.desc"
            );
            public static final LangEntry searchPredicateTierDesc = ui(
                "calculator.database.search.predicate.tier.desc"
            );
            public static final LangEntry searchPredicateLevelDesc = ui(
                "calculator.database.search.predicate.level.desc"
            );
            public static final LangEntry searchPredicateContextDesc = ui(
                "calculator.database.search.predicate.context.desc"
            );
            public static final LangEntry searchPredicatePlanDesc = ui(
                "calculator.database.search.predicate.plan.desc"
            );
            public static final LangEntry searchPredicateProfileDesc = ui(
                "calculator.database.search.predicate.profile.desc"
            );
            public static final LangEntry searchPredicateCompareDesc = ui(
                "calculator.database.search.predicate.compare.desc"
            );
            public static final LangEntry searchPredicateExactDesc = ui(
                "calculator.database.search.predicate.exact.desc"
            );
            public static final LangEntry searchPredicateWildcardDesc = ui(
                "calculator.database.search.predicate.wildcard.desc"
            );
            public static final LangEntry searchPredicateNotDesc = ui("calculator.database.search.predicate.not.desc");
            public static final LangEntry searchPredicateQuoteDesc = ui(
                "calculator.database.search.predicate.quote.desc"
            );
            public static final LangEntry searchPredicateOrDesc = ui("calculator.database.search.predicate.or.desc");
            public static final LangEntry searchPredicateGroupDesc = ui(
                "calculator.database.search.predicate.group.desc"
            );
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
            public static final LangEntry recipeStatusIncludedBySource = ui(
                "calculator.database.recipe.status.included_by_source"
            );
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
            public static final LangEntry actionSetCalculatorDesc = ui(
                "calculator.database.action.set_calculator.desc"
            );
            public static final LangEntry actionSetExecution = ui("calculator.database.action.set_execution");
            public static final LangEntry actionSetExecutionDesc = ui("calculator.database.action.set_execution.desc");
            public static final LangEntry shortcutHeader = ui("calculator.database.shortcut.header");
            public static final LangEntry shortcutDefaultClick = ui("calculator.database.shortcut.default_click");
            public static final LangEntry shortcutSetCalculatorTarget = ui(
                "calculator.database.shortcut.set_calculator_target"
            );
            public static final LangEntry shortcutOpenCalculator = ui("calculator.database.shortcut.open_calculator");
            public static final LangEntry screeningExcluded = ui("calculator.database.screening.excluded");
            public static final LangEntry screeningAdvisory = ui("calculator.database.screening.advisory");
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
            public static final LangEntry debugPluginExtraOutputDesc = ui(
                "calculator.database.debug_plugin.extra_output.desc"
            );
            public static final LangEntry debugPluginChannel = ui("calculator.database.debug_plugin.channel");
            public static final LangEntry debugPluginChannelDesc = ui("calculator.database.debug_plugin.channel.desc");
            public static final LangEntry debugPluginChannelInput = ui(
                "calculator.database.debug_plugin.channel.input"
            );
            public static final LangEntry debugPluginChannelOutput = ui(
                "calculator.database.debug_plugin.channel.output"
            );

            private Database() {}

            public static final class Effect {
                public static final LangEntry input = ui("calculator.database.effect.input");
                public static final LangEntry output = ui("calculator.database.effect.output");
                public static final LangEntry added = ui("calculator.database.effect.added");
                public static final LangEntry removed = ui("calculator.database.effect.removed");
                public static final LangEntry replaced = ui("calculator.database.effect.replaced");
                public static final LangEntry projectionTransformer = ui(
                    "calculator.database.effect.phase.projection_transformer"
                );
                public static final LangEntry contentTransformer = ui(
                    "calculator.database.effect.phase.content_transformer"
                );
                public static final LangEntry resolveEffect = ui("calculator.database.effect.phase.resolve_effect");

                private Effect() {}
            }

            public static final LangEntry pipelineTitle = ui("calculator.database.pipeline.title");
            public static final LangEntry pipelineBase = ui("calculator.database.pipeline.base");
            public static final LangEntry pipelineOutput = ui("calculator.database.pipeline.output");
            public static final LangEntry pipelineDecoratorChain = ui("calculator.database.pipeline.decorator_chain");
            public static final LangEntry pipelinePassThrough = ui("calculator.database.pipeline.pass_through");
            public static final LangEntry pipelineChanges = ui("calculator.database.pipeline.changes");
            public static final LangEntry pipelineBlocked = ui("calculator.database.pipeline.blocked");
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
            public static final LangEntry materialSearchPlaceholder = ui(
                "calculator.rules.material_picker.search.placeholder"
            );
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
            public static final LangEntry databaseAutoRefreshDesc = ui(
                "calculator.settings.database_auto_refresh.desc"
            );
            public static final LangEntry projectionUsesGlobalDatabase = ui(
                "calculator.settings.projection_uses_global_database"
            );
            public static final LangEntry projectionUsesGlobalDatabaseDesc = ui(
                "calculator.settings.projection_uses_global_database.desc"
            );
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
            public static final LangEntry plannerModeAuto = ui("calculator.settings.planner_mode.auto");
            public static final LangEntry plannerModeAssuredBounded = ui(
                "calculator.settings.planner_mode.assured_bounded"
            );
            public static final LangEntry plannerModeAssuredProven = ui(
                "calculator.settings.planner_mode.assured_proven"
            );
            public static final LangEntry plannerModeCostAware = ui("calculator.settings.planner_mode.cost_aware");
            public static final LangEntry useLiveStock = ui("calculator.settings.use_live_stock");
            public static final LangEntry useLiveStockDesc = ui("calculator.settings.use_live_stock.desc");
            public static final LangEntry recipeScreening = ui("calculator.settings.recipe_screening");
            public static final LangEntry recipeScreeningDesc = ui("calculator.settings.recipe_screening.desc");
            public static final LangEntry inventorySensitivity = ui("calculator.settings.inventory_sensitivity");
            public static final LangEntry inventorySensitivityDesc = ui(
                "calculator.settings.inventory_sensitivity.desc"
            );
            public static final LangEntry inventorySensitivityHigh = ui(
                "calculator.settings.inventory_sensitivity.high"
            );
            public static final LangEntry inventorySensitivityBalanced = ui(
                "calculator.settings.inventory_sensitivity.balanced"
            );
            public static final LangEntry inventorySensitivityLow = ui("calculator.settings.inventory_sensitivity.low");
            public static final LangEntry stockPolicy = ui("calculator.settings.stock_policy");
            public static final LangEntry stockPolicyDesc = ui("calculator.settings.stock_policy.desc");
            public static final LangEntry stockPolicyConsume = ui("calculator.settings.stock_policy.consume");
            public static final LangEntry stockPolicyPreserve = ui("calculator.settings.stock_policy.preserve");
            public static final LangEntry executionUseCalculatorPath = ui(
                "calculator.settings.execution_use_calculator_path"
            );
            public static final LangEntry executionUseCalculatorPathDesc = ui(
                "calculator.settings.execution_use_calculator_path.desc"
            );
            public static final LangEntry executionPlannerMode = ui("calculator.settings.execution_planner_mode");
            public static final LangEntry executionPlannerModeDesc = ui(
                "calculator.settings.execution_planner_mode.desc"
            );
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
                public static final LangEntry sortModeAvailable = ui(
                    "execution.overlay.ingredients.sort.mode.available"
                );
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

        public static final class Cockpit {
            public static final LangEntry title = ui("execution.cockpit.title");
            public static final LangEntry minusTitle = ui("execution.cockpit.minus_title");
            public static final LangEntry minusDesc = ui("execution.cockpit.minus_desc");
            public static final LangEntry plusTitle = ui("execution.cockpit.plus_title");
            public static final LangEntry plusDesc = ui("execution.cockpit.plus_desc");
            public static final LangEntry traceOn = ui("execution.cockpit.trace_on");
            public static final LangEntry traceOff = ui("execution.cockpit.trace_off");
            public static final LangEntry traceDesc = ui("execution.cockpit.trace_desc");
            public static final LangEntry hideTitle = ui("execution.cockpit.hide_title");
            public static final LangEntry hideDesc = ui("execution.cockpit.hide_desc");
            public static final LangEntry progressPlanning = ui("execution.cockpit.progress_planning");
            public static final LangEntry progressWaiting = ui("execution.cockpit.progress_waiting");
            public static final LangEntry progressStages = ui("execution.cockpit.progress_stages");
            public static final LangEntry statusPlanning = ui("execution.cockpit.status_planning");
            public static final LangEntry statusIdle = ui("execution.cockpit.status_idle");
            public static final LangEntry statusDone = ui("execution.cockpit.status_done");
            public static final LangEntry statusBreakpoint = ui("execution.cockpit.status_breakpoint");
            public static final LangEntry statusMissing = ui("execution.cockpit.status_missing");
            public static final LangEntry statusPaused = ui("execution.cockpit.status_paused");
            public static final LangEntry statusRunning = ui("execution.cockpit.status_running");
            public static final LangEntry statusReady = ui("execution.cockpit.status_ready");
            public static final LangEntry btnPause = ui("execution.cockpit.btn_pause");
            public static final LangEntry btnResume = ui("execution.cockpit.btn_resume");
            public static final LangEntry btnAuto = ui("execution.cockpit.btn_auto");
            public static final LangEntry btnAutoShort = ui("execution.cockpit.btn_auto_short");
            public static final LangEntry btnStep = ui("execution.cockpit.btn_step");
            public static final LangEntry btnSkip = ui("execution.cockpit.btn_skip");
            public static final LangEntry btnReset = ui("execution.cockpit.btn_reset");
            public static final LangEntry btnSaveCp = ui("execution.cockpit.btn_save_cp");
            public static final LangEntry btnResumeCp = ui("execution.cockpit.btn_resume_cp");
            public static final LangEntry tipPauseTitle = ui("execution.cockpit.tip_pause_title");
            public static final LangEntry tipResumeTitle = ui("execution.cockpit.tip_resume_title");
            public static final LangEntry tipRunTitle = ui("execution.cockpit.tip_run_title");
            public static final LangEntry tipPauseDesc = ui("execution.cockpit.tip_pause_desc");
            public static final LangEntry tipRunDesc = ui("execution.cockpit.tip_run_desc");
            public static final LangEntry tipStepTitle = ui("execution.cockpit.tip_step_title");
            public static final LangEntry tipStepDesc = ui("execution.cockpit.tip_step_desc");
            public static final LangEntry tipSkipTitle = ui("execution.cockpit.tip_skip_title");
            public static final LangEntry tipSkipDesc = ui("execution.cockpit.tip_skip_desc");
            public static final LangEntry tipResetTitle = ui("execution.cockpit.tip_reset_title");
            public static final LangEntry tipResetDesc = ui("execution.cockpit.tip_reset_desc");
            public static final LangEntry tipSpeedTitle = ui("execution.cockpit.tip_speed_title");
            public static final LangEntry tipSpeedDesc = ui("execution.cockpit.tip_speed_desc");
            public static final LangEntry tipSaveCpTitle = ui("execution.cockpit.tip_save_cp_title");
            public static final LangEntry tipSaveCpDesc = ui("execution.cockpit.tip_save_cp_desc");
            public static final LangEntry tipResumeCpTitle = ui("execution.cockpit.tip_resume_cp_title");
            public static final LangEntry tipResumeCpDesc = ui("execution.cockpit.tip_resume_cp_desc");
            public static final LangEntry tipResumeCpStage = ui("execution.cockpit.tip_resume_cp_stage");
            public static final LangEntry matTitle = ui("execution.cockpit.mat_title");
            public static final LangEntry matFilterMissing = ui("execution.cockpit.mat_filter_missing");
            public static final LangEntry matFilterAll = ui("execution.cockpit.mat_filter_all");
            public static final LangEntry matSummaryMissing = ui("execution.cockpit.mat_summary_missing");
            public static final LangEntry matSummaryReady = ui("execution.cockpit.mat_summary_ready");
            public static final LangEntry matEmptyNone = ui("execution.cockpit.mat_empty_none");
            public static final LangEntry matEmptyReady = ui("execution.cockpit.mat_empty_ready");
            public static final LangEntry matBadgeMissing = ui("execution.cockpit.mat_badge_missing");
            public static final LangEntry matBadgeReady = ui("execution.cockpit.mat_badge_ready");
            public static final LangEntry matTipShowAll = ui("execution.cockpit.mat_tip_show_all");
            public static final LangEntry matTipShowMissing = ui("execution.cockpit.mat_tip_show_missing");
            public static final LangEntry matTipFilterDesc = ui("execution.cockpit.mat_tip_filter_desc");
            public static final LangEntry matTipExpand = ui("execution.cockpit.mat_tip_expand");
            public static final LangEntry matTipFold = ui("execution.cockpit.mat_tip_fold");
            public static final LangEntry matTipFoldDesc = ui("execution.cockpit.mat_tip_fold_desc");
            public static final LangEntry matKvRequired = ui("execution.cockpit.mat_kv_required");
            public static final LangEntry matKvOwned = ui("execution.cockpit.mat_kv_owned");
            public static final LangEntry matKvMissing = ui("execution.cockpit.mat_kv_missing");
            public static final LangEntry matKvProgress = ui("execution.cockpit.mat_kv_progress");
            public static final LangEntry graphTitle = ui("execution.cockpit.graph_title");
            public static final LangEntry graphTitleProgress = ui("execution.cockpit.graph_title_progress");
            public static final LangEntry graphHint = ui("execution.cockpit.graph_hint");
            public static final LangEntry graphEmptyPlanning = ui("execution.cockpit.graph_empty_planning");
            public static final LangEntry graphEmptyIdle = ui("execution.cockpit.graph_empty_idle");
            public static final LangEntry graphEmptyPlanningSub = ui("execution.cockpit.graph_empty_planning_sub");
            public static final LangEntry graphEmptyIdleSub = ui("execution.cockpit.graph_empty_idle_sub");
            public static final LangEntry bpRemove = ui("execution.cockpit.bp_remove");
            public static final LangEntry bpAdd = ui("execution.cockpit.bp_add");
            public static final LangEntry bpDesc = ui("execution.cockpit.bp_desc");
            public static final LangEntry bpTag = ui("execution.cockpit.bp_tag");
            public static final LangEntry bpEnabled = ui("execution.cockpit.bp_enabled");
            public static final LangEntry stageKvFormula = ui("execution.cockpit.stage_kv_formula");
            public static final LangEntry stageKvTimes = ui("execution.cockpit.stage_kv_times");
            public static final LangEntry stageKvState = ui("execution.cockpit.stage_kv_state");
            public static final LangEntry stageKvBp = ui("execution.cockpit.stage_kv_bp");
            public static final LangEntry stageFooter = ui("execution.cockpit.stage_footer");
            public static final LangEntry stageStatusDone = ui("execution.cockpit.stage_status_done");
            public static final LangEntry stageStatusActive = ui("execution.cockpit.stage_status_active");
            public static final LangEntry stageStatusPaused = ui("execution.cockpit.stage_status_paused");
            public static final LangEntry stageStatusPending = ui("execution.cockpit.stage_status_pending");
            public static final LangEntry stageManualHint = ui("execution.cockpit.stage_manual_hint");
            public static final LangEntry stagePrepHint = ui("execution.cockpit.stage_prep_hint");
            public static final LangEntry bannerCpFound = ui("execution.cockpit.banner_cp_found");
            public static final LangEntry bannerCpStage = ui("execution.cockpit.banner_cp_stage");
            public static final LangEntry bannerCpResume = ui("execution.cockpit.banner_cp_resume");
            public static final LangEntry bannerCpRestart = ui("execution.cockpit.banner_cp_restart");
            public static final LangEntry bannerBpHit = ui("execution.cockpit.banner_bp_hit");
            public static final LangEntry bannerBpContinue = ui("execution.cockpit.banner_bp_continue");
            public static final LangEntry bannerBpSkip = ui("execution.cockpit.banner_bp_skip");
            public static final LangEntry bannerPausedMissing = ui("execution.cockpit.banner_paused_missing");
            public static final LangEntry bannerPausedGeneric = ui("execution.cockpit.banner_paused_generic");
            public static final LangEntry bannerContinue = ui("execution.cockpit.banner_continue");
            public static final LangEntry bannerSkip = ui("execution.cockpit.banner_skip");
            public static final LangEntry phaseHitBp = ui("execution.cockpit.phase_hit_bp");
            public static final LangEntry phaseManualStep = ui("execution.cockpit.phase_manual_step");
            public static final LangEntry phasePrepareMats = ui("execution.cockpit.phase_prepare_mats");
            public static final LangEntry phaseGathering = ui("execution.cockpit.phase_gathering");
            public static final LangEntry phaseWaitStation = ui("execution.cockpit.phase_wait_station");
            public static final LangEntry phasePlacing = ui("execution.cockpit.phase_placing");
            public static final LangEntry phaseCrafting = ui("execution.cockpit.phase_crafting");
            public static final LangEntry phaseCollecting = ui("execution.cockpit.phase_collecting");
            public static final LangEntry phaseWaitManual = ui("execution.cockpit.phase_wait_manual");
            public static final LangEntry phasePausedMissing = ui("execution.cockpit.phase_paused_missing");
            public static final LangEntry phasePausedFull = ui("execution.cockpit.phase_paused_full");
            public static final LangEntry phasePausedStation = ui("execution.cockpit.phase_paused_station");
            public static final LangEntry phasePaused = ui("execution.cockpit.phase_paused");
            public static final LangEntry queueLabel = ui("execution.cockpit.queue_label");
            public static final LangEntry queueTip = ui("execution.cockpit.queue_tip");
            public static final LangEntry queueTipDesc = ui("execution.cockpit.queue_tip_desc");
            public static final LangEntry queueRemove = ui("execution.cockpit.queue_remove");
            public static final LangEntry queueEmpty = ui("execution.cockpit.queue_empty");
            public static final LangEntry spotlightTitle = ui("execution.cockpit.spotlight_title");
            public static final LangEntry spotlightInspecting = ui("execution.cockpit.spotlight_inspecting");
            public static final LangEntry spotlightFollow = ui("execution.cockpit.spotlight_follow");
            public static final LangEntry spotlightNoGrid = ui("execution.cockpit.spotlight_no_grid");
            public static final LangEntry spotlightTipFold = ui("execution.cockpit.spotlight_tip_fold");
            public static final LangEntry spotlightTipExpand = ui("execution.cockpit.spotlight_tip_expand");
            public static final LangEntry spotlightTipFollow = ui("execution.cockpit.spotlight_tip_follow");
            public static final LangEntry matSearchPlaceholder = ui("execution.cockpit.mat_search_placeholder");
            public static final LangEntry matSearchTip = ui("execution.cockpit.mat_search_tip");
            public static final LangEntry matSearchTipDesc = ui("execution.cockpit.mat_search_tip_desc");
            public static final LangEntry matFilterReady = ui("execution.cockpit.mat_filter_ready");
            public static final LangEntry matSortShortfall = ui("execution.cockpit.mat_sort_shortfall");
            public static final LangEntry matSortProgress = ui("execution.cockpit.mat_sort_progress");
            public static final LangEntry matSortRequired = ui("execution.cockpit.mat_sort_required");
            public static final LangEntry matSortName = ui("execution.cockpit.mat_sort_name");
            public static final LangEntry matSortStage = ui("execution.cockpit.mat_sort_stage");
            public static final LangEntry matSortTip = ui("execution.cockpit.mat_sort_tip");
            public static final LangEntry matSortTipDesc = ui("execution.cockpit.mat_sort_tip_desc");
            public static final LangEntry matOrderTip = ui("execution.cockpit.mat_order_tip");
            public static final LangEntry matNoMatch = ui("execution.cockpit.mat_no_match");
            public static final LangEntry stageReady = ui("execution.cockpit.stage_ready");
            public static final LangEntry stageShort = ui("execution.cockpit.stage_short");
            public static final LangEntry stageDeps = ui("execution.cockpit.stage_deps");
            public static final LangEntry stageStationCrafting = ui("execution.cockpit.stage_station_crafting");
            public static final LangEntry stageStationSmelting = ui("execution.cockpit.stage_station_smelting");
            public static final LangEntry stageStationManual = ui("execution.cockpit.stage_station_manual");
            public static final LangEntry stageStationMachine = ui("execution.cockpit.stage_station_machine");
            public static final LangEntry stageActionInspect = ui("execution.cockpit.stage_action_inspect");
            public static final LangEntry stageActionJump = ui("execution.cockpit.stage_action_jump");
            public static final LangEntry stageActionSkip = ui("execution.cockpit.stage_action_skip");
            public static final LangEntry stageActionRetry = ui("execution.cockpit.stage_action_retry");
            public static final LangEntry drawerDiagnostics = ui("execution.cockpit.drawer_diagnostics");
            public static final LangEntry drawerTelemetry = ui("execution.cockpit.drawer_telemetry");
            public static final LangEntry drawerHealthy = ui("execution.cockpit.drawer_healthy");
            public static final LangEntry drawerErrors = ui("execution.cockpit.drawer_errors");
            public static final LangEntry drawerNoIssues = ui("execution.cockpit.drawer_no_issues");
            public static final LangEntry drawerClear = ui("execution.cockpit.drawer_clear");
            public static final LangEntry drawerReplan = ui("execution.cockpit.drawer_replan");
            public static final LangEntry drawerRate = ui("execution.cockpit.drawer_rate");
            public static final LangEntry drawerEta = ui("execution.cockpit.drawer_eta");
            public static final LangEntry drawerElapsed = ui("execution.cockpit.drawer_elapsed");
            public static final LangEntry drawerDoneCount = ui("execution.cockpit.drawer_done_count");
            public static final LangEntry drawerProgress = ui("execution.cockpit.drawer_progress");
            public static final LangEntry drawerSeverityBreakdown = ui("execution.cockpit.drawer_severity_breakdown");
            public static final LangEntry drawerFilterAll = ui("execution.cockpit.drawer_filter_all");
            public static final LangEntry drawerFilterErr = ui("execution.cockpit.drawer_filter_err");
            public static final LangEntry drawerFilterWarn = ui("execution.cockpit.drawer_filter_warn");
            public static final LangEntry drawerFilterInfo = ui("execution.cockpit.drawer_filter_info");
            public static final LangEntry drawerNoEvents = ui("execution.cockpit.drawer_no_events");
            public static final LangEntry diagMissingTitle = ui("execution.cockpit.diag_missing_title");
            public static final LangEntry diagMissingDetail = ui("execution.cockpit.diag_missing_detail");
            public static final LangEntry diagInvFullTitle = ui("execution.cockpit.diag_inv_full_title");
            public static final LangEntry diagInvFullDetail = ui("execution.cockpit.diag_inv_full_detail");
            public static final LangEntry diagStationTitle = ui("execution.cockpit.diag_station_title");
            public static final LangEntry diagStationDetail = ui("execution.cockpit.diag_station_detail");
            public static final LangEntry diagPlanTitle = ui("execution.cockpit.diag_plan_title");
            public static final LangEntry diagPlanDetail = ui("execution.cockpit.diag_plan_detail");
            public static final LangEntry diagBreakpointTitle = ui("execution.cockpit.diag_breakpoint_title");
            public static final LangEntry diagBreakpointDetail = ui("execution.cockpit.diag_breakpoint_detail");
            public static final LangEntry diagGenericTitle = ui("execution.cockpit.diag_generic_title");
            public static final LangEntry diagWaitingOutputTitle = ui("execution.cockpit.diag_waiting_output_title");
            public static final LangEntry diagWaitingOutputDetail = ui("execution.cockpit.diag_waiting_output_detail");
            public static final LangEntry diagGridBlockedTitle = ui("execution.cockpit.diag_grid_blocked_title");
            public static final LangEntry flowFeeding = ui("execution.cockpit.flow_feeding");
            public static final LangEntry flowReadyCraft = ui("execution.cockpit.flow_ready_craft");
            public static final LangEntry flowCollecting = ui("execution.cockpit.flow_collecting");
            public static final LangEntry flowMissing = ui("execution.cockpit.flow_missing");
            public static final LangEntry flowWaitingOutput = ui("execution.cockpit.flow_waiting_output");
            public static final LangEntry historyTitle = ui("execution.cockpit.history_title");
            public static final LangEntry historyDesc = ui("execution.cockpit.history_desc");
            public static final LangEntry historyHeader = ui("execution.cockpit.history_header");
            public static final LangEntry historyClear = ui("execution.cockpit.history_clear");
            public static final LangEntry historyEmpty = ui("execution.cockpit.history_empty");
            public static final LangEntry historyDone = ui("execution.cockpit.history_done");
            public static final LangEntry historyPaused = ui("execution.cockpit.history_paused");
            public static final LangEntry historyLoad = ui("execution.cockpit.history_load");
            public static final LangEntry historyResume = ui("execution.cockpit.history_resume");
            public static final LangEntry historyStages = ui("execution.cockpit.history_stages");
            public static final LangEntry auditStageDone = ui("execution.cockpit.audit_stage_done");
            public static final LangEntry auditQueueAdded = ui("execution.cockpit.audit_queue_added");
            public static final LangEntry auditCheckpointSaved = ui("execution.cockpit.audit_checkpoint_saved");
            public static final LangEntry auditCheckpointResumed = ui("execution.cockpit.audit_checkpoint_resumed");
            public static final LangEntry auditSkipped = ui("execution.cockpit.audit_skipped");
            public static final LangEntry auditJumped = ui("execution.cockpit.audit_jumped");
            public static final LangEntry auditReset = ui("execution.cockpit.audit_reset");

            private Cockpit() {}
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
            Calc.actionDeepen,
            Calc.actionDeepenDesc,
            Calc.pendingBanner,
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
            Calculator.Database.screeningExcluded,
            Calculator.Database.screeningAdvisory,
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
            Calculator.Database.pipelineTitle,
            Calculator.Database.pipelineBase,
            Calculator.Database.pipelineOutput,
            Calculator.Database.pipelineDecoratorChain,
            Calculator.Database.pipelinePassThrough,
            Calculator.Database.pipelineChanges,
            Calculator.Database.pipelineBlocked,
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
            Calculator.Settings.recipeScreening,
            Calculator.Settings.recipeScreeningDesc,
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
            Execution.Cockpit.title,
            Execution.Jei.Target.tooltip,
            Execution.Jei.Target.prefix,
            Execution.Jei.Target.count,
            Execution.Jei.Target.unsupported,
            Planning.Acquire.base,
            Planning.Conflict.relaxRule,
            Planning.Prompt.MachineFork.title,
            Planning.Prompt.TagChoice.title,
            Planning.Prompt.ExpensiveLeaf.title,
            Planning.Prompt.Suspicious.title,
            Planning.Strategy.balanced,
            Workbench.Mode.planner,
            Workbench.Theme.obsidianBlueprint,
            Workbench.Tab.newTab,
            Workbench.Topbar.searchPlaceholder,
            Workbench.Palette.placeholder,
            Workbench.Toast.worldPersistUnavailable,
            Workbench.Flow.dag,
            Workbench.Ledger.acquire,
            Workbench.Checklist.probeAuto,
            Workbench.Pareto.cardsMode,
            Workbench.Copilot.tabPrompts,
            Workbench.Dnd.dropAddTarget,
            Workbench.Timeline.initial,
            Workbench.Action.replaceRecipe,
            Workbench.Facilities.floorMachines,
            Workbench.Settings.appearance,
            Calculator.Jei.PlanFlow.title
        );
    }
}
