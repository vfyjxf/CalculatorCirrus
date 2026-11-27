# Java 架构规格 — 智能规划器落地（Phase 1–5 移植用）

日期：2026-09-20。本文把 Python 原型（`experiments/quality/`，契约见 `DESIGN.md`，数字见 `REPORT.md`）翻译成 Java 侧的包结构、类职责、建图期流水线、磁盘缓存、接线点与测试计划。算法细节不重复，一律指向 `DESIGN.md` 的对应节；接线点的文件:行来自对当前代码的核实（`HANDOVER.md` §6–§7）。路径省略前缀 `Mod/src/main/java/dev/vfyjxf/cirrus/`。

原则（计划 §九，原型已逐条遵守，Java 必须同样遵守）：

1. 运行时不含任何 mod / 物品 / 系统 id 词表。唯一允许的"机械筛选"是纯形状（零输入、输入输出完全相同、空槽）。跨包先验类表是学习产物，新包可不依赖它。
2. 运行时零训练：模型离线训练一次，反馈只做闭式更新。
3. 数据不完全可信：可靠性权重进入价值定点与模型；"世界资源 vs 数据缺口"显式区分。
4. 旧 `StandardPlanner` / `AcyclicPlanner` 只作回退。

---

## 0. 总体数据流

```
建图期（每个 catalog 一次，落盘缓存）
  RecipeGraph
    → TokenClassTable      加载 token-classes.json 先验 + 对新 token 增量类比归纳
    → ContentIdentity      每个 base id 的 (内容类型, 材料类, 形态类, 修饰类, 变体类)
    → RecipeTemplateIndex  每个 Node 的模板签名 → template_id（缓存到 Node）
    → EconomyIndex         图（OR 节点）→ 形状角色 → 转换池 → tier → 价值定点 → 叶子可得性 → 数据缺口 → 可靠性
    → TemplateFeatures     每模板 129 维（feature-spec.json）
    → TemplateRoleModel    junk_p、角色后验（每模板一次）
    → ProducerRanker       每配方 S(r) = w·f(r)（LR 臂，Bradley–Terry 分解，见 §3.4）
    → QualityRuntime       排除位图（唯一生产者保护）+ 代价先验 + 扫描排序
请求期
  HierarchicalPlanner（材料层代价求解 → 形态层生产者选择 → 抽取）→ RoutingPlan
  失败 / 超预算 → StandardPlanner
反馈期
  PlannerFeedback.have/ban/prefer/choose → FeedbackLearner（模板级贝叶斯 + 成对在线权重）→ 持久化
```

原型对应：`ontology.py` → 本体三件；`economy.py` → EconomyIndex；`features.py` + `predict_reference.py` → TemplateFeatures / 两个模型；`planner.py` → HierarchicalPlanner；`feedback.py` → 反馈（§6）。

---

## 1. 包放置

| 包 | 类 | 侧 | 原型 |
| --- | --- | --- | --- |
| `api/recipe/routing/ontology/` | `TokenClassTable`, `TokenClassInducer`, `ContentIdentity`(record), `ItemTriple`(record), `RecipeTemplateIndex`, `TemplateSignature` | api（规则、规划器、UI 都读） | `ontology.py` |
| `recipe/routing/economy/` | `EconomyIndex`(门面, `of(graph)` 缓存), `ShapeRoles`, `ConversionClasses`, `MachineTierSolver`, `MaterialValueSolver`, `LeafAcquirability`, `DataReliability` | impl（`cost/` 旁） | `economy.py` |
| `recipe/routing/quality/` | `TemplateFeatures`, `FeatureSpec`, `LinearModel`, `TreeModel`(可选), `TemplateRoleModel`, `ProducerRanker`, `QualityRuntime` | impl | `features.py`, `predict_reference.py` |
| `recipe/routing/planner/` | `HierarchicalPlanner`(实现 `RoutePlanner`) + `PackIndex`, `CandidatePolicy`, `CostSolver`, `PlanExtractor`, `PlanGuards`, `PlanExplanation` | impl | `planner.py` |
| `api/recipe/routing/feedback/` | `PlannerFeedback`, `FeedbackLearner`, `TemplateInquiry`, `FeedbackStore` | api | `feedback.py` |
| `recipe/routing/cache/` | `DerivedIndexCache`（目录哈希 → 文件） | impl | 新模式（§4） |
| `assets/calculator_cirrus/quality/` | `token-classes.json`, `feature-spec.json`, `template-role.json`, `producer-ranker.json`, `leaf-acquirability.json`, `manifest.json` | 资源 | `out/models/` 原样复制 |

