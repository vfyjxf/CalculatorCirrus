# Cirrus 智能规划器重构 — 非移植工作交接文档（Phase 0 + Phase 3/4/5 原型 + Java 规格）

日期：2026-09-20（范围修订）。计划 `~/Downloads/plan.md/plan (1).md` 中**所有非移植工作**由本层交付：Phase 0 全部（本体、经济层、标签/特征、模型），Phase 3 分层规划器的设计与完整 Python 原型及离线 A/B（`planner.py`、`eval_planner.py`，已完成），Phase 4 反馈学习原型（`feedback.py`）与 Phase 5 性能基准/缓存设计（进行中），以及 Phase 1–2 的 Java 架构规格 `JAVA-DESIGN.md`。**交接给接手人的只有 Java 编码本身（按 `JAVA-DESIGN.md` 逐类翻译）与导出器修复（chance/value 字段，需要运行游戏）。** 数字与推导过程在 `REPORT.md`（实验报告）与 `DESIGN.md`（数据契约与算法），本文是索引与补充，不重复其内容。

---

## 1. 交接范围与状态

| 计划 todo | 状态 | 代码 | 产物 | 验收 |
| --- | --- | --- | --- | --- |
| p0-ontology 本体归纳 | 完成 | `load_exports.py` `ontology.py` | `cache/`、`out/<slug>/{identity,templates,recipe_template}.parquet`、`out/token-classes.json` | `tests/test_phase0a.py` 42 项通过；fom 模板数与计划目标差 8% 内 |
| p0-value-tier 经济层 | 完成 | `economy.py` | `out/<slug>/economy/*.parquet`、`out/economy/` | `tests/test_phase0b.py` 65 项通过；round-006 五个选路案例 4/5 在价值层通过 |
| p0-labels 标签+特征 | 完成 | `labels.py` `features.py` `llm_labels.py`(桩) | `out/labels/`、`out/features/` | `tests/test_phase0c1.py` 44 项通过；金标索引与缓存逐行对齐 100% |
| p0-models 模型训练/导出 | 完成 | `pair_features.py` `train_eval.py` `export_model.py` `predict_reference.py` | `out/models/`（3.75 MB JSON + golden sample） | `tests/test_phase0c2.py` 80 项通过；门槛表 §5.5（精度 PASS，召回/成对 FAIL，如实记录） |
| p3-planner + p3-eval 分层规划器（**Python 原型 + 离线 A/B**） | 完成 | `planner.py` `eval_planner.py` | `out/<slug>/planner/`、`out/planner-eval/` | `tests/test_phase3.py` 45 项通过；22 包 3,301 对配目标 A/B，审计 13 案例赢 8，见 §5.6 |
| p4-feedback 反馈学习（**原型**） | 完成 | `feedback.py` | `out/<slug>/feedback/`、`out/planner-eval/feedback-*.json` | `tests/test_phase4.py` 49 项通过；无反馈时 fom 50 目标计划逐字节不变；族泛化 18/18、white_dye 一次点击翻盘、留一法 15/19，见 §5.7 |
| p5-speed 性能基准与缓存设计（**原型**） | 完成 | `bench_planner.py` | `out/planner-bench/` | `tests/test_phase5.py` 22 项通过；atm10 深锥 28.4 ms 中位、暖建增量 2.03 s、memo 3.5–58×，见 §5.8 |
| p1/p2 Java 本体/经济/质量接线 | 规格已写，编码交接 | `JAVA-DESIGN.md` §1–§5 | — | 接手人：平价测试先绿（§9） |
| p3/p4/p5 Java 编码、tests | 交接 | `JAVA-DESIGN.md` §6–§9 | — | 接手人 |

执行方式：每阶段一个独立 agent 串行实现，主会话逐项核对产物（不只看报告）。每个阶段的 v1→v2 修正都记录在 `REPORT.md`/`DESIGN.md` 的对应小节。

---

## 2. 环境与运行方式

- Python：`.venv/bin/python`（uv，3.12；pandas 3.0.6、pyarrow 25、ijson 3.5、numpy 2.5、scipy、scikit-learn 1.9、lightgbm 4.7；`requirements.txt` 已锁定）。lightgbm 依赖 `brew install libomp`（已装）。系统 `python3` 是 3.9 且没有 pyarrow/ijson，不要用。
- 一键脚本：`run_phase0a.sh`（加载 + 本体 + 不变量检查）、`run_phase0b.sh`、`run_phase0c1.sh`、`run_phase0c2.sh`、`run_phase3.sh`、`run_phase4.sh`、`run_phase5.sh`。各 `.py` 支持 `--packs fom`、`--stage`，缓存命中时增量。Phase 4 全流程 < 2.5 min，Phase 5 ≈ 20 min（预算各 20 min）。
- 测试：`tests/test_phase0{a,b,c1,c2}.py`，plain assert，没有 pytest，直接用 venv python 跑。每个测试文件都含"可执行代码中无 mod/物品/颜色/系统名词表"检查，这是计划 §九 的硬约束。
- 机器：10 核 / 16 GB。磁盘仅剩约 26 GB（`cache/` 62 MB、`out/` 142 MB）。

| 阶段 | 22 包实测耗时 | 峰值 RSS | 计划预算 |
| --- | --- | --- | --- |
| 加载 + 归一化（ijson 流式） | 32.8 s | 674 MB | 5–10 min / < 6 GB |
| 本体 + 跨包先验 + 22 次留一包 onboarding | 35.6 s | 1.5 GB | < 2 min |
| 经济层 | 17–18 s | 2.5 GB | < 5 min |
| 标签 9.8 s + 特征 19.7 s | 29.5 s | 2.4 GB | < 5 min |
| 模型训练与导出 | 见 §5.5 | | < 1 h |

Java 侧前提（接手人必须知道）：

1. `Planner-Final/CloudLib-Standalone` 必须在 `feat/search-api` 分支（= `blueprint` + 一个 "search api" 提交）。CalculatorCirrus 自 2026-08-23 起 import `cloudlib.api.search`；2026-09-19 本地 `blueprint` 被 reset 丢掉该提交，造成 100 个编译错误，已 checkout 修复。
2. 基线 `./gradlew :Mod:test`：765 项，1 项既有失败 `RecipeClassifierTest.jeiBrewingMirrorIsDuplicate`（规则层，Phase 2 会替换该层），10 项 skipped。
3. 该账号会被多 agent 并发限流，子 agent 只能串行。

---

## 3. 数据与标签资产

语料：22 包 693,952 条配方、3,661,444 条内容行，0 条被结构性丢弃（`recipes.parquet` 行数 == 导出 `recipeCount`）。

导出：`Planner-Final/exports/<pack>/recipes-*.json`（schema `exports/SCHEMA.md`）。atm10（732 MB，283,422 条）与 minecolonies-dimensional-adventure（212 MB）的 recipes 文件曾被删、只存于 `exports.zip`，已解压恢复。

已对原始 JSON 核实的数据缺陷（下游设计全部绕开了它们）：

