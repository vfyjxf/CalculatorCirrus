# CalculatorCirrus 全屏工作台 UI（spec v2.0）验收报告

- **验收日期**：2026-11-24（Gate UI-B/C 复跑于 15:41–15:44 CST）
- **分支**：`router-refactor`，HEAD `9f0b870`；工作树含未提交改动（并行任务仍在写入）
- **覆盖范围**：本文只记录**无头可复现**的 Gate UI-A / UI-B / UI-C 实测；Gate UI-D（双主题对比度与渲染帧率）与 Gate UI-E（真客户端 4 分辨率全流程走查）是实机项，本会话未执行，不作为已验收结论。
- **数据来源**：下列命令、计数、测试结果均为本机亲自复跑所得；测试汇总直接解析 `Mod/build/test-results/test/*.xml`。

---

## 1. 环境与版本

| 项 | 实测值 |
| :--- | :--- |
| JDK | `/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home` |
| `java -version` | `Oracle GraalVM 21.0.11+9.1`（Java 21.0.11 LTS） |
| `javac -version` | `javac 21.0.11` |
| 分支 / HEAD | `router-refactor` / `9f0b870`（2026-11-23 21:19） |
| CloudLib | composite `includeBuild("../CloudLib-Standalone")`，`CloudLib-Standalone/gradle.properties` 中 `mod_version=0.1.11` |

### CloudLib 0.1.11 能力缺口

spec 01 §3.3 要求强制复用的一批 CloudLib 工业控件在 0.1.11 中**不存在**。用 SourceScout 对 composite 建索引后逐个检索（`scout search <Name>`），以下 8 个名称均返回 `no class matches`：

`InfoBarWidget`、`CellRowWidget`、`RingWidget`、`SparkWidget`、`CountStyle`、`RichText`、`Themes`、CSS（`dev.vfyjxf.cloudlib.*` 无 CSS 类）。

CloudLib 0.1.11 实际提供的是 `TextWidget` / `LabelWidget` / `PanelWidget` / `BoxWidget` / `ProgressBarWidget` / `ToggleWidget` / `SliderWidget` 等基础件，因此该节只能以**本地等价件**落地：

| spec 要求的 CloudLib 控件 | 本地等价件（`ui/workbench/paint/` 等） |
| :--- | :--- |
| `InfoBarWidget` / `SparkWidget` | `WorkbenchMeterBar`（分段计量条 / 5 维火花条） |
| `CellRowWidget` | `WorkbenchCellRow`（10 格杠杆刻度） |
| `RingWidget` | `WorkbenchRingGauge`（96 段圆环） |
| `CountStyle` | `WorkbenchCountBadge`（槽位数量胶囊） |
| `RichText` | `ChromeText` + 既有 `CirrusText` |
| `Themes` / CSS | `CirrusPalette` + CloudLib `useStyle(...)` |

这些等价件的行为由 `CirrusPaletteContractTest`、`WorkbenchReadoutContractTest`、`WorkbenchPaintContractTest` 断言。

---

## 2. Gate UI-A：旧 UI 清零与静态审计

命令（逐项）：

```bash
grep -rE --include=*.java '<pattern>' Mod/src | wc -l
```

| 扫描符号 | 实测计数 |
| :--- | ---: |
| `TODO\(planning-` | 0 |
| `OmniCalculatorView` | 0 |
| `TabContainer` | 0 |
| `OmniCalcColors` | 0 |
| `CalculatorProcessGraphBuilder` | 0 |
| `CalculatorBuildResultCache` | 0 |
| `RecipeGraphBuilder` | 0 |
| `cirrus\.ui\.calc\.state` | 0 |

| 素材 / i18n | 实测值 |
| :--- | :--- |
| `textures/` 下 PNG 总数 | **160** |
| `git status --porcelain` 中新增 PNG 数 | **0** |
| `en_us.json` 键数 | **1014** |
| `zh_cn.json` 键数 | **1014** |
| 对称差（en−zh / zh−en） | **0 / 0** |
| `cirrus.ui.workbench.` 前缀键数（en / zh） | **304 / 304** |
| 门禁覆盖测试源中的 `@Disabled` | **0** |