api 依赖方向不变：impl → api，不反向。`GraphTopology`（impl）新增生产者/消费者计数与回路对的对外访问器。

---

## 2. 建图期流水线（顺序即依赖）

| 步 | 输入 | 输出 | 缓存到 | 原型耗时（atm10 283k 条） | Java 预算 |
| --- | --- | --- | --- | --- | --- |
| 1 分词 + 类表 | 全部 base id（item/fluid） | `TokenClassTable`（先验 + 增量） | Node 外，按 catalog | 本体总 ~5 s/22 包 | ≤ 1 s |
| 2 身份四元组 | 类表 | `ContentIdentity` per base id | `RecipeGraph` 侧表 | | |
| 3 模板签名 | 四元组 + Node 槽 | `template_id` per Node | `Node` 新字段 | | ≤ 1 s |
| 4 经济层 | 图 + 四元组 | `EconomyIndex` | 文件 | 7.7 s（atm10） | ≤ 2 s |
| 5 特征 | 模板 + 经济 + 系统统计 | float[129] per 模板 | 文件 | 19.7 s/22 包 | ≤ 1 s |
| 6 模型推理 | 特征 | junk_p、角色后验 per 模板；S(r) per 配方 | 文件 | 35 s 冷（含 5, atm10） | ≤ 1 s |
| 7 QualityRuntime | 后验 + S(r) | 排除位图、先验、排序 | 内存 | | ms |

计划目标：atm10 建图期额外 ≤ 3–5 s；二次加载走磁盘缓存毫秒级。Python 原型冷建 atm10 35.3 s 主要是 pandas 开销，Java 原生数组应远低于预算。`l5_sys_*` 特征块是按当前包的系统聚合，**必须在建图期算，不能烘焙进导出**（`REPORT.md` Phase 0c-2 issue 5）。

---

## 3. 必须逐位复现的契约

Java 与 Python 不一致的地方模型就是噪声。以下每一条都有 Python 侧的机器可读依据，且 `golden-sample.json`（1,000 模板 / 500 对 / 300 叶子，含原始特征向量与期望输出）作为平价夹具，**先跑通平价测试再做任何规划器集成**。

### 3.1 分词与类归纳（`DESIGN.md` Phase 0a §2.1）

- `ns:path` 切分；path 按 `/ _ . -` 切、小写；命名空间不进 token 序列。
- 证据：同长度、恰一个位置不同的两个 id = 一次互换证据（桶哈希，桶上限 400 组合）。
- bigram 单元的非组合性判据；上下文余弦 ≥ 0.7；`min-evidence 2`；Louvain（resolution 1.0）。Java 运行时可以只做"先验加载 + 新 token 归入最近类/新类"的增量版，全量 Louvain 留给离线；但增量版对 fom 的模板折叠结果必须与 Python 一致（测试用 fixture 包比对 `template_id`）。
- 角色猜测按位置：末 token → form，倒数第二 → material，其余 → modifier。

### 3.2 模板签名（`DESIGN.md` Phase 0a §2.4）

`template_id = "tpl:" + sha1(canon_json(signature))[:12]`，其中 `canon_json` = 键排序、无空白分隔符 `,`/`:`、UTF-8 不转义（Python `json.dumps(sort_keys=True, separators=(",",":"), ensure_ascii=False)`）。签名字段：`sys`、`mach`（排序）、`in`/`out`（抽象内容字符串的排序多重集，格式 `kind|content_type|material|form|modifiers|variant_class|amount|chance_bucket`）、`rid`（残留 token 列表）、`pat`（排序）。全部字段是字符串或整数，没有浮点，因此 Java 用相同的键序与分隔符即可得到相同字节。`any`/`typedAny` 的 `base_id` 取成员字典序最小。

### 3.3 特征（`out/models/feature-spec.json`，`DESIGN.md` Phase 0c-1 §2）

- 129 列名、层、聚合方式（模板内成员 mean/max/share）、NaN 填充值、标准化 mean/std 全在 `feature-spec.json`。三条易错规则：（a）L6 = 残留 id path token + 残留系统 path token，**先去掉该包用到的全部命名空间**，再 `sha1(token) mod 32` 分桶；（b）价值列按包中心化 `log v − median(log v)`；（c）缺失的 one-hot 是 0 不是 NaN，只有 11 列允许 NaN 并按 spec 填充。
- 兄弟离群 z-score：同输出生产者在 n_inputs / value_in / tier / n_output_slots 上的 z 分数，聚合到模板。