- `chance` 在全部 3.66M 行上恒为 1.0；`value` 类内容（时间/能量）一条都没有。计划里的"值类流"与"概率产出按期望"在现有导出上是死输入，只以钩子实现并由合成测试覆盖。根因在 Java 导出器，Phase 1+ 待修。
- 28.8%（200,053 条）配方 id 为空，集中在 JEI 分类系统；atm10 有 138,578 行与其他行共用 id。**任何 join 只能按 `recipe_idx`（导出流索引），不能按 id。** Java `ExportPackLoader` 把空 id 合成为 `jei:/synthetic/<i>`，`<i>` 就是同一个流索引。
- `BlockFront - Official Mod Pack 0.9.0.24b` 与 `LuckyBlockChallenge` 是同一份导出（签名集哈希相同，各 1,277 条）。所有表带 `pack_group`，切分按组。
- atm10/atm10-lite 没有注册表侧文件，tag 家族回退为 item（与 Java loader 一致）；5,566 条无机器；`jei:category/minecraft/anvil` 的输出 amount 是经验等级不是数量。

| 标签来源 | 位置 | 规模 | 用途 |
| --- | --- | --- | --- |
| 规则重放弱标签（builtin rules v3） | `Planner-Final/experiments/rulehits-v3/features/<pack>.jsonl` | 22 包 692,608 条：useful 341,186 / middle 269,696 / junk 81,726 | bronze 成对、`wl`。由 Java `BayesExperimentMain features` 生成（21 包 10 s，atm10 单跑 14 s，`-Xmx9g`），命令见 `experiments/generalization/README.md` L23 |
| 金/银配方级标签 | `Planner-Final/experiments/svm/data/labeled.jsonl`，`svm/labels/adjudication.tsv` | 10 包 112,854 条（recipe 级 55,680 / system 级 57,174；adjudication 27,735 最净） | 模板 `junk_target`/`role_target`、silver 成对。字段 `i` 与 `recipe_idx` 在 10 包上逐行对齐 100%（已逐包核对） |
| 人工链审计 | `CalculatorCirrus/chain-review/round-001..006/` | round-006 16 条选路发现 → 19 行成对金标（9/16 可解析）；4,326 条链级 ok/bad；rounds 1–5 约 200 条 junk 族 | 成对**评估**集（不训练）；chain 成对 |
| 链导出 | `Planner-Final/chain-exports/<pack>/batch-NNN/` | 685,166 条 EXECUTE，95% 定位到配方 | 链级判定落到配方 |

金标只覆盖 10 包（atm10 占 69%），其余 12 包无金标，正好是零样本/留一包测试集。LLM 离线标注只做了带预算保护的桩（`llm_labels.py`），从未调用。

---

## 4. 产物与契约（设计与代码参考）

### 4.1 目录

```
experiments/quality/
  common.py            slug、id 分词、canonical JSON、稳定哈希、pack 文件发现、RSS
  load_exports.py      ijson 流式加载 → cache/<slug>/{recipes,contents,systems}.parquet, registry.json, load-meta.json
  ontology.py          token 类归纳、component 键集类、四元组身份、模板折叠、跨包先验、onboarding
  economy.py           图构建、守恒/形状角色、池、tier、价值定点、叶子可得性、数据缺口、可靠性
  labels.py            四来源标签 → 模板级目标、成对数据
  features.py          六层 129 维模板特征 + 配方特征
  train_eval.py        TemplateRoleModel / ProducerRanker / 监督 LeafAcquirability，LOPO + 零样本 + 消融
  export_model.py      out/models/*.json
  predict_reference.py 纯 numpy 从 JSON 推理（Java 移植契约）
  tests/               test_phase0a/0b/0c1/0c2/3/4/5.py（+ fixtures/ 的计划快照）
  planner.py           分层规划器（Phase 3）+ 一个附加的 PlanOptions 缝（Phase 4/5）
  eval_planner.py      离线 A/B、审计案例、消融
  feedback.py          反馈状态/学习器/问询/持久化（Phase 4）
  bench_planner.py     深锥基准、建图期分解、memo/折叠/预算研究（Phase 5）
  DESIGN.md REPORT.md  契约 / 报告（每阶段一节，含 v1→v2）
  cache/  out/         产物（gitignore）
```

### 4.2 本体（`DESIGN.md` Phase 0a §2）

- id 分词：`namespace:path`，path 按 `/ _ . -` 切、小写。
- 类比归纳：两个 id 仅一个 token 位不同 = 一次互换证据；证据图上先做**上下文余弦**（`ev(a,b)/√(ctx(a)·ctx(b))`，阈值 0.7，`--min-evidence 2`）再 Louvain。原始共现直接聚类会塌成两个巨型 blob（fom 只剩 4 类），余弦是关键。
- 多 token 单元（`light_blue`、`dark_oak`）用**非组合性**判据提升：bigram `a_b` 只有当它能与单 token 互换（≥2 个单 token 伙伴、≥2 个上下文，且 `unit_contexts(a_b) >= solo(a)`）才成为单元。频率判据会把 `black_acacia`、`acacia_seat` 这类可组合复合词提升成单元，导致先验里出现 371 成员 blob；改判据后提升数 24,672 → 724，先验 959 → 540 类。
- 角色（material/form/modifier）纯按位置统计猜测：末 token → form，倒数第二 → material，其余 → modifier；未入类 token 以 `t:<tok>` 透传，四元组总是全的。
- 模板签名 = canonical JSON `{sys, mach, in, out, rid, pat}`：`in/out` 是抽象内容 `kind|content_type|material|form|modifiers|variant_class|amount|chance_bucket` 的排序多重集，`rid` 是抽象后残留的 id path token，`pat` 是 `side:kind:match_strategy` 模式；`template_id = "tpl:" + sha1(签名)[:12]`，跨运行、跨包稳定。`any`/`typedAny` 的 `base_id` 取成员字典序最小（Java 取 `get(0)`，成员序在导出间不稳定，已验证）。
- 跨包先验 `out/token-classes.json`：540 类 / 2,752 token / 22 包，`onboarding.json` 记录每包在其余 21 包先验上新增的类数。

### 4.3 经济层（`DESIGN.md` Phase 0b）

- 图：stack → 物品节点；tag/any/typedAny → 去重的 OR 节点（价值取成员最小、材料取共同类否则 `mixed`）；complex → 不入图；同配方两侧同物品做净额（催化剂钩子）；amount × chance（概率钩子，现数据恒 1.0）。
- 守恒/形状角色：按材料类聚合两侧；`is_round_trip` 来自同材料物品图的 SCC；转换容差带从 round-trip 确认的守恒配方比值分布**学习**（中央 90%），不假设 ≈1（3 块 → 6 台阶合法）。角色 production / conversion / recycling_shaped / noop / generation 只由形状信号定义；`recycling_shaped` 最弱（99% 命中是 anvil 的经验等级行）。
- 池：转换角色配方的物品图 SCC + 弱连通分量；`pools.parquet`、`pool_edges.parquet`。
- tier：单调 min-max 定点；机器 tier = 机器物品自身 tier，在同一定点里联立求解（机器作为每配方地板，先做一遍无机器 pass；否则"工作台造工作台"成环，815 轮 → 33 轮）。`reverse_tier` 用 Tukey 围栏而非分位数。
- 价值定点（Knuth 版 Dijkstra 超图松弛），四个必须保留的修正（v1 把花朵定到钻石之上、所有金属锭 = 圆石价，见 `REPORT.md` "The four v1 defects"）：
  1. 叶子种子限幅：`seed = K^(1 − acq)`，`--seed-spread` K 默认 2，无监督分只在可采集物之间破平局，不能压过一次合成步。
  2. 每步成本 `--step-cost 0.25 × median(leaf seed) × (1 + recipe_tier)`，链不再免费，tier 单调被计价。
  3. 封闭环底价 = 环内最廉成员的可得性种子，只对"每个成员的每个生产者都依赖环内成员"的封闭环启动；有外部入口的环走普通松弛。v1 用全局最廉叶子做底价，把 71 个物品钉在圆石价。
  4. 可靠性权重用 `max(reliability in pack) / reliability`（相对）而非 `1/reliability`（绝对）：字段上限 0.5 是语料常数，绝对形式会把深度惩罚 2^depth。
  求解器暴露 `recipe_weight` / `exclude` 参数，供角色后验回灌（任务/展示系统污染价值是 Phase 0c 模型的事，经济层不做规则）。