> 说明：原本的 11 处 `TODO(planning-*)` 桩点全部位于已删除的 4 个旧 UI 文件，现已随旧 UI 一并清零。`CirrusCalculatorTextureContractTest.textureAssetCountIsUnchanged` 亦在测试内断言 PNG = 160。

---

## 3. Gate UI-B：纯核心隔离 + 4 源集离线强制编译

### ① 纯核心 `javac --release 21 -Xlint:all -Werror`

```bash
cd /Users/vfyjxf/ModdingGround/Project/CalculatorGround/CalculatorCirrus
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home
rm -rf /tmp/cirrus_core_check && mkdir -p /tmp/cirrus_core_check
"$JAVA_HOME/bin/javac" --release 21 -Xlint:all -Werror -d /tmp/cirrus_core_check \
  $(find Mod/src/main/java/dev/vfyjxf/cirrus/api/planning Mod/src/main/java/dev/vfyjxf/cirrus/planning/core -name "*.java")
```

- 编译文件数：**129**（90 个 `api/planning` 契约 + 39 个 `planning/core`）
- 退出码：**0**
- 输出行数：**0**（0 error / 0 warning，仅依赖 JDK 21）

### ② 4 源集离线强制重编译

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home
./gradlew :Mod:cleanCompileJava :Mod:cleanCompileTestJava :Mod:cleanCompileTestmodJava :Mod:cleanCompileJmhJava \
  :Mod:compileJava :Mod:compileTestJava :Mod:compileTestmodJava :Mod:compileJmhJava :Mod:chainExportClasspath \
  --offline --no-build-cache --console=plain
```

- 结果：`BUILD SUCCESSFUL in 10s`
- 任务统计：**19 actionable tasks: 9 executed, 10 up-to-date**（4 个 `cleanCompile*` + 4 个 `compile*` 全部 `EXECUTED`）
- `chainExportClasspath` 写出 `Mod/build/chainexport/cp.txt`
- 仅有 JDK 的 `注: ...已过时的 API / 未经检查的操作` 提示（来自 Minecraft/NeoForge 调用点），不是 error，也不属于纯核心。

---

## 4. Gate UI-C：全量 JUnit 5 无头自动化

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home
./gradlew :Mod:cleanTest :Mod:test --offline --no-build-cache --console=plain \
  --tests 'dev.vfyjxf.cirrus.planning.*' \
  --tests 'dev.vfyjxf.cirrus.ui.workbench.*' \
  --tests 'dev.vfyjxf.cirrus.ui.CirrusLangsContractTest' \
  --tests 'dev.vfyjxf.cirrus.ui.CirrusCalculatorTextureContractTest' \
  --tests 'dev.vfyjxf.cirrus.ui.NineSliceTextureSafetyTest' \
  --tests 'dev.vfyjxf.cirrus.internal.integration.jei.*' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlanContractTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.ExecutionRuntimeContractTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.StorageExecutionContractTest'
```

- 结果：`BUILD SUCCESSFUL in 18s`，23 actionable tasks: 2 executed, 21 up-to-date
- 从 `Mod/build/test-results/test/*.xml` 汇总（70 个 XML）：

| 分组 | 测试类 | 用例 |
| :--- | ---: | ---: |
| `ui.workbench.*` | 51 | 333 |
| `planning.*` | 9 | 46 |
| `internal.integration.jei.*` | 4 | 17 |
| `api.recipe.execution.*` | 3 | 19 |
| `ui` 三个契约测试 | 3 | 14 |
| **合计** | **70** | **429** |

- **fail = 0，error = 0，skip = 0，`@Disabled` = 0**；XML 内 `time` 合计 1.90 s（Gradle 18 s 含配置/编译检查）。

> Gate 脚本缺口：UI-TC-14 的覆盖类 `recipe.execution.ExecutionTargetPlanHandoverTest`（6 用例）与 `ui.widget.calculator.CalculatorTargetDemandQueueTest`（5 用例）**不在上述 `--tests` 过滤集内**。本次已对两者单独复跑，均 PASS；建议后续把这两个过滤项补进 Gate UI-C 命令。

---

## 5. UI-TC-01 ～ UI-TC-32 对照表

“Gate 内”= 该测试类被 §4 的 Gate UI-C 命令过滤命中并已随 429 用例通过。