### 3.4 推理（`predict_reference.py`，`DESIGN.md` Phase 0c-2 §4）

- LR：`z = (x − mean) / std`，`logit = w·z + b`，sigmoid / softmax。容差 1e-6（Python 侧最大误差 6.1e-15）。
- LightGBM（可选）：树遍历，缺失值走 `default_left`，叶值求和后 sigmoid/softmax。原型建议 Java 全用 LR：junk/role 头 GBM 只领先 0.021/0.025，LR 在配方级 AUC 与零样本上更好，且一条代码路径（`REPORT.md` Phase 0c-2 "Export"）。
- 排序器（LR 臂）：成对特征 `f(a) − f(b)` + 8 个对标志，无截距；由于线性，`margin(a,b) = S(a) − S(b)`，`S(r) = w_base · f(r)`（对标志的贡献在 Bradley–Terry 行均值里精确处理，`planner.py` 已验证与 `predict_reference.margin` 最大差 1.8e-14）。因此每配方一次 180 维点积，不需要 O(n²) 成对。`d_leaf_acq_min` 为 NaN 时置 0（权重 −1.773，fom 70% 为 NaN）。
- 叶子分：`seed = K^(1 − σ(w·z))`，10 个权重 + 每包标准化在 `leaf-acquirability.json`。

### 3.5 经济层（`DESIGN.md` Phase 0b）

Java 复现 `economy.py` 各阶段即可；四个不能省的修正：叶子种子限幅 `[1, K]`（K=2）、每步成本 `0.25 × median(seed) × (1 + tier)`、封闭环底价取环内成员、可靠性用相对权重。求解器暴露 `recipeWeight[]` / `exclude` 位图参数（角色后验回灌用）。

---

## 4. 磁盘缓存（新模式）

- 目录：`FMLPaths.CONFIGDIR.get().resolve("cirrus").resolve("cache").resolve(<catalogKey>)`（`RecipePathSettings.java:72` 已用 `CONFIGDIR/cirrus`）。
- `catalogKey` = sha-256 over（排序的 `stableSystemId`+`stableRecipeId`+`stableRecipeHash` 序列，系统数、配方数，mod 列表）+ `manifest.json` 的 `data_hash`。任一变化即失效。`RecipeDatabaseCatalogCache.CatalogKey` 的指纹材料可复用，但它本身是内存 TTL，不是磁盘缓存。
- 文件（Gson，gzip）：`token-classes.json`、`templates.bin`（Node 序号 → template_id）、`economy.bin`（值/tier/池/可靠性数组，按 Node/资源序号）、`posteriors.bin`（junk_p、角色、S(r)）。写入原子（临时文件 + move）。
- 加载失败 → 静默重算并覆盖；版本字段不匹配 → 重算。

---

## 5. Phase 2 接线（三处 + 清理）

1. **排除位图**：`QualityRuntime` 产出一条参与 `SourcePolicyDecision` 的规则：`excluded(r) = (junk_p(r) > τ_junk || P_display(r) > τ_display) && admissibleProducers(output(r)) > 1`。唯一生产者永不排除（原型：受保护的唯一生产者会被记录并解释）。τ 取 `metrics-junk.json` 的 P ≥ 0.95 工作点（当前 τ_junk = 0.8707，τ_display = 0.5）。**保留 builtin 规则作为第二道网**（`REPORT.md` Phase 0c-2 issue 1：junk 头配方级只抓 85%，A/B 里 `junk_in_path(wl)` 0.015 → 0.022）。
2. **代价先验**：新增 `FeatureType.qualityPrior`，`DefaultScoreModels.build` 在 cost tier 声明分量，值 = `−log σ(S(r) − S̄(siblings))` 或直接 `−S(r)`；`StandardPlanner` :1735（及 :1414/:1434/:1623/:1659）`features.set`。
3. **扫描排序**：`StandardPlanner.scanFilteredProducers`（:1213–1272）在 `producerScanLimit`（:1307）截断前按 S(r) 降序。
4. **挂载**：`RecipePathSettings.java:531` 把 `BayesRuntime.attach(service, graph)` 换成 `QualityRuntime.attach(service, graph, economyIndex)`；签名与返回值仿 `BayesRuntime`（静态 `attach`，返回 `@Nullable RecipeCostHeuristic`，注册命名空间化的 `RuleProfile`）。
5. **清理**：main 删除 `builtin_rules.json` 的字符串规则、`RecolorStructuralRule`/`RecyclingStructuralRule`/`ColonyViewMirrorRule` 的词表、`bayes/bayes-model.json`、`BayesRuntime`、`CascadeScoreRuntime`（后者本来就没接 main）；迁到 `src/test` 作标签生成器（`BayesExperimentMain features` 依赖它们产 `rulehits`）。保留 `RuleEngine` 与纯形状结构规则（Exchange/Generation/MassSink）供用户自定义与第二道网。`RecipeClassifierTest.jeiBrewingMirrorIsDuplicate` 随规则层一起退役。

