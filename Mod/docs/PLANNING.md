# CalculatorCirrus 规划器架构参考（v2）

本文件描述 `CalculatorCirrus` 重写后的开放世界生产规划模块（`api/planning/**` 与 `planning/**`），面向维护规划核心、扩展桥接层或即将重写 UI 层的开发者。

## 1. 分层总览

```text
  Minecraft / Cirrus 运行时                   离线导出 JSON / Zip
  (RecipeSystem / JEI / Storage)             (exports/<pack>/recipes-*.json)
               │                                           │
               ▼                                           ▼
     planning/bridge/ (7 files)               planning/offline/ (5 files)
  CirrusCatalogAdapter / ContentBridge       ExportJsonCatalogLoader / OfflinePlanBench
               └──────────────┬────────────────────────────┘
                              ▼
                  planning/adapter/ (5 files)
          RawRecordMapper / StackIdentity / CanonicalJson
          ClassifierVerdictBridge / DropReason (D3 确定性过滤)
                              │
                              ▼  RawRecipeCatalogSnapshot (纯值对象)
 ┌─────────────────────────────────────────────────────────────────────────┐
 │              纯核心层（仅依赖 JDK 21，零外部导入，零概率概念）                │
 │  api/planning/ (90 files)          planning/core/ (39 files)            │
 │                                                                         │
 │  L1 图编译: DefaultRecipeGraphCompiler -> ResourceDictionary            │
 │             CandidateTable(FNV-1a64) -> CSR -> IterativeTarjanScc       │
 │             -> CycleMacroMiner -> IndexedRecipeGraph                    │
 │                                                                         │
 │  Stage 0  : ConeExtractor (加权单调层级 + 自然基础资源止步 + 深度递减扇出)    │
 │  Stage 1-2: Potentials -> Netting -> SchedulerAndFinalizer              │
 │             -> ImprovementLoop (含 BatchCoupling / SetCover / A13 产线)  │
 │  Stage 3  : DefaultPlanVerifier (34 ErrorCodes) + DiverseParetoSelector │
 │  L5 协同  : LayeredHumanGuidanceStore + DecisionPointDetector           │
 │             + ConflictMinimalCutDiagnoser + PlanningSessionImpl         │
 └─────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼  ProductionPlan + VerificationCertificate
  ExecutionPlanBridge -> ExecutionTranslator -> InteractionExecutionPlan
```

### 包清单与规模

| 层级 | 路径（`Mod/src/main/java/dev/vfyjxf/cirrus/` 下） | 文件数 | 行数 | 依赖红线 |
| :--- | :--- | ---: | ---: | :--- |
| **L0 公开契约** | `api/planning/{context,cost,graph,human,model,plan,service}/` | 90 | 2,366 | 仅 JDK 21 |
| **L1–L5 纯核心** | `planning/core/{lang,cost,graph,cone,solve,verify,select,human,service}/` | 39 | 12,526 | 仅 JDK 21（`javac --release 21 -Xlint:all -Werror`） |
| **统一适配层** | `planning/adapter/` | 5 | 1,316 | 含 Gson 与 Cirrus 分类器桥接，零概率下传 |
| **运行时桥接** | `planning/bridge/` | 7 | 2,457 | 对接 MC / NeoForge / Cirrus RecipeApi / StorageApi |
| **离线基准** | `planning/offline/` | 5 | 1,981 | 脱离游戏加载导出目录或 Zip，输出规范 JSON 与 `ChainMetrics` |
| **测试套件** | `Mod/src/test/java/dev/vfyjxf/cirrus/planning/` | 11 | 2,802 | JUnit 5（含合规契约、故障注入、PBT、跨 JVM INV-02） |

---

## 2. 端到端数据流

1. **目录捕获与 D3 过滤（Bridge / Offline → Adapter）**
   - 游戏内由 `CirrusCatalogAdapter` 按 `RecipeApiImpl.attachEpoch()` 捕获各 `RecipeSystem`，先按默认策略跳过 `EXCLUDED_DISPLAY` 系统（不枚举其配方），对损坏的 JEI 分类在 8 次连续异常后触发熔断，并消费 `RecipeSystem.routingHints(R)` 合并至 `SystemAdmissionPolicy`。
   - 离线由 `ExportJsonCatalogLoader` 支持目录与 Zip（含 Zip Slip 路径穿越防护）读取。
   - `RawRecordMapper` 与 `CirrusContentBridge` 统一执行 D3 规则：拒绝小数数量（`DropReason.INVALID_AMOUNT`）、拒绝 `0 < chance < 1` 概率槽（`DropReason.PROBABILISTIC`）、将 `chance == 0` 输入折算为不消耗催化剂、丢弃 `chance == 0` 输出，并为每条保留配方分配 1-based `sourceRef` 以支持空 ID 配方 O(1) 反查。
   - `StackIdentity` 对流体/物品标签做命名空间类型消歧，并对无法编码的组件对象提供基于 `ClassValue` 的跨实例/跨 JVM 确定性回退令牌（不使用 `Object.hashCode()`）。