- 叶子可得性：无监督 = 秩归一化特征 → PC1，方向定为"消费广者廉"；`n_forms_of_material` 已从复合中移除（钻石 14 形态、`cls:nether` 类把 nether_star 算 6 形态，它度量的是核心度不是廉价度），列仍保留在 `leaf_features.parquet` 供监督拟合。
- 数据缺口：`producer_packs`（去重后其他包中有生产者的包数）≥ 多数 → `data_gap`。
- 可靠性：flag 惩罚 × 系统形状因子，全语料封顶 0.5（chance/value 两个字段退化是测出来的，不是假设）。

### 4.4 标签层（`DESIGN.md` Phase 0c-1 §1）

- 四来源精度序：adjudication > human-nb > audit > subagent > svm-rN > 规则重放；chain（人工 ok 链）高于 bronze。
- category → role 映射表是**唯一被允许的词表**（`labels.py` §1.2，训练期，作用于既有标签类别名而非运行时数据）：variant-swap → conversion；recycling-reverse → recycling；free-generation/only-source-generation → generation；display-page/mirror-duplicate/pseudo-recipe/trade-quest 族 → display；real-* → production。
- 模板聚合：多数 verdict、purity、冲突标记、`label_weight`（来源 tier × purity × 包组归一）。purity 在 99.8% 模板上为 1.0，权重实际由 tier 决定。
- 成对构造（`pairs.parquet`）：同输出 ≥2 生产者，每输出采样封顶；gold（round-006，仅评估）、silver（useful > junk 金标兄弟）、chain（ok 链内配方 > junk 兄弟）、bronze（wl useful > junk）。round-006 金对的选中方由被引链**实际 EXECUTE** 的配方决定 + 同输出约束；靠散文启发式挑对会错 4/12。`F-lostworld-2` 对 `minecraft:granite_wall` 的第二种读法是误读，模型阶段已剔除。

### 4.5 特征层（`DESIGN.md` Phase 0c-1 §2）

129 维（L1 本体 34 / L2 守恒经济 21 / L3 进度 8 / L4 资源 9 / L5 意图 23 / L6 词法残留 34），float32，一行一 (pack, template_id)；`feature-catalog.json` 给每列名称、层、聚合方式。三个值得知道的定义：兄弟离群 z-score（同输出生产者在 n_inputs / value_in / tier / n_output_slots 上的 z 分数聚合到模板）；L6 是残留 `rid` 与系统路径 token 的 32 桶哈希，**按包内命名空间集合过滤**（否则 `minecraft`/`ftbquests` 作为路径段泄漏，一个桶与 junk 相关 −0.80）；价值按包中心化（`log v − median log v`），因为原始价值跨包不可比。`recipe_features.parquet`（42 列）供成对特征 `f(a) − f(b)`。

### 4.6 模型与参考推理（`DESIGN.md` Phase 0c-2）

`out/models/`（3.75 MB JSON + 一个 5.6 MB 数据 parquet）：

| 文件 | 内容 |
| --- | --- |
| `feature-spec.json` | 129 模板 + 188 成对 + 10 叶子特征名、所属层、NaN 填充值、标准化 mean/std、L6 哈希规范（与 `features.py` 逐位一致） |
| `template-role.json` | junk 头（二分类）+ role 头（5 类），LR 与 LightGBM 双臂都导出 |
| `producer-ranker.json` | 成对排序器双臂；LR 臂 = 无截距点积 |
| `leaf-acquirability.json` | 监督叶子分：10 个权重、K、每包标准化 |
| `token-classes.json` | Phase 0a 跨包先验，带版本戳 |
| `golden-sample.json` | 1,000 行 / 21 包组 + 500 对 + 300 叶子，含原始特征向量与每个模型的期望输出——**Java 移植的平价测试夹具** |
| `manifest.json` | 版本、数据哈希、双臂指标、默认臂选择理由、文件大小 |
| `recipe_leaf_agg.parquet` | 每配方叶子输入的 mean/min 聚合（`pair_features.py`，3.2 s），是数据不是模型 |

- 默认臂规则"LR，除非 LightGBM 在头指标上领先 > 0.02"：junk 头 GBM（+0.021）、role 头 GBM（+0.025）、ranker LR（silver-LOPO 0.811 vs 0.656，GBM 在 enigmatica10 折上 0.061 即自信地反着排）。两个头的差都只比容差高 0.005；LR 在配方级 AUC（0.881 vs 0.860）与零样本第一折（0.961 vs 0.776）反而更好。**建议 Java 全用 LR**：损失约 0.02 AUC，换来一条代码路径和 1/10 的 JSON。
- `predict_reference.py` 只 import numpy，在 golden sample 上复现全部库预测，最大误差 6.1e-15（容差 1e-6，Java 求和序不同也不会破）。契约见 `DESIGN.md` Phase 0c-2 §4。
- 训练协议：留一包组（10 个有金标的组）；样本权重 = 来源 tier × 包组封顶 40%（atm10 占标签 69%）；成对样本反对称化、无截距；gold 19 行只评估从不训练（其中 `F-lostworld-2` 的 granite_wall 读法已剔除，余 18）。
- **对规划器设计最重要的一条**：经济层单独做生产者排序在人工标签上是抛硬币（silver 0.550），在规则重放上只有 0.137——junk 配方（镜像页、单槽伪配方）的输入系统性地比正经兄弟更便宜，纯代价最小化会径直走进去。排序器必须在代价求解之后重排同产物生产者，这是 API 决定而非模型决定；换叶子种子对五个审计案例毫无作用（10/18 无监督 = 10/18 监督，同样的 10 个）。

### 4.7 分层规划器（Phase 3 原型，`DESIGN.md` §Phase 3，Java 映射表在 `JAVA-DESIGN.md` §6）

`planner.py` 的结构就是 Java 类结构：`PackIndex`（建图期缓存：按 `base_id` 的生产者索引、OR 节点代表成员、每配方后验/角色/tier/机器/可靠性/模板、每输出生产者分）→ `Policy`（候选策略）→ `solve_cost`（请求作用域代价求解）→ `choose_producer`（形态层）→ `plan_target` + 账本（抽取）→ guards → `chain_text`/`plan_json`（与基线同格式的解释输出）。要点：