---

## 6. HierarchicalPlanner（`DESIGN.md` §Phase 3，含 Java 映射表）

原型 `planner.py` 的结构就是 Java 类的结构：

| 原型 | Java | 说明 |
| --- | --- | --- |
| `PackIndex` | `PackIndex`（建图期，随 `EconomyIndex` 缓存） | 按 `base_id` 的生产者索引、OR 节点代表成员（池内最廉，平局取消费者最多）、每配方后验/角色/tier/机器/可靠性/模板、每输出 S(r) |
| `Policy` | `CandidatePolicy` | τ、唯一生产者保护、conversion 降级（只在库存/副产有输入形态时可用）、recycling ×8 末位、generation 可靠性加权 |
| `solve_cost` | `CostSolver` | 请求作用域的 Knuth/Dijkstra 超图定点：库存 0 价、叶子种子、每步 `step_cost×(1+tier)`、机器足迹（首次使用付 tier 代价，求解期贪心、抽取期精确）、tier 逆行罚、可靠性；记忆键 = (item, 剩余库存签名)；**settle-order 规则**：拒绝任何输入比输出更晚定值的生产者 → 储物梯（A→B→A）在构造上不可能 |
| `choose_producer` | `ProducerChooser` | 代价在最优 γ 倍（默认 2.0）内的候选进短名单，短名单内按 S(r) 选，平局回代价；记录次优与差距 |
| `plan_target` + ledger | `PlanExtractor` + `ResourceLedger`/`PlanBuilder` | 自顶向下展开；`craftTimes = ceil(remaining / (out_amount × chance))` 用 `long`；any-of 按账本持有成员满足；副产记账；动作按执行序（依赖先）；无可用生产者或超预算 → `UNRESOLVED` |
| guards | `PlanGuards` | 池净增益 ≤ 0（目标池除外）；自依赖/净亏环拒绝（按**解析后的** any-of 成员判定）；每请求一个转换边集合防止副产重开反向边 |
| budget | `RoutingBudget.fromConeStats` | 展开上限 = base 512 + 4/生产者，封顶 200k；耗尽 → `status=incomplete`，`RoutingService` 回退 `StandardPlanner` |
| `chain_text` / `plan_json` | `RoutingPlanDebugText` | 解释：每动作代价分解、次优与差距、模板判断、被排除生产者及原因；每计划材料画像/机器/mod 足迹/触及池/触发的守卫 |

接入：`PlannerMode.hierarchical`；`RoutingService.choosePlanner`（:797–804）`auto` 分支优先 `hierarchicalPlanner`，失败/超预算回退。`AutoPlannerDispatchTest` 相应更新。

原型实测（`REPORT.md` §Phase 3）：22 包 3,301 对配目标，中位 actions 82 → 12、depth 33 → 5、round trips 42.8 → 0、tier 逆行 19 → 0.9、incomplete 0.717 → 0.005；fom 每目标 0.43 ms、atm10 19 ms 中位 / 164 ms p95。审计 13 案例赢 8（代价 7 + 排序器 1）、输 1（white_dye）、绕过 4。两个默认常数近乎无效（`tier_penalty`、`machine_cost` 归零只动 0.002）——Java 侧要么有意调大要么去掉。

---

## 7. 反馈（Phase 4）

原型 `feedback.py`，契约见 `DESIGN.md` §Phase 4，数字见 `REPORT.md` §Phase 4。