| 用例 | 覆盖测试类（实际类名 / 关键方法） | Gate 内 |
| :--- | :--- | :---: |
| UI-TC-01 | `ui.CirrusLangsContractTest`：32 核心键 + workbench 家族在两份 JSON 全存在 | ✅ |
| UI-TC-02 | `ui.CirrusCalculatorTextureContractTest`（23 张双主题贴图非空、PNG=160）、`ui.NineSliceTextureSafetyTest`（NineSlice 中心 >0）、`workbench.paint.CirrusPaletteContractTest` | ✅ |
| UI-TC-03 | `WorkbenchBreakpointContractTest`：`freshClassificationMatchesSpecThresholds`、`*Hysteresis`（±4px） | ✅ |
| UI-TC-04 | `workbench.state.WorkbenchPhaseTransitionTest`：7 条阶段迁移路径 | ✅ |
| UI-TC-05 | `workbench.state.WorkbenchAsyncRaceTest`：`GENERATIONS = 20`，逆序完成仅最后一次生效 | ✅ |
| UI-TC-06 | `workbench.state.WorkbenchSessionLeakTest`：切换/热重载/重复 close 后 open==closed | ✅ |
| UI-TC-07 | `workbench.state.SessionTabManagerTest`（单目标/篮子/Fork/8 标签/closeAll） + `chrome.ChromeTabBarTest`（Fork/重命名/关闭其他/篮子角标） | ✅ |
| UI-TC-08 | `workbench.state.PromptSubjectResolverTest`：4 类 `DecisionPromptKind` 主体/选项/锚点 | ✅ |
| UI-TC-09 | `workbench.state.CommandPaletteEngineTest`（5 前缀/中英别名/空输入推荐） + `chrome.ChromeCommandPaletteTest` | ✅ |
| UI-TC-10 | `workbench.layout.PlanDagLayoutDeterminismTest`：`RUNS = 100`，LR/TB 逐像素一致、0 重叠 | ✅ |
| UI-TC-11 | `workbench.layout.PlanDagSwimlaneAndLoopTest`（泳道/循环展开/回环边） + `PlanDagLayoutStressTest` | ✅ |
| UI-TC-12 | `workbench.layout.CriticalPathCalculatorTest`：ES/EF/LS/LF/slack/makespan/关键路径/瓶颈 | ✅ |
| UI-TC-13 | `workbench.layout.PreparationLedgerModelTest`（4 分区/覆盖率/6 排序/过滤） + `flow.LedgerWhatIfTest` | ✅ |
| UI-TC-14 | `recipe.execution.ExecutionTargetPlanHandoverTest`：`Selection(entry,5,plan)`→多步 plan、`pinnedPlan==null` 回退 | ❌（单独复跑 6/6 PASS） |
| UI-TC-15 | `WorkbenchShellBoundsTest` + `WorkbenchShellLayoutTest.everyRegionStaysInsideTheViewportAtAllFourBreakpoints`（1280×720/960×540/640×360/427×240） | ✅ |
| UI-TC-16 | `WorkbenchZenModeTest`（收起范围 + `zenModeRestoresBoundsExactlyOnToggleBack`） + `WorkbenchShellLayoutTest.zenModeReleasesAtLeastNinetyTwoPercentOfTheViewport` | ✅ |
| UI-TC-17 | `WorkbenchDockResizeTest` + `chrome.ChromeDockResizeTest`（[176,320]/[210,380]、画布 ≥220、双击恢复） | ✅ |
| UI-TC-18 | `WorkbenchDatabaseHostingTest`：DATABASE 铺满、会话存活、切回 PLANNER 画布状态原样 | ✅ |
| UI-TC-19 | `chrome.ChromeFocusIsolationTest`：文本焦点屏蔽、5 层 Esc 退栈、快捷键表覆盖 | ✅ |
| UI-TC-20 | `workbench.flow.SpatialDagLodTest`：0.35/0.85/1.50 三级 LOD + LOD-2 内嵌槽命中 | ✅ |
| UI-TC-21 | `workbench.flow.SpatialDagDragTest`：3px 阈值、8px 吸附、Alt 关闭、≤4px 磁吸线、Esc 复原 | ✅ |
| UI-TC-22 | `workbench.flow.SpatialDagMarqueeTest`：框选聚合 5 维、批量禁配方单次 `ApplyRulesFeedback` | ✅ |
| UI-TC-23 | `workbench.flow.SpatialDagMinimapTest`：白框反推相机、上/下游/无关三分区 | ✅ |
| UI-TC-24 | `workbench.flow.GanttBottleneckTest`：`DeclareOwnedMachineFeedback(machineId,2,false)` 单次派发 | ✅ |
| UI-TC-25 | `workbench.flow.LedgerWhatIfTest`：`OverrideStockFeedback` 防抖/合并/不触碰真实背包 | ✅ |
| UI-TC-26 | `workbench.flow.ChecklistProbeBadgeTest`：`Supported(auto)`、`Supported(manual)`、`Unsupported` 三态徽章 | ✅ |
| UI-TC-27 | `workbench.copilot.PromptDispatchTest`（4 类提示、`already_owned`、`persistToWorld` JSON 往返） + `PromptSubjectResolverTest` | ✅ |
| UI-TC-28 | `workbench.copilot.DragPayloadDispatchTest`：4 种 `DragPayload`、4 投放区、6 类 `HumanFeedback`、取消 0 副作用 | ✅ |
| UI-TC-29 | `workbench.copilot.ConflictAndTimelineTest`：冲突保底幽灵、`relaxConflictRules`、时间轴 undo | ✅ |
| UI-TC-30 | `workbench.facilities.FacilitiesCrudTest`：自有机器/产线/自动化配方/OPT_IN 四模块 CRUD | ✅ |
| UI-TC-31 | `workbench.facilities.GuidanceRuleBuilderTest`：8 类规则构建 + codec 双作用域往返 | ✅ |
| UI-TC-32 | `WorkbenchShellLifecycleTest`（外壳 10 次挂载/切换/卸载） + `WorkspaceLifecycleTest`（设施/设置各 10 次 + 无悬挂定时器） | ✅ |