- **代价求解**是请求作用域的 Knuth/Dijkstra 超图定点：库存 0 价、叶子种子、每步 `step_cost×(1+tier)`、机器足迹（首次使用付 tier 代价；求解期贪心、抽取期精确）、tier 逆行罚、可靠性；记忆键 = (item, 剩余库存签名)。**settle-order 规则**——拒绝任何输入比输出更晚定值的生产者——使储物梯（redstone ↔ block）在构造上不可能；fom CHAIN 0001 从 102 动作/46 次往返变为 27 动作/0 往返。
- **候选策略**：`junk_p > τ_junk`（0.8707，P≥0.95 工作点）或 `P(display) > 0.5` 且存在其他可用生产者 → 排除；唯一生产者永不排除（display 判断也在保护之内）；`conversion` 降级为"只在库存/副产已有输入形态时可用"而非禁止（字面禁止会让每个储物块从空库存起都 UNRESOLVED）；`recycling` ×8 末位；`generation` 可靠性加权。
- **生产者选择**：代价在最优 γ=2.0 倍内进短名单，短名单内按排序器分 S(r) 选，平局回代价；记录次优与差距。排序器 LR 臂的 margin 可分解为 S(a)−S(b)，S(r)=w·f(r)，Bradley–Terry 聚合为每配方一次 180 维点积（与 `predict_reference.margin` 最大差 1.8e-14），atm10 的 2,560 万成对 margin 变成一次 283k×180 矩阵乘。
- **守卫**：池净增益 ≤ 0（目标池除外）；自依赖/净亏环按**解析后的** any-of 成员判定；每请求一个转换边集合防止副产重开反向边；预算 base 512 + 4/生产者、封顶 200k，耗尽 → `incomplete`（Java 在此回退 StandardPlanner）。
- 不变量测试抓到的三个坑（Java 同样会踩）：净亏守卫若按 tag 成员集合而非解析成员判定，会让 noop 切石机自递归、并禁掉全部重染色配方；`display` 判断放在唯一生产者保护之外会孤立唯一产物；账本对无环规则的例外会用刚产出的副产重开反向边。
- 常数：`tier_penalty` 1.0 与 `machine_cost` 0.25 在默认值下近乎无效（归零只动 0.002），机制正确但相对输入价值分布太小。

解释输出的实例（farmers-colonies `minecraft:blue_dye`，`planner.py --stage solve --packs farmers-colonies --target minecraft:blue_dye --dump <dir>`，0.09 ms）——Java 的 `PlanExplanation` 至少要携带同样的字段：

```json
"why": {
  "decided_by": "cost",
  "cost": {"inputs": 1.9308, "steps": 1.7678, "machines": 0.4419, "tier_penalty": 0.0,
           "reliability_weight": 1.0789, "role_multiplier": 1.0, "out_amount": 1.0, "unit_cost": 4.4674},
  "runner_up": {"recipe_id": "minecraft:blue_dye", "unit_cost": 5.2194, "producer_score": -0.1945, "gap": 0.7520},
  "judgements": {"junk_p": 0.4966, "role_argmax": "production", "effective_role": "production",
                 "shape_role": "production", "producer_score": 0.1946, "reliability": 0.4524, "tier": 4.0, "reverse_tier": false},
  "excluded": [],
  "shortlist": [{"recipe_idx": 186, "unit_cost": 4.4674, "producer_score": 0.1946},
                {"recipe_idx": 281, "unit_cost": 5.2194, "producer_score": -0.1945}]
}
```

计划级 `profile`：叶子可得性画像（low/mid/high 占比）、机器足迹（机器 id + tier）、mod 足迹、触及的池、触发的守卫、候选策略统计（该包 flagged 1,030 / protected 32 / excluded 998；角色 production 1,647 / display 923 / conversion 88）。

### 4.8 反馈学习（Phase 4 原型，`feedback.py`，`DESIGN.md` §Phase 4，Java 映射表在 `JAVA-DESIGN.md` §7）

全部闭式更新，无梯度训练；规划器通过一个附加的 `PlanOptions` 缝读取反馈状态，无反馈时计划与 Phase 3 逐字节相同（快照 sha256 `adb9c06a…`，有测试）。

- **状态**（每包一个 JSON，fom 3.4 KB，按 `manifest.data_hash` + catalog key 版本化，不匹配拒绝而非静默套用）：模板级 Beta（junk）/ Dirichlet（角色）伪计数，先验强度 `n0 = 4`，由模型后验初始化；排序器 delta 权重 `d`（188 个 float，落在导出 `coef` 空间）；物品级 ban/prefer；库存。
- **`ban(recipe)`**：模板 junk 证据 +1 ⇒ 后验 ≥ 7/8 = 0.875 > τ_junk，与模型原信念无关（这是设计不等式，不是调参）；同时按标准化 129 维特征上的余弦 k-NN（k=8，下限 0.70，单邻居 ≤ 0.5、合计 ≤ 1.0 倍）向邻域扩散，推论是邻居只有在模型本来就偏 junk（p > 0.42）时才会越阈。`prefer(recipe)` 对称加 useful 证据。
- **`choose(a over b)`**：`d ← (1−λ)d + η·g·z/‖z‖²`，`‖d‖ ≤ 0.25‖coef‖`；一次点击恰好把 margin 推动 `η·σ(−m)` 且不会更多。`prefer(recipe)` = 对每个兄弟各一次低权重 choose。由于线性，`ΔS(r)` 按输出节点惰性缓存，每生产者一次点积（atm10 0.027 ms/组）。
- **`have(item, n)`** 进库存（转换门因此可用）；**`ban(item)`** = 该物品不可作为输入来源；**`prefer(item)`** = 记录在案的代价折扣。
- **`TemplateInquiry`**：规划后按 `uncertainty（Beta/Dirichlet 熵）× impact（crafts × 使用次数，或短名单差距）` 排序，每个模板族每会话最多一问，附 3 个示例配方与两个答案（keep / 模板级 ban）。
- Java 必须照搬的三条结论：显式 ban 压过"唯一生产者永不排除"（那条门防模型不防用户；全部被 ban 时需求按 orphan 规则写进 `[RAW]`）；模板证据按包存（新包冷启动），排序器 delta 跨包共享（它是对特征空间的陈述，实测一次点击把另外四个包的审计 margin 都推向审计方向）；后验按**模板**（96k 行）而非按配方（283k 行）存，否则视图重建 14.6 ms。

### 4.9 性能基准与缓存（Phase 5 原型，`bench_planner.py`，`DESIGN.md` §Phase 5，摘要在 `JAVA-DESIGN.md` §8）

- **缓存清单**（每 catalog，不含 `RecipeGraph` 与 jar 内资源）：fom 0.51 MB、atm10-lite 2.58 MB、minecolonies 5.98 MB、atm10 23.79 MB；每项"落到 `Node` 还是侧表"见 `DESIGN.md` §Phase 5 §2，`bench_planner.CACHE_INVENTORY` 是机器可读版本并有测试断言。
- **跨请求共享空库存定点，默认开**：memo 键 = (item, 库存签名, 排除位图指纹)；指纹必须缓存在 `SourcePolicyDecision` 上（atm10 每请求重算 9 ms，吃掉三分之一收益）。
- **材料池折叠减枝不要移植**：六包实测 0.94–1.08×，200 目标里只改 0–2 个计划，审计案例不变；它只说明同模板兄弟生产者在实践中可互换——那是模板级反馈的论据。
- **预算形状保持 cone**；`budget_base` 的真实作用是"多久付一次锥体行走"：atm10 上 512 → 64 使中位耗时 15.2 → 96.6 ms 而截断率不变，Java 要么按 `(root, policy)` 缓存 `GraphTopology` 的锥体行走，要么把 base 留在典型展开数之上。时间盒不确定且要么等价要么灾难，不用。
- 解释块（`why.cost` / `shortlist` / `excluded`）惰性构建：十个 atm10 深目标里 186k 次 `round()`。
- **更正 Phase 3 的一个诊断**：atm10 的 15% incomplete 是 `SELF_DEPENDENT`（逐目标复核 17/110，各一行 unresolved，展开数 264 远低于 512），不是 `BUDGET_TRUNCATED`；预算没有问题要修，自依赖有。