| 原型 | Java（§1 的包） | 说明 |
| --- | --- | --- |
| `FeedbackState` / `FeedbackStore` | `api/.../feedback/FeedbackStore` | 每包一个 JSON（fom 3.4 KB），字段见 DESIGN §1；按 `manifest.data_hash` + catalog key 版本化，不匹配**拒绝并上报**，不静默套用；写入原子（临时文件 + move），与 `DerivedIndexCache` 同目录 |
| `PlannerFeedback` | `api/.../feedback/PlannerFeedback` | `have/ban/unban/prefer/choose/reset`；实测每调用 atm10 p95 ≤ 2.5 ms |
| `FeedbackLearner.add_evidence` | `api/.../feedback/FeedbackLearner` | 模板级 Beta/Dirichlet 伪计数，先验强度 `n0 = 4`；**一次 ban ⇒ `junk' ≥ 7/8 = 0.875 > τ_junk`，与模型原本的信念无关**——这条不等式是设计，不是调参 |
| `FeedbackLearner.spread` | 同上 | 标准化 129 维模板特征上的余弦 k-NN（k=8，下限 0.70，单邻居 ≤ 0.5、合计 ≤ 1.0 倍）。推论（已测）：邻居只有在模型本来就倾向 junk（p > 0.42）时才会越过阈值 |
| `FeedbackLearner.step` | 同上 | 成对在线更新，`d ← (1−λ)d + η·g·z/‖z‖²`，`‖d‖ ≤ 0.25‖coef‖`；因为除了 `‖z‖²`，**一次点击正好把 margin 推动 `η·σ(−m)` 且不会更多**。`d` 存在导出的 `coef` 空间，188 个 float |
| `LazyScore` | `QualityRuntime` 的 producer-score 轴 | `S(r)` 留在 `RecipeGraph.Node`，`ΔS(r)` 按输出节点缓存：**每个生产者一次点积**，实测 0.027 ms/组（atm10） |
| `FeedbackView` | `CandidatePolicy` / `ProducerChooser` 读的数组 | 未被反馈触及的数组**共享而非复制**。Java 侧后验按**模板**存（96,393 行）而不是按配方（283,422 行），原型按配方导致视图重建 14.6 ms，这是原型形状不是设计代价 |
| `TemplateInquiry` | `api/.../feedback/TemplateInquiry` | `uncertainty × impact`，每个 family 每会话一问，两个答案 keep / ban-at-template-scope |
| `PlanOptions` | `RoutingRequest` 的选项对象 | **不属于计划本身的身份**：不带选项的请求序列化结果与 Phase 3 逐字节相同（有快照测试） |

Java 侧必须照搬的三条结论：

1. **显式 ban 压过"唯一生产者永不被排除"。** 那条门是防模型的，不是否决用户的。全部生产者被 ban 时，需求落到 Phase 3 既有的 orphan 规则写进 `[RAW]`。实测意义：fom 的 48 条重染色模板只有**模板级** ban 能把它从计划里拿掉（配方级 ban 让后验全部越阈，但 14/48 被唯一生产者保护救回，10/10 计划照用）。
2. **模板证据在新包上是冷启动，排序器 delta 不是。** 有角色的模板只有 130 个出现在 ≥2 个包组；而 delta 是对**特征空间**的陈述，实测把一个包里的一次点击带到另外四个包的审计案例上（margin 全部朝审计方向移动）。因此 `FeedbackStore.merge` 应当允许"证据按包、delta 跨包"。
3. **反馈会抬高 `junk_in_path_wl` 16%**（0.0219 → 0.0255）。跟随人工审计就会走进规则重放判为 junk 的路线；两个标签源在这些路线上不一致。保留 builtin 规则作第二道网会压住现象但不解决分歧。

---

## 8. 性能（Phase 5）

原型 `bench_planner.py`，契约见 `DESIGN.md` §Phase 5，数字见 `REPORT.md` §Phase 5。

**建图期预算（实测，Python）**：atm10 冷建 39.4 s（其中 13.8 s 是 parquet → CSR，23.4 s 是模型推理），暖建 16.1 s。**对 Java 有意义的是"暖建 − CSR"**，因为 CSR 就是 `RecipeGraph` 本身：fom 0.07 s、atm10-lite 0.25 s、minecolonies 0.56 s、**atm10 2.31 s** —— 在计划的 3–5 s 预算内，且这还是 Python。

**请求期（实测）**：fom 中位 0.50 ms / p95 0.67 ms；minecolonies 4.64 / 10.09；atm10 最深组 **29.1 / 320.1**、随机组 **15.2 / 139.1**（每包 50 个基线最深目标 + 150 随机）。