支撑用例（不单独对应某条 TC，但同属 Gate UI-C）：`WorkbenchSurfacesTest`、`WorkbenchFormattersContractTest`、`WorkbenchConfigDefaultsTest`、`WorkbenchPaintContractTest`、`WorkbenchReadoutContractTest`、`hosts.*HostAdapterTest`、`IntegrationProviderTest`、`ChromePopoverTest`、`ChromeToastTest`、`ChromeTopDockTest`、`SpatialDagCanvasSmokeTest`、`SpatialDagPreviewTest`。

---

## 6. 规模与结构

`ui/workbench/**` 主源：**118 文件 / 28,117 行**；`src/test/java/.../ui/workbench/**`：**58 文件 / 12,549 行**（其中 7 个是测试夹具）。全屏入口 `ui/widget/calculator/CalculatorScreen.java`：185 行。

| 位置 | 文件数 | 行数 | 职责 |
| :--- | ---: | ---: | :--- |
| `ui/workbench/`（根） | 11 | 1,878 | 外壳 `WorkbenchShellWidget`、断点/布局几何、模式与上下文、四个工作区装配（`WorkbenchIntegrationProvider` / `DefaultWorkspaceProvider`）、`WorkbenchSurfaces` |
| `state/` | 24 | 3,055 | 多标签控制器、异步状态机与快照、命令面板引擎、提示主体解析、历史/拖拽载荷 |
| `layout/` | 9 | 2,063 | 纯几何 DAG 布局（LR/TB/泳道、循环展开、Diff 幽灵）、关键路径 CPM、备料总账模型 |
| `paint/` | 7 | 662 | 本地矢量等价件（计量条/刻度/圆环/数量牌）与主题绘制工具 |
| `flow/` | 17 | 6,612 | 四视图宿主 + 空间 DAG 画布（LOD、拖拽、框选、小地图）、甘特、总账、执行清单 |
| `copilot/` | 14 | 4,881 | 左右停靠岛、Pareto 卡/散点图、提示卡/内联信标、冲突覆盖、右键菜单、DND 协议 |
| `facilities/` | 8 | 2,427 | 设施与 8 类规则工作室（机器地板/产线/自动化配方/OPT_IN/规则构建器） |
| `settings/` | 3 | 761 | 设置与实时诊断工作区、进度预设 |
| `chrome/` | 19 | 4,847 | 标签栏/顶栏/左轨/底栏、命令面板、Toast、弹层、快捷键表、主题文本 |
| `hosts/` | 6 | 931 | 各工作区的窄宿主适配器（外壳 ↔ 面板接缝） |