---

## 5. 关键结果

### 5.1 本体

- 先验 540 类 / 2,752 token，提升 bigram 724，>100 成员的类只剩 2 个（114 家具形态、109 作物/木材名，都是真实多填充槽位）；颜色类 16 成员按证据质量居首，木材类 11 成员、工具/盔甲 9、Create 机器 16、铜氧化态 7。
- fom：3,442 条 → 1,769 模板（计划目标 ~1,931），293 个多成员模板覆盖 57.1%（目标 ~55%），48 条染色配方一个模板，石材 × 砖形态 × `cut` 修饰 78 条一个模板。
- 各包多成员覆盖 41–84%；onboarding：8 包新增 0 类，atm10 65、minecolonies 108。

### 5.2 经济层

角色占比 production 66.3% / conversion 18.3% / recycling_shaped 14.8% / noop 0.5% / generation 0.2%；29,140 叶子中 7,811 判为数据缺口。fom 价值梯：圆石 1.00 < 线 1.01 < 橡木 1.06 < 花 1.4–1.9 < 铁锭 2.50 < 钻石 2.79 < 青金石 2.81 < 下界合金 22.4。atm10-lite 机器梯自动得出 copper/iron 3 → gold 4 → diamond 5 → emerald 6 → obsidian 7 → netherite 8 → allthemodium 9 → vibranium 10 → unobtainium 11。

round-006 选路案例在价值层（K=2，step 0.25，生产者有效成本，低者胜）：

| 案例 | 胜者 | 成本 | 次优 | 成本 | 结论 |
| --- | --- | --- | --- | --- | --- |
| farmers-colonies blue_dye | cornflower | 4.418 | lapis | 5.446 | 通过（v1 输 12×） |
| farmers-colonies white_dye | bone_meal | 4.133 | lily_of_the_valley | 4.418 | **差 6.9%**（K=1 通过；无监督叶子分把单用途花判为稀有） |
| tfc-enhanced blue_dye | graphite | 2.202 | cornflower 2.573 | lapis 4.273 | 通过（矿路线最贵） |
| technical-electrical red_dye | rose_bush | 1.605 | lead 熔炼 | 2.273 | 通过 |
| lost-world mossy_cobblestone | from_vine | 22.777 | from_moss_block | 22.821 | 通过 |

### 5.3 标签

模板 240,377，其中 24,686（10.3%）有多数 verdict、17,704（7.4%）有角色目标（production 6,820 / display 5,645 / unknown 4,200 / conversion 813 / recycling 177 / generation 49）。成对 156,047：bronze 143,651 / silver 6,291 / chain 6,086 / gold 19；**atm10 占 94%**，训练必须按包组归一化权重。有角色的模板只有 130 个出现在 ≥2 个包组——模板身份跨包几乎不迁移，必须靠特征泛化。

### 5.4 特征

与金标 junk 的相关性（n = 24,686）：`l2_self_dependent_share` +0.62、`l2_out_subset_in_share` +0.49、`l4_pool_input_share` −0.48、`l3_machine_unbuildable_share` +0.42。11 列有 NaN，最高 `l4_leaf_acq_*` 84%（多数模板无叶子输入）。

### 5.5 模型门槛表

全部为留一包组交叉验证；完整表在 `REPORT.md` "The gate table" 与 "Every Phase 0 gate"。

| 计划门 | 数字 | 结论 |
| --- | --- | --- |
| LOPO junk 召回 ≥ 0.90 @ P ≥ 0.95（模板级） | LR 0.778 / GBM 0.809 | FAIL |
| 同上（配方级，规划器实际消费的粒度） | LR 0.854 / GBM 0.849 | FAIL |
| 金标精度 ≥ 0.95 | LR 0.966 / GBM 0.965 | PASS |
| 成对准确率 ≥ 0.85（silver-LOPO） | LR 0.811 / GBM 0.656（chain 0.870/0.912，bronze 0.865/0.940） | FAIL |
| gold 对（n=18，仅报告） | LR 0.611（加权 0.663）/ GBM 0.667（加权 0.878） | — |
| 新 mod 零样本（5 个命名空间） | 第一折 LR 0.961 / GBM 0.776，其余见报告 | — |
| 消融（去 L1 本体 / L3 tier / L6 词法） | 每层 ≤ 0.026 AUC | — |
| 完整实验 ≤ 1 h；改特征后重训 ≤ 20 min | 370 s；6.1 min | PASS |
| 全链冷跑（0a→0c-2，22 包） | 8 min 12 s | PASS |

解读：五个 FAIL 是同一个失败——模型精确但召回差 0.05–0.13，与早先同数据配方级 SVM/LR/GBM 研究（判断行 AUC 0.81–0.86，`experiments/svm/REPORT-V2.md`）撞到同一堵墙：10–15% 灰区标签噪声。消融表明特征层不是杠杆，灰区补标才是。角色头只有 production（召回 0.88）与 display（0.86）可用，conversion 0.29–0.48、recycling 0.23–0.54、generation 0.04 不可用，argmax 落在后三类时回退到 Phase 0b 的形状角色。junk 头在配方级安全工作点抓到 85% junk、误伤 3.5% 真配方，**只能作为规则之前的减负层，不能作为唯一过滤器**。

### 5.6 分层规划器离线 A/B（Phase 3，`REPORT.md` §Phase 3）

基线 = Java `StandardPlanner` 的链导出（空库存、builtin rules v2 生效）；原型对同一目标空库存求解。22 包、每包 150 目标（含全部审计目标），3,301 对配目标，A/B 94 s。

| 指标 | 基线中位 | 原型中位 | 基线均值 | 原型均值 | 原型更优 / 更差 / 平 |
| --- | --- | --- | --- | --- | --- |
| actions | 82 | **12** | 235.7 | **40.4** | 3085 / 93 / 123 |
| depth | 33 | **5** | 80.5 | **6.4** | 3161 / 9 / 131 |
| round trips（同池 A→B→A） | 1 | **0** | 42.8 | **0.0** | 1772 / 0 / 1529 |
| tier 逆行 | 8 | **0** | 19.0 | **0.9** | 2696 / 62 / 543 |
| incomplete | 1 | **0** | 0.717 | **0.005** | 2351 / 0 / 950 |
| raw_nonleaf（非叶子被当原料） | 43 | **1** | 183.2 | **1.43** | 2903 / 2 / 396 |
| junk_in_path（模型 junk_p） | 0.333 | **0.000** | 0.359 | **0.014** | 2989 / 24 / 288 |
| junk_in_path（规则 wl） | 0.000 | 0.000 | 0.015 | 0.022 | 866 / **581** / 1854 |

唯一变差的指标是规则口径的 junk_in_path：junk 头在 atm10-lite 只标出 50.6% 的规则 junk，被执行的 junk 配方多数是模型不同意（junk_p 中位 0.275）而非险些漏掉，最常用的一个因唯一生产者保护而运行——这是 §5.5 结论的端到端实测。

审计案例（19 行金标 → 13 个案例，只评估）：γ ≥ 1.5 时赢 8（代价单独赢 7，含 Phase 0c-2 曾输 40% 的 technical-electrical red_dye；排序器额外赢 1：tfc blue_dye）、输 1（farmers white_dye，代价与排序器都同意 bone meal）、绕过 4（其中 3 个走了真正更便宜的第三生产者）。把"选中方"的输入放进库存再解：6/13 翻到选中方而无需重定价——转换门在工作。耗时：fom 每目标 0.43 ms 中位；atm10 19.1 ms 中位 / 164 ms p95 / 338 ms 最大；22 包冷建 82.5 s（atm10 35.3 s）。