**磁盘缓存清单与体积**（每 catalog，不含 `RecipeGraph` 本身与 jar 内资源）：fom 0.51 MB、atm10-lite 2.58 MB、minecolonies 5.98 MB、**atm10 23.79 MB**。逐文件的"落到 `Node` 还是侧表"见 `DESIGN.md` §Phase 5 §2，`bench_planner.CACHE_INVENTORY` 是机器可读版本，`tests/test_phase5.py` 断言每一项都存在且被分类。

**四条要带进 Java 的结论：**

1. **跨请求共享空库存定点，默认开。** 实测 3.4×–48.9× 加速，命中率 149/150，计划逐字节不变；共享 memo 与"调用方自己把 solve 提出来"在噪声内相等。memo 键必须包含排除位图的指纹，且**该指纹要缓存在 `SourcePolicyDecision` 上**——每请求重算它在 atm10 上要 9 ms，吃掉三分之一的收益。
2. **材料池折叠减枝不要移植。** 六个包上实测 0.97–1.00×（即更慢），200 个目标里只改 0–2 个执行计划，13 个审计案例一个不变。它唯一说明的事情是"同模板的兄弟生产者在实践中可互换"——那是 Phase 4 模板级反馈的论据，不是减枝的论据。
3. **预算形状保持 cone。** 三种形状 × 三个 base × 200 目标：默认 base 下**没有任何目标被预算截断**，depth 变体在噪声内、base 降到 8 时反而更差（5% 截断），时间盒要么等价（0.25 s）要么灾难（0.05 s：默认 base 下 16%，base 8 下 98%），且不确定性。`budget_base` 的真实作用是"多久付一次锥体行走的钱"：atm10 上从 512 降到 64，中位耗时 15.2 ms → 96.6 ms（6.3×），截断率不变。`GraphTopology` 的锥体行走要么按 `(root, policy)` 缓存，要么把 base 留在典型展开数之上。
4. **`RoutingPlan` 的解释块要惰性构建。** 十个 atm10 深目标里有 186 k 次 `round()`，全在 `why.cost` / `why.shortlist` / `why.excluded`。

**对 `HANDOVER.md` §8 与 Phase 3 开放问题 2 的更正**：atm10 的 15% `incomplete` **不是 `BUDGET_TRUNCATED`**，而是 `SELF_DEPENDENT`（逐目标复核：17/110 个不完整计划各带一行 unresolved，原因全是自依赖，展开数 264 远低于 512 的额度）。预算形状没有问题要修；自依赖有。

---

## 9. 测试计划（`:Mod:test`）

| 测试 | 内容 | 依据 |
| --- | --- | --- |
| `TokenClassTableTest` | fixture 包（`src/test/resources/chainexport/fixture-pack`）+ fom 导出：类表与模板 id 与 Python `out/fom/{identity,recipe_template}.parquet` 一致 | §3.1–3.2 |
| `TemplateSignatureTest` | 签名 canonical JSON 字节与 Python `templates.parquet.signature` 相同 | §3.2 |
| `EconomyParityTest` | fom 的 value/tier/pool/reliability 与 `out/fom/economy/*.parquet` 在容差内 | §3.5 |
| `ModelParityTest` | `golden-sample.json` 全部行：junk_p、角色、margin、叶子分 ≤ 1e-6 | §3.4 |
| `QualityRuntimeTest` | 排除位图：唯一生产者永不排除；先验分量存在；扫描序按 S(r) | §5 |
| `HierarchicalPlannerOfflineTest` | `ExportPackLoader.load(dir, LoadOptions(keepJunk=true))` 加载 fom + 审计包，解 A/B 目标子集，`RoutingPlanReverseVerifier` 通过；redstone 梯目标无往返 | §6 |
| `PlannerFeedbackTest` | ban 一条 → 同族全部降权；prefer 翻转一个审计案例；更新毫秒级；持久化往返 | §7 |
| `RecipeRoutingScaleBenchmark` + atm10 深锥 | 建图期增量 ≤ 5 s；请求期与原型同量级 | §8 |

---

## 10. 移植顺序与验收

1. 契约先行：`ModelParityTest` 与 `TemplateSignatureTest` 绿之前不写规划器。
2. 本体 → 经济 → 特征/推理 → `QualityRuntime` 接线（此时旧 `StandardPlanner` 已受益：排除 + 先验 + 排序）→ 清理规则层 → `HierarchicalPlanner` → 反馈 → 性能。
3. 每步的验收门在 `HANDOVER.md` §9。