> 没有 `integration/` 子包：集成装配类位于根包（`WorkbenchIntegrationProvider`、`DefaultWorkspaceProvider`、`WorkbenchSurfaces`）。

---

## 7. 与 UI spec v2.0 的偏差清单（逐条回代码核实）

| # | lead 给出的偏差 | 代码核实 | 结论 |
| ---: | :--- | :--- | :--- |
| 1 | 本地等价件（CloudLib 无 `InfoBarWidget/CellRowWidget/RingWidget/SparkWidget/CountStyle/RichText/Themes/CSS`） | SourceScout 对 CloudLib 0.1.11 逐个检索均 0 命中；本地实现见 §1 | **属实** |
| 2 | Atelier StepTint 自定色 | `CirrusPalette.java:61-67` 为 Atelier 单独定义 `tintBg/borderIdle/borderSelected`；spec 01 §2.3 只给 Obsidian 的 `tintBg` 标了主题，Atelier 步进色无冻结表（契约测试也只校验 Obsidian 步进色） | **属实但不违背已冻结值**；spec 需补 Atelier 表 |
| 3 | CycleLoop 折叠 172×64 | `layout/LayoutGeometry.java:28-29` = 172×64；spec 03 §1.1 写 168×62，而 §1.3 写 172×64 | **spec 自身矛盾**；实现与 §1.3 一致 |
| 4 | LedgerSortKey 5 键 + ascending | `layout/PreparationLedgerModel.java:36-42` 5 键 + `build(..., boolean ascending, ...)`；spec 06 §1.4 定义即 5 键，03 §3 的“6 维排序”= 5 键 + 升降序按钮 | **不成立**（与 spec 06 一致） |
| 5 | Diff 幽灵 50% 覆盖填充 | `flow/SpatialDagCanvasWidget.java:985` `withAlpha(palette.diffRemoved(), 0.5f)`；spec 只写“半透明”，未给具体 alpha | **属实，属实现补齐** |
| 6 | LOD-2 端口级连线对齐未做 | `layout/PlanDagLayout.java:784-803` 连线锚点为节点 `centerY()` / `maxY()`，未按输入/输出槽位高度对齐 | **属实** |
| 7 | 惯性/缓动动画未做 | `ui/workbench` 内无 `TweenAnimation`/`Easing` 调用，画布平移无 140ms 惯性；`reducedMotion` 只影响脉冲/Toast | **属实** |
| 8 | 快捷键子集 | `chrome/ChromeShortcut.java:90-116` 定义了 spec 全表，但生产输入只有 `CalculatorScreen:102-127`（K/Tab/Esc）与 `SpatialDagCanvasWidget:766-815`（Esc/F/Home/L/R/1-4）；`ChromeKeyRouter` 未接输入层，`ChromeSink` 生产环境未安装 | **属实且更窄**（见“其它偏差”①） |
| 9 | Diff 不触发对比 | 对比链路存在：拖入底部对比槽（`LeftPlanDockWidget:271-288`）或散点图点互拖（`ParetoScatterPlotWidget:99`）→ `comparePlanId` → 画布 REMOVED 幽灵 + 左栏 5 维差值 chips；缺的是“方案卡拖到另一张方案卡”与 ADDED 高亮 | **整句不成立**；实际是触发面窄（见“其它偏差”③） |
| 10 | beacon 点击 = 聚焦右栏 prompt | `WorkbenchSurfaces.java:216` `dagHost.beaconSink((promptId, stepId) -> rightDock.focusPrompt(promptId))`；spec 05 §1.1 要求节点旁弹出 `PromptCardWidget` | **属实** |
| 11 | 2 条 `MainPanelAsyncSessionBuildTest` 红灯属基线既有债务 | 见下方基线证据 | **属实** |

### 基线证据（MainPanel 2 条红灯）