### 5.7 反馈学习（Phase 4，`REPORT.md` §Phase 4）

`feedback.py` + `tests/test_phase4.py`（49 项）+ `run_phase4.sh`。**先保证的事**：改 `planner.py` 之前对 fom 50 个目标的 `plan_json` 取了快照（`tests/fixtures/phase3-plans-fom.json.gz`，sha256 `adb9c06a…`），加钩子之后 `opt=None` 与默认 `PlanOptions()` 都逐字节复现，`tests/test_phase3.py` 45 项仍全绿。

- **族泛化**：7 个包 21 个最大模板各 ban 一个成员，**21/21 全族后验越过 τ_junk**（5,146 条配方被 21 次点击覆盖）。但后验不等于排除：fom 48 条重染色模板与 tfc 三个 84 条模板各自是其产物的**唯一生产者**，被保护救回（14/48、83/84），用到它们的计划照用。**模板级 ban（问询的第二个答案）是绝对的**——加上它，10/10、4/4、3/3、4/4 个计划都改走别的路线且**没有一个变 incomplete**。邻域附带影响：21 次点击里 14 次对族外零影响，最坏一次排除了邻域 46 条中的 45 条；设计上的界（邻居只有 p > 0.42 才越阈）处处成立。
- **唯一输掉的审计案例**：`F-farmers-colonies-2` white_dye，一次 `choose(preferred over chosen)` 后**改选 preferred**（margin −0.5011 → +1.9898，正好是 `η·σ(−m)`；‖delta‖ 0.069，上限 1.583）。13 个案例 8 赢 1 输 → **9 赢 0 输**，per-pack 与跨包共享 delta 同结果。泛化体现在 margin 上：同包 blue_dye +0.389 → +4.806（从未被点过），共享 delta 下 tfc blue_dye +3.276 → +5.848、lost-world blue_dye +0.686 → +3.759、technical-electrical red_dye +0.728 → +2.481。
- **最少标注学习曲线**（19 条 round-006 读数当反馈流，5 个随机顺序，每次点击后重跑审计 + 6 个包 360 个目标的 A/B 指标）：赢 8 → 9，输 1 → 0，中位 **5 次点击**修好、最迟 13 次；`incomplete` 恒 0；actions 均值 54.74 → 52.63。**代价：`junk_in_path_wl` 0.0219 → 0.0255（+16%）**——跟随人工审计就会走进规则重放判 junk 的路线。金标在这里既是流又是分，已明说。**留一法**（喂 18 条、打分第 19 条，真正的留出）：默认 14 preferred / 1 chosen → 本包 18 次点击后 **15 / 0**，共享 delta 后 14 / 0 / 5（多一个 neither：lost-world mossy_cobblestone 从 preferred 退成 neither）。**white_dye 被它从未见过的点击修好**，这是本层最强的泛化证据。
- **问询精度**（标注最多的 5 个包，各规划 150 个 A/B 目标后取前 20 问）：被问模板**有标签的比例 10.0%**（总体 7.8%，几乎等于随机）；但**有标签的被问模板里 80% 标签与当前信念相反**（总体 15.2%，5.3 倍）。分母只有 10，区间很宽。每 family 一问，20 问 20 个 family。
- **时延**（每方法 200 次调用）：atm10 上 `have`/`ban(item)`/`prefer(item)`/`reset` ≤ 0.003 ms，`choose` 0.031 ms，`ban(recipe)` 2.05 ms、`ban(template)` 2.06 ms、`prefer(recipe)` 2.00 ms（p95 全部 ≤ 2.6 ms，**达标 < 5 ms**），重打分一个输出组 0.027 ms。两个如实记录的数字：带模板证据的视图重建 atm10 14.6 ms 中位 / 23.4 ms p95（原型按配方存后验，Java 按模板存小 3 倍；只有 delta 时 0.069 ms）；**点击之后下一次请求要重建候选策略 128 ms + 重跑定点 619 ms**——这正是 §5.8 的 memo 要解决的形状。
- **持久化**：`out/<slug>/feedback/state.json`（fom 3.4 KB），往返逐字节一致、视图签名一致、merge 计数相加、`data_hash` 不匹配被拒。

### 5.8 性能基准（Phase 5，`REPORT.md` §Phase 5）

`bench_planner.py` + `tests/test_phase5.py`（22 项）+ `run_phase5.sh`，全部走同一个 `PlanOptions` 缝，默认仍逐字节等于 Phase 3。

- **深锥基准**（每包 50 个基线最深目标 + 150 随机，三趟取每目标最小值）：fom 0.50 / 0.67 / 1.26 ms（中位/p95/最大），minecolonies 4.64 / 10.09 / 13.62，**atm10 29.11 / 320.12 / 385.92**（最深组，基线深度 2,198）；随机组 atm10 15.24 / 139.07 / 316.75。峰值 RSS 129 MB / 1,075 MB / 1,842 MB。
- **更正 Phase 3 开放问题 2**：atm10 的 15% `incomplete` **不是 `BUDGET_TRUNCATED` 而是 `SELF_DEPENDENT`**（逐目标复核 17/110，展开数 264 远低于 512 额度）。预算没有问题要修，自依赖有。
- **`cProfile`（atm10 十个最深目标）**：**41% 的时间是三次惰性锥体行走**（`cone_stats`，每次约 96 ms）；其余平坦。`round()` 186 k 次全在解释块里。
- **建图期**：atm10 冷 39.4 s（13.8 s parquet→CSR + 23.4 s 模型推理），暖 16.1 s。**"暖 − CSR" 才是 Java 的增量**：fom 0.07 s、atm10-lite 0.25 s、minecolonies 0.56 s、**atm10 2.31 s**，在 3–5 s 预算内。磁盘缓存体积（每 catalog，不含图与 jar 资源）：fom 0.51 MB、atm10-lite 2.58 MB、minecolonies 5.98 MB、atm10 23.79 MB；逐文件的 node/侧表归属见 `DESIGN.md` §Phase 5 §2。
- **跨请求共享定点**：命中率 149/150，**加速 3.4×（atm10-lite）– 48.9×（minecolonies）**，atm10 19.6×，**计划逐字节不变**。共享 memo 与"调用方提出 solve"在噪声内相等。踩到的坑：memo 键里排除位图的哈希每请求重算要 9 ms（atm10），必须缓存在 `Policy` 上。
- **材料池折叠减枝**：六个包 **0.97–1.00×（即更慢）**，200 个目标改 0–2 个执行计划，13 个审计案例一个不变。**不要移植。**
- **预算形状**：默认 base 下三种形状都 0 截断，depth 在噪声内、base 8 时更差（5% 截断），时间盒 0.05 s 灾难（默认 base 16%、base 8 下 98%）且不确定。**默认 cone 保持不变。** `budget_base` 的真实含义是"多久付一次锥体行走"：atm10 上 512 → 64 让中位耗时 15.2 → 96.6 ms（6.3×）而截断率不变。

---

## 6. 计划文本与代码不符之处（已核实）

路径省略前缀 `Mod/src/main/java/dev/vfyjxf/cirrus/`。