2. **L1 图编译（`DefaultRecipeGraphCompiler` → `IndexedRecipeGraph`）**
   - `ResourceDictionary` 以零正则校验建立稳定整数资源表；`CandidateTable` 驻留多候选输入集合并缓存 FNV-1a 64 位结构哈希。
   - 对 JEI 多输出展示配方（单批 `>= 4` 种输出，如附魔/拆解展示页）自动拆分为单输出 `DISCOURAGED` 配方，杜绝“一输入同时产 68 物品”。
   - 仅当同 `ResourceKey`（含 `canonicalVariantHash` 完全相等）同时出现在输入和输出时，才执行催化剂提升或自增殖折叠。
   - 使用原始数组非递归 `IterativeTarjanScc` 计算二分图 SCC，将压缩/解压等真正包装转换标为 `REVERSIBLE_PACKING`，并由 `CycleMacroMiner` 挖掘 1–4 步再生催化/自增殖闭环为 `CompiledCycleMacro`。

3. **Stage 0 查询锥提取（`ConeExtractor`）**
   - 为目标资源按**加权单调层级**（自然基础原料不向下展开，模组高级机器加权递增）与**深度递减扇出**提取紧凑 `ConeModel`。
   - 支持 A11 高扇出截断放宽阶梯（`{6, 16, 64, Integer.MAX_VALUE}`）与节点预算保护（`truncatedByNodeBudget`）。

4. **Stage 1–2 求解、调度与局部精炼（`planning/core/solve/`）**
   - `Potentials`：自底向上计算五维资源势能；`MachineCapabilityGroups.orderMachines` 优先系统原生模组机器，未展开机器采用保守惩罚（不虚报 `itemPot = 0`）。
   - `Netting`：按需求堆迭代净额结算，支持自动产线（`LinePullStep`）、可逆转换（`ConvertStep`）、环宏（`CycleLoopStep`）、混合批次覆写与递归建机；对自增殖催化剂先外部自举种子再按剩余缺口计算精确批次数，杜绝多做 1 批冗余。
   - `SchedulerAndFinalizer`：拓扑排序并检查机器建机自环（必要时优雅剥离为玩家预备 `ProvisionMachineStep`），连续编号 `s001..sNNN`，按两池 FIFO 精确核算 `PreparationSummary` 与 `surplusByproducts`。
   - `ImprovementLoop`：在软/硬时间预算内执行生产者替换、槽位代表替换、A12 整数耦合批求解（`BatchCouplingSolver` / `BoundedBranchAndBoundBatchSolver`）、共享机器 Set Cover（`BitmaskAndGreedySetCover`）与 A13 自动化产线评估（`SetupLineStep`）。

5. **Stage 3 独立验证与 Pareto 多样化选择（`verify/` + `select/`）**
   - `DefaultPlanVerifier` 独立重放计划，覆盖全部 34 个 `ErrorCodes` 违规码（DAG、质量守恒、严格两池 FIFO 副产物、机器与催化剂、环宏归纳、往返环、硬规则、防溢出、成本一致性）。
   - `DiverseParetoSelector` 在严格保持 INV-04（集合内绝不出现严格支配）的前提下，平衡五维成本、建议等级（`LAST_RESORT` / `DISCOURAGED` 不压制清洁路线）、硬钉选（`HARD_PIN` 优先）与结构多样性。

6. **L5 人机协同、运行时与执行桥接**
   - `LayeredHumanGuidanceStore` 维护会话层与世界层规则，经 `worldSaveListener` 与 `machinePersistenceListener` 实时落盘至 `WorldFacilityRegistry`（NeoForge `SavedData` + DFU Codec）。
   - `DecisionPointDetector` 产出纯 i18n 键的高杠杆决策提示（建机分叉、高扇出标签、昂贵叶原料、环内/可疑配方）；`ConflictMinimalCutDiagnoser` 对规则导致的不可达定位极小冲突规则集（`<= 16` 条时保证极小性，超限诚实返回 `cirrus.conflict.too_many_rules_to_localize`）。
   - `CirrusPlanningRuntime` 负责后台单线程异步编译与原子快照发布（`publishedVersion` 递增、旧快照在重编译期间持续可读、epoch 失配丢弃、`close()` 非阻塞立即返回）。
   - `ExecutionPlanBridge` 将 `ProductionPlan` 转为 `ExecutionOutline`（含 `CraftStep` / `ConvertStep` / `CycleLoopStep` 展开与多候选槽/催化剂具体绑定落地），再由 `ExecutionTranslator` 翻译为 `InteractionExecutionPlan`。

---

## 3. 与 Spec v2.1 的刻意偏差清单