- 现状复跑：`./gradlew :Mod:test --tests 'dev.vfyjxf.cirrus.ui.execution.MainPanelAsyncSessionBuildTest'` → `7 tests completed, 2 failed`：`activeRecipeContextBuildRunsAfterSessionBuildCompletes`（:199）、`clearingSelectionAfterCompletionResetsSession`（:167）。
- **重写前基线同样失败**：`/private/tmp/cirrus_test60.log`（2026-11-24 13:11）记录的同名 2 条失败、同样断言行号；该次运行的 `--tests` 列表仍包含 **已被本次重写删除** 的旧 UI 类（`ui.widget.omnicalc.calculator.OmniCalculatorViewContractTest`、`ui.widget.TabContainerCalculatorContractTest`），可确认它跑在旧 UI 树上。`/private/tmp/reg.log`（14:19）复现同样 2 条。
- 该测试类与其被测类 `ui/execution/MainPanel` 均不在 `git status` 改动列表中；直接依赖里只有 `ExecutionTargetRecipeState`（新增可空第 4 分量，`set(recipe,times)` 行为不变）、`CalculatorSyntheticScenario`（仅新增方法、删除一条过期 TODO 注释）与 `ExecutionOverlayClientConfig`（+165 行纯新增 workbench 配置项）三处**追加式**改动，均不改变被测路径行为。

### 本次额外发现的偏差

1. **Chrome 接缝未在生产装配**：全仓无 `ChromeSink.install(...)` 调用（`ChromeSink.EMPTY` 是默认值），`ChromeKeyRouter` 只在 `TopCommandDockWidget` 内部做按钮动作。结果：命令面板的 `focusPlanStep` / `exportJson` / `focusCopilotPrompt` 与画布快捷键 M/P/Delete 在实机中为空动作；全局 Ctrl+Z / Ctrl+T/W / Ctrl+Enter / Alt+1..4 无输入分发（仅测试直接构造 router 驱动）。
2. **停靠岛折叠形态缺失**：`WorkbenchBreakpoint` 只按断点隐藏（左栏仅 WIDE、右栏 COMPACT 隐藏），没有 spec 02 §3 的“左栏一键折叠 24px 胶囊 / STANDARD 折叠胶囊条 / COMPACT 右栏抽屉”，仅 Zen 统一收起。
3. **Diff 视觉不完整**：`PlanDagLayout` 会给节点标 `ADDED/REMOVED`，但画布只对 `REMOVED` 做 50% 幽灵（:983-985），`ADDED` 无翠绿描边；方案卡之间互拖无 drop consumer（仅底部对比槽与散点图点互拖）。

---

## 8. 已知缺口与后续项

1. **DATABASE 页不随主题换肤**：`DatabasePage` 仍引用 `CirrusPalette.Legacy.*`（Atelier 静态色），不读取当前主题。
2. **三个工作区在 `DefaultWorkspaceProvider` 下是占位**：只有 `databaseWorkspace` 返回真实 `DatabasePage`；生产装配走 `WorkbenchIntegrationProvider`（`CalculatorScreen` 已使用），`DefaultWorkspaceProvider` 在主源中无引用、仅测试使用。
3. **`rebuildSuffixSession` 不保留 `pinnedPlan`**：`ExecutionTargetRecipeState.java:430-434` 重建 `Selection` 时使用 3 参构造，`pinnedPlan = null`，增量重建会退回单配方翻译。
4. **无拼音首字母匹配**：`CommandPaletteEngine` 的 `>` 前缀只做中英文别名/大小写归一，无拼音首字母索引（spec 02 §5.1 要求）。
5. **无 10 万级资源耗时基准**：`PlanDagLayoutStressTest` 仅 80 步；UI 层没有 10 万资源规模的布局/绘制耗时基准。
6. **Gate UI-C 过滤集不含 UI-TC-14 的覆盖类**：`ExecutionTargetPlanHandoverTest`、`CalculatorTargetDemandQueueTest` 需单独跑（见 §4 说明）。
7. **代码风格/质量工具未接入**：spotless 等后续单独落地，届时一次性 apply。
8. **Gate UI-D / UI-E 未执行**：双主题对比度与帧率自检、真客户端 4 分辨率与全交互走查需实机补做。

---

## 9. 复现命令清单