| 计划原文 | 代码事实 | 对实施的影响 |
| --- | --- | --- |
| 按目录哈希落盘缓存，复用 `RecipeDatabaseCatalogCache` 模式 | 它是内存 5 分钟 TTL 单条缓存；main 中没有任何目录哈希 → 文件的磁盘缓存 | 磁盘缓存是新模式。建议 `FMLPaths.CONFIGDIR/cirrus/cache/<catalog-hash>/`（`RecipePathSettings.java:72` 已用 `CONFIGDIR/cirrus`），Gson 写 JSON |
| `craftTimes` 为 `int` 字段（:1508） | 是 `StandardPlanner` 局部 `int`（:1384、:1508），作为参数穿过 :1719/:1759/:2024/:2715/:3367/:3654/:3823，:1735 进 `FeatureType.craftTimes` | 改 `long` 要改整条参数链 |
| 概率产出当保证副产（:957） | `StandardPlanner` 无任何 chance 逻辑；chance 只在 `UnitCostOracle` :198/:224、`CostAwarePlanner:597`、`CandidateRuleInput:59`；输出 chance 在 `OutputContent.chance()` vs `RecipeContent.MAX_CHANCE`，催化剂 = chance-0 输入 | 期望产出是新增；且导出 chance 恒 1.0，先修导出器 |
| 放大风险只看深度（:3218） | 属实：`estimateAmplificationRisk(cycleCandidate, depth)` = 0.05 + min(depth,20)×0.02（+0.45） | 按计划改为材料池净增益 |
| 生产者按索引序截断（:1307） | 属实：`producerScanLimit` :1307–1311；扫描 `scanFilteredProducers` :1213–1272；排除位图 :1218 读 `SourcePolicyDecision.allowedRecipes()` | Phase 2 接线点正确 |
| 复用 `RoutingVariantKeys.stackKey` | 是 `private`（:84） | 放宽可见性或走 `inputKey`/`variantKey`；`ComponentStackKey` 在 `api/content/value/` |
| 结构规则在 routing 层 | `Recolor/Recycling/ColonyViewMirror/Exchange/Generation/MassSink` 在 `api/recipe/classify/structural/`，由 `RecipeClassifier.createDefault()` :91–103 插入；`builtin_rules.json` 在 `RecipeClassifier:116` 用 Gson + DFU `RuleDocument.CODEC` 加载 | Phase 2 清理目标在这里 |
| `CascadeScoreRuntime` 在生产路径 | 未接入 main，只被 `src/test/.../CascadeBenchMain` 调用；main 只有 `BayesRuntime.attach`（`RecipePathSettings.java:531`） | 删除无生产影响 |

另：`ExportPackLoader`（离线）加载时即丢弃 builtin 规则判为 JUNK/DUPLICATE 的配方（:281–286），离线评估新规划器必须 `LoadOptions(keepJunk=true)`；JSON 约定 Gson + Mojang DFU Codec（无 Jackson）；包级设计文档放代码旁（`api/recipe/routing/Process.md` 等）；`GraphTopology.of(graph)` 是"按图实例缓存派生结构"的现成模式。

---

## 7. Java 落地指引（指针）

完整规格在 **`JAVA-DESIGN.md`**：包放置、建图期流水线与预算、逐位复现的契约（分词/签名/特征/推理/经济）、磁盘缓存、Phase 2 三处接线与清理清单、规划器与反馈的类映射表、测试计划、移植顺序。以下只是定位指针。

- 规划器唯一注册缝：`RoutingService.java` :94–97（四个 planner 字段）+ :797–804（`choosePlanner` switch）+ `PlannerMode.java`（auto/standard/assuredBounded/assuredProven/costAware）；`AcyclicPlanner(RoutePlanner fallback)` 是委托回退的现成先例。
- 评分轴：`FeatureType`（14 值）→ `DefaultScoreModels.build` 三层 tier（safety/cost/quality，字典序）→ `StandardPlanner` :1735 `features.set`（并行调用点 :1414/:1434/:1623/:1659）。
- 运行时挂载：仿 `BayesRuntime`——静态 `attach(RoutingService, RecipeGraph)` 返回 `@Nullable RecipeCostHeuristic`，在 `RecipePathSettings.java:531` 接线；模型资源用 `RecipeClassifier.class.getClassLoader().getResource(...)` 从 `assets/calculator_cirrus/quality/` 读。
- `RecipeGraph.Node`（:1458–1928）字段：recipe、machineEntry、mergedInputs/Outputs（紧凑槽）、routingHints、stableSystemId/RecipeId/Hash、sourceOrder；模板 id / 四元组缓存加在这里。`GraphTopology`（impl 侧）现只有 SCC/环/往返对/锥统计，生产者/消费者计数是新增。
- api vs impl 硬分层：`recipe/routing`（impl）依赖 `api/recipe/routing`，不能反向；本体若被规则与规划器共读放 api 侧，economy/quality 放 impl 侧 `cost/` 旁。
- **移植契约不是模板 id 而是特征**：模型消费 129 维特征，Java 只需按 `out/models/feature-spec.json` 逐位复现特征与标准化，并用 `predict_reference.py` + `golden-sample.json` 做平价测试（≥1,000 行，LR 1e-6）。本体类先验直接加载 `token-classes.json`，新 token 走同一归纳。
- 离线 A/B：`ChainExportMain`（`:Mod:chainExport`，`-Xmx8g`，`--packs`）+ `CalculatorCirrus/chain_compare.py` 比对链树；`RoutingPlanReverseVerifier` 复用；JMH `RecipeRoutingScaleBenchmark`（`:Mod:jmhRun -PcirrusJmhInclude=...`）。测试用 `LoadOptions(keepJunk=true)`。

---

## 8. 已知问题与开放问题（按影响排序）