| 偏差项 | 实现做法 | 原因 |
| :--- | :--- | :--- |
| **零概率纯核心（D3）** | `api/planning` 与 `planning/core` 完全不设概率字段；`Adapter` / `Loader` 直接拒绝 `0 < chance < 1` 槽（`DropReason.PROBABILISTIC`），`chance == 0` 输入转催化剂、`chance == 0` 输出丢弃。 | 保证规划器质量守恒与确定性验证绝对严谨，避免期望值近似破坏整数批守恒。 |
| **1:1 跨系统可逆边界修正** | 仅当 `inAmt != outAmt`（缩放包装）或同系统非自然→加工转换时标为 `REVERSIBLE_PACKING`；跨系统 1:1 烧炼/粉碎（如 `cobblestone <-> stone`、`sand <-> glass`）保持 `STANDARD_PRODUCTION`。 | 防止基础原版烧炼被误判为“仅库存可逆包装”，确保 ATM10 `stone_bricks` 直接选出原版 3 步链。 |
| **JEI `>= 4` 多输出拆分** | 单批输出 `>= 4` 种的 JEI 展示配方自动拆分为单输出 `DISCOURAGED` 配方。 | 消除 JEI 聚合展示页导致的“一输入产 68 种附魔书”伪高产漏洞。 |
| **加权单调锥提取 + 深度递减扇出** | `ConeExtractor` 对自然基础资源止步，并按深度递减单节点扇出上限，同时保留 A11 阶梯放宽。 | 将 ATM10 326k 配方查询锥收敛至 61–967 节点、0 截断、5 目标总计 0.5–0.6 s。 |
| **原生机器排序 + 保守未展开机器势能** | `MachineCapabilityGroups.orderMachines` 优先与配方系统同命名空间的机器；未展开的异域机器不再回退 `itemPot = 0`。 | 杜绝异域/不可手搓机器因未展开而被误判“免费”，使 `ae2:controller` 正统手搓并装配 `ae2:inscriber`。 |
| **软规则零泄漏进上报成本** | `StepCosts` 严守真实物理成本，软规则乘子仅作用于搜索势能、标量化与 Pareto 排序。 | 避免软偏好污染 `CostVector` 展示值与验证器成本核对。 |
| **1-based `sourceRef` O(1) 反查** | `RawRecipeRecord` 携带 `sourceRef`，`CompiledRecipeGraph.sourceRefOf(compactId)` 供 `ExecutionPlanBridge.resolveEntry` 反查。 | 准确反查 `explicitRecipeId == Optional.empty()` 的无 ID 配方到原始 `RecipeEntry<?>`。 |
| **自增殖催化剂种子外部自举** | `Netting.applyStandardRecipe` 在确定批次数前先从配方外部自举自增殖催化剂种子并扣减净缺口。 | 避免自增殖配方（如 `1 gem + 1 dust -> 2 gem`）空库存起手时多算 1 批冗余原料。 |

---

## 4. 全屏工作台 UI（已完成）

spec v2.0 的全屏工作台 UI（WP-UI-1～WP-UI-7）已完成，无头自动化验收 Gate UI-A / UI-B / UI-C 全部通过；实测命令、计数、32 条 UI-TC 对照与偏差清单见 [UI-VERIFICATION.md](./UI-VERIFICATION.md)。

- **11 处 UI 桩点已全部清零**：`grep -rE --include=*.java 'TODO\(planning-' Mod/src | wc -l` = 0。原先承载桩点的 4 个文件（`ui/process/CalculatorProcessGraphBuilder`、`ui/widget/calculator/page/CalculatorSettingsPage`、`ui/widget/calculator/page/RulesPage`、`ui/widget/omnicalc/calculator/OmniCalculatorView`）已随旧 UI 一并删除。
- **旧 UI 已删除**：`ui/widget/omnicalc/**`、`TabContainer`、`RulesPage`、`CalculatorSettingsPage`、`ui/process/**`（`ProcessGraph` / `CalculatorBuildResultCache` / `RecipeGraphBuilder`）、`ui/calc/state/**` 全部移除，Gate UI-A 的 8 项符号扫描均 0 匹配。
- **新 UI 落点**：
  - `Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/CalculatorScreen.java`：全屏入口（185 行），挂载 `WorkbenchShellWidget` 与 `WorkbenchIntegrationProvider`；
  - `Mod/src/main/java/dev/vfyjxf/cirrus/ui/workbench/**`：118 文件 / 28,117 行，含外壳与断点、`state` 多标签状态机、`layout` 纯几何布局、`flow` 四视图与空间 DAG、`copilot` 左右停靠岛、`facilities` 设施/8 类规则工作室、`settings` 设置诊断、`chrome` 顶栏/标签/命令面板/Toast；
  - `ui/CirrusPalette`（双主题）与 `ui/CirrusTextures.Workbench`（23 张染色贴图，0 新增 PNG）。
- **旧模块职责去向**：`ProcessGraph` 视图 → `ui/workbench/flow/**` 四视图；`RulesPage` / `CalculatorSettingsPage` → `FacilitiesAndRulesWorkspace` 与 `SettingsAndDiagnosticsWorkspace`；`OmniCalculatorView` 的方案列表与决策提示 → `LeftPlanDockWidget` / `RightCoPilotDockWidget` / `InlineDecisionBeaconWidget`。
- **验收边界**：Gate UI-A～C 为无头可复现项，已实测；Gate UI-D（视觉/帧率）与 Gate UI-E（真机走查）需实机补做，已知缺口见验收报告 §7–§8。