```bash
cd /Users/vfyjxf/ModdingGround/Project/CalculatorGround/CalculatorCirrus
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home

# Gate UI-A：旧 UI / TODO 清零
for p in 'TODO\(planning-' 'OmniCalculatorView' 'TabContainer' 'OmniCalcColors' \
         'CalculatorProcessGraphBuilder' 'CalculatorBuildResultCache' 'RecipeGraphBuilder' \
         'cirrus\.ui\.calc\.state'; do
  grep -rE --include=*.java "$p" Mod/src | wc -l
done
find Mod/src/main/resources/assets/calculator_cirrus/textures -iname '*.png' | wc -l   # 160
git status --porcelain | grep -E '^\?\?' | grep -ic '\.png$'                            # 0

# Gate UI-B①：纯核心隔离
rm -rf /tmp/cirrus_core_check && mkdir -p /tmp/cirrus_core_check
"$JAVA_HOME/bin/javac" --release 21 -Xlint:all -Werror -d /tmp/cirrus_core_check \
  $(find Mod/src/main/java/dev/vfyjxf/cirrus/api/planning Mod/src/main/java/dev/vfyjxf/cirrus/planning/core -name "*.java")

# Gate UI-B②：4 源集离线强制编译
./gradlew :Mod:cleanCompileJava :Mod:cleanCompileTestJava :Mod:cleanCompileTestmodJava :Mod:cleanCompileJmhJava \
  :Mod:compileJava :Mod:compileTestJava :Mod:compileTestmodJava :Mod:compileJmhJava :Mod:chainExportClasspath \
  --offline --no-build-cache --console=plain

# Gate UI-C：全量无头验收
./gradlew :Mod:cleanTest :Mod:test --offline --no-build-cache --console=plain \
  --tests 'dev.vfyjxf.cirrus.planning.*' \
  --tests 'dev.vfyjxf.cirrus.ui.workbench.*' \
  --tests 'dev.vfyjxf.cirrus.ui.CirrusLangsContractTest' \
  --tests 'dev.vfyjxf.cirrus.ui.CirrusCalculatorTextureContractTest' \
  --tests 'dev.vfyjxf.cirrus.ui.NineSliceTextureSafetyTest' \
  --tests 'dev.vfyjxf.cirrus.internal.integration.jei.*' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlanContractTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.ExecutionRuntimeContractTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.StorageExecutionContractTest'

# 结果汇总（XML）
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
tot=fail=err=skip=0
for f in glob.glob('Mod/build/test-results/test/*.xml'):
    r=ET.parse(f).getroot()
    tot+=int(r.get('tests')); fail+=int(r.get('failures'))
    err+=int(r.get('errors')); skip+=int(r.get('skipped'))
print(len(glob.glob('Mod/build/test-results/test/*.xml')), tot, fail, err, skip)
PY

# 基线：MainPanel 2 条红灯
./gradlew :Mod:test --offline --no-build-cache --console=plain \
  --tests 'dev.vfyjxf.cirrus.ui.execution.MainPanelAsyncSessionBuildTest'

# Gate UI-C 未覆盖的两组（UI-TC-14 等）
./gradlew :Mod:test --offline --no-build-cache --console=plain \
  --tests 'dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetPlanHandoverTest' \
  --tests 'dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetDemandQueueTest'
```

---

## 附：与 lead 提供口径的差异

1. **数字无差异**：`TODO(planning-`) = 0、PNG = 160、新增 PNG = 0、CloudLib = 0.1.11、MainPanel 2 条红灯，均复现一致。
2. **“LedgerSortKey 5 键 + ascending”不是偏差**：与 spec 06 §1.4 完全一致，03 §3 的“6 维排序”= 5 键 + 升降序。
3. **“Diff 不触发对比”整句不成立**：触发链路（对比槽、散点图互拖、5 维差值 chips、REMOVED 幽灵）均在；实际缺口是方案卡互拖与 ADDED 高亮。
4. **“快捷键子集”成立但更严重**：不仅少键，而且 `ChromeSink` 未装配、`ChromeKeyRouter` 未接输入，生产环境只有 K/Tab/Esc + 画布本地 F/Home/L/R/1-4 生效。
5. **“CycleLoop 172×64”是 spec 自身矛盾**（03 §1.1 168×62 vs §1.3 172×64），实现取 §1.3。