1. **任务/展示系统污染价值定点。** atm10-lite 里圆石最廉生产者是 `jei:category/ftbquests/quest`（原铁 → 圆石），梯度倒挂（nether_star 1.08 < diamond 3.21 < cobblestone 11.78）。可靠性的信息量只有 1–2×，压不住。设计上的解法：Phase 0c 的 display 角色后验通过求解器 `recipe_weight`/`exclude` 回灌，经济层刻意不加规则。
2. **white_dye 差 6.9%。** 无监督叶子分无法区分"单用途但随手可得"（铃兰）与"单用途且稀有"（潜影壳）——包内结构就没有这个信息。监督 LeafAcquirability（§4.6）是对症的；若仍不过，需要额外的叶子级标注。
3. **导出缺陷：chance 恒 1.0、无 value 内容。** 概率产出、催化剂、能量/时间成本三条计划线全部悬空，只有钩子和合成测试。修导出器（Java `ExportDriver`）后重跑 `run_phase0a.sh --force` 即可，下游代码无需改。
4. **标签噪声天花板。** 同数据早先研究判断行 AUC 停在 0.81–0.86；灰区（台阶/墙/切石变体、打蜡、重染色）近界带泄漏 56%。模板级聚合 + 六层特征是否突破，看 §5.5。
5. **模板身份跨包不迁移**（有角色模板仅 130 个出现在 ≥2 组）。零样本能力只能来自特征，不能来自模板查表；Phase 4 的"模板级贝叶斯更新"在新包上基本是冷启动。
6. `recycling_shaped` 角色几乎全是 anvil 经验等级伪影；真正的回收识别靠模型。
7. 与储物块成环的贵重物品（nether_star ↔ block）按封闭环规则定价为可采集物，`value_source="cycle"` 已标出，44 个（fom）。
8. 值只在包内可比（atm10-lite `atm_star_block_9x` 达 2.7e17，算术正确）；跨包特征已做包中心化。
9. 位置角色猜测随语料变化（`acacia` 在 fom 是 material、在先验里是 form，因某些 mod 写 `planks_oak`），只影响四元组槽位标签，不影响模板折叠。
10. `chain` tier 假定 ok 链背书其全部 ~60 步，权重 0.5 是估计；7/16 审计发现因格式无法表达（"同时用了路线和替代"）未成对。
11. ~~**atm10 15% 目标 `BUDGET_TRUNCATED`**~~ **（Phase 5 已复核并更正：是 `SELF_DEPENDENT`，不是预算）**。逐目标查 17/110 个不完整的 atm10 计划，每个各带一行 unresolved，原因全部是自依赖，展开数 264 远低于 512 的额度；默认设置下三种预算形状都 **0 截断**。真正的问题是：settle-order 规则让往返在构造上不可能，但当账本例外触发时**自依赖仍然可能**。是否真的不可达、还是账本规则的漏洞，未查。另一个相关数字：`budget_base` 实际控制的是"多久付一次锥体行走的钱"（atm10 上 512 → 64 让中位耗时 6.3 倍劣化而截断率不变），`cone_stats` 每次约 96 ms、占十目标 profile 的 41%，Java 侧要按 `(root, policy)` 缓存。
12. **`raw_nonleaf` 均值 1.43 应为 0**：atm10-lite 9,823 个物品中 1,012 个被候选策略孤立（策略关闭时 248 个）——junk 头移除了路线而没有替代；Java 侧唯一生产者保护之外还需要"被排除后无可用生产者则放行"的兜底。
13. **金标格式记录的是"对"不是"可接受集合"**：4 个绕过案例中 3 个走了真正更便宜的第三生产者却计为未赢；下一轮审计应记录可接受集合。
14. **反馈把 `junk_in_path_wl` 抬高 16%**（0.0219 → 0.0255，19 次点击后）：跟随 round-006 人工审计就会走进规则重放（builtin rules v3）判为 junk 的路线。两个标签源在这些路线上不一致，从本层无法判定谁对。Java 保留 builtin 规则作第二道网会压住现象而不解决分歧。
15. **一次 ban 可能排除邻域 46 条中的 45 条**：设计上的界（邻居只有在模型本来 p > 0.42 时才越阈）处处成立，但用户未必预期。缓解是 UI 在提交前展示 `ban()` 已经返回的 `touched` 列表；另一条路是调小 `SPREAD_PER`，代价是泛化。
16. **跨包共享排序器 delta 会丢一个审计案例**（留一法下 lost-world mossy_cobblestone 从 preferred 退成 neither），按包的 delta 不会。共享换来迁移、付出特异性；`FeedbackStore.merge` 就是开关。
17. （已修）`pair_features.py` 曾按导出目录名而非 slug 标记 pack，5 个包的叶子聚合列在训练时被静默 NaN；已修复并重训，指标基本不变（`REPORT.md` Phase 0c-2 "v2 re-run" 节，旧指标在 `out/models/_pre-packkey-fix/`）。

---

## 9. 验收门与验证方法

Phase 0（本层）：见 §1 表与 §5.5。验证方法一律是重跑测试 + 亲自查产物（`.venv/bin/python tests/test_phase0*.py`；抽查 parquet；对着原始 JSON grep 核实数据声明）。

原型层已达成的数字即 Java 实现的回归基线（`./run_phase3.sh` 可随时重跑）：A/B 3,301 目标上 actions 中位 12、depth 5、round trips 0、incomplete 0.005；审计 8/13；atm10 19 ms 中位。Java 的 `HierarchicalPlanner` 在同一目标集上不得劣于这些数（用 `ChainExportMain` 导出后按 `eval_planner.py` 的指标定义比对）。Phase 4 基线：无反馈计划逐字节不变；一次 ban 使同族全部越阈；一次 choose 翻转 white_dye；更新 p95 ≤ 2.5 ms（atm10）。Phase 5 基线：atm10 深锥 29 ms 中位 / 320 ms p95；建图期增量 ≤ 2.3 s；共享 memo 3.4–49×；默认预算零截断。

Phase 1–5（接手人）：

| 阶段 | 门 |
| --- | --- |
| P1 本体/经济 Java | `:Mod:compileJava` + `:Mod:test` 绿（基线 1 项既有失败除外）；fom 特征与 `golden-sample.json` 平价；atm10 建图期额外 ≤ 3–5 s，二次加载走磁盘缓存 |
| P2 质量模型接线 | main 中无字符串规则/词表/Bayes/Cascade；`assets/calculator_cirrus/quality/*.json` 加载；唯一生产者永不被排除 |
| P3 分层规划器 | `ChainExportMain` A/B（fom、farmers-colonies、create-oneblock、lost-world、technical-electrical）：round-006 五案例改选、junk-in-path 下降、材料放大/tier 逆行下降、`RoutingPlanReverseVerifier` 通过、耗时不劣化 |
| P4 反馈 | have/ban/prefer/choose 闭式更新毫秒级（原型 atm10 p95 ≤ 3.7 ms）；模板级泛化有测试；**无反馈时计划与 Phase 3 逐字节相同**（原型用 50 目标快照断言，Java 侧用同样的手法）；持久化往返 + 版本戳拒绝 |
| P5 速度 | `RecipeRoutingScaleBenchmark` + atm10/minecolonies 深锥计时；建图期增量 ≤ 3–5 s（原型"暖建 − CSR" atm10 2.03 s）；二次加载走磁盘缓存（atm10 23.79 MB/catalog）；跨请求共享定点默认开、计划不变 |

---

## 10. 文件索引

- 计划：`~/Downloads/plan.md/plan (1).md`（frontmatter todos 是进度跟踪）
- 本层：`CalculatorCirrus/experiments/quality/{DESIGN.md, REPORT.md, HANDOVER.md, JAVA-DESIGN.md, *.py, tests/, run_phase*.sh, requirements.txt}`；脚本：`load_exports.py` `ontology.py` `economy.py` `labels.py` `features.py` `pair_features.py` `train_eval.py` `export_model.py` `predict_reference.py` `planner.py` `eval_planner.py` `feedback.py` `bench_planner.py`
- 产物：`cache/<slug>/`、`out/<slug>/`、`out/<slug>/economy/`、`out/<slug>/planner/`、`out/<slug>/feedback/`、`out/labels/`、`out/features/`、`out/models/`、`out/planner-eval/`、`out/planner-bench/`、`out/token-classes.json`、`out/onboarding.json`、`out/economy/`
- 数据：`Planner-Final/exports/`（+ `exports.zip`）、`Planner-Final/experiments/rulehits-v3/`、`Planner-Final/experiments/svm/{data,labels}/`、`CalculatorCirrus/chain-review/`、`Planner-Final/chain-exports/`
- 早先研究（勿重开的方向见其 README）：`Planner-Final/experiments/{svm,generalization,naive-bayes*}/`、`Planner-Final/recipe-experiment-kit/work/{e2,e7}/`
- Java 离线工具：`Mod/src/test/java/dev/vfyjxf/cirrus/recipe/chainexport/{ExportPackLoader,ChainExportMain,BayesExperimentMain,...}.java`；`CalculatorCirrus/CHAIN-REVIEW.md`、`Mod/docs/RULES.md`
