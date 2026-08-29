# 规则系统 v3：统一规则语言 + 分层加权计分

本文件是规则系统的权威参考。目标读者：给规则文件加规则、调计分权重、或排查"planner 为什么选了这条路径"的人。

## 总览

一套规则语言服务两个求值域，一个计分模型决定候选排序：

```
catalog 域（建图期，每配方一次）          candidate 域（规划期，每候选一次）
  builtin_rules.json (v3)                   RuleProfile 的 score model + 子句
  结构化条件 ──> tag / hint 动作            条件树 ──> penalize/boost/reject
        │                                            │
        ▼                                            ▼
   TagIndex / SourcePolicy（剔除 junk）        FeatureVector ─> ScoreModel ─> 层和 ─> 排序
```

- 旧的正则专属分类规则（`classification/builtin_rules.json` v2）已删除，由
  `assets/calculator_cirrus/rules/builtin_rules.json`（v3）取代。
- 旧的路由子句 schema（`RuleClauseSpec`/`RuleConditionSpec`/`RuleEffectSpec`）已删除，
  用户规则统一为 candidate 阶段的 `RuleSpec`。
- 旧 5 轴字典序 + 手调魔法罚分（1e6/1e5/5e4/1e3/×1000/×4）全部消失，语义原样搬进
  `DefaultScoreModels` 的显式权重与乘数。

## 一、规则文件（v3 文档）

```json
{
  "version": 3,
  "rules": [
    {
      "id": "cirrus:storage_compression_shape",
      "label": "9+:1 / 1:9 压缩往返（按形状识别）",
      "stage": "catalog",
      "enabled": true,
      "when": { "kind": "all_of", "conditions": [ ... ] },
      "then": [
        { "do": "tag", "tag": "cirrus:pattern/storage_ladder" },
        { "do": "hint", "policy": "discouraged" }
      ]
    }
  ]
}
```

- `stage`：`catalog`（分类/排除/hint）或 `candidate`（打分/否决）。必须与动作种类一致，加载期校验。
- `when`：条件树。`all_of` / `any_of` / `not` 组合 + 叶子。
- `then`：动作列表，按序执行。

### 条件叶子（`kind` 分发）

| kind | 字段 | 说明 |
|---|---|---|
| `system` / `recipe_id` / `recipe_mod` / `machine` | `op`, `values` | id 匹配。`op ∈ in/prefix/suffix/contains/glob/regex`。**优先用结构化 op**；`regex` 是显式兜底（子串 `find` 语义），label 里写清楚为什么 |
| `material` | `channel`（`input/output/any`）、`match`（token 列表，可省）、`amount`（可选 `{op, value}`）、`all_slots` | 按槽位匹配具体 id / 声明 tag。token：`item:x`、`fluid:x`、裸 id、`#tag`、`tag:tag`。省略 `match` 只给 `amount` = 任意槽位按数量过滤（形状规则用） |
| `slot_count` | `channel`, `op`, `value` | 槽位数比较（`any` = 输入+输出） |
| `id_overlap` | — | 某输出 id 同时出现在输入 id 中（chisel 互换/自修复） |
| `chance` | `channel`, `op`, `value` | 槽位概率比较（`< 1` = 概率产出） |
| `has_concrete_input` | `value` | 是否存在具象输入（`false` + 有输出 = free-resource） |
| `classification_tag` | `tag` | 配方已带该分类标签 |
| `flag` / `scalar` / `hint` | 见下 | **candidate 域专用**：`flag ∈ cycleCandidate/activeDemandCycle/intraLoopConversion/roundTrip`；`scalar ∈ depth/craftTimes/subtreeCost/amplificationRisk/contextualPenalty`（op+value）；`hint ∈ normal/discouraged/lastResort/...` |

规则在不适用的域求值时，域专属叶子返回 `false`（不报错）。

### 动作（`do` 分发）

| 动作 | 阶段 | 说明 |
|---|---|---|
| `tag` | catalog | 打分类标签；`quality/junk`、`quality/duplicate` 会被默认 source policy 剔除 |
| `exclude` / `include` | catalog | 直接剔除/翻案（文档序 last-wins）；内置文件目前用标签式剔除以保留 UI 覆盖能力 |
| `hint` | catalog | 设路由 hint：`discouraged`（+1000 权重）或 `lastResort`（+50000 并默认不作候选） |
| `penalize` / `boost` | candidate | 对某分量加/减分：`{ "do": "penalize", "component": "contextual", "amount": 500, "scale": "coverage_shortage" }`。component 必须在计分模型中声明，加载期校验（写错名字直接报错，不再静默丢弃） |
| `reject` | candidate | 硬否决，`reason` 出现在失败诊断里 |

### 从 v2 正则迁移的写法

- `systemPattern: "(?i)(jei:|jei__)category/ftbquests/"` → `system contains "category/ftbquests/"`（contains 天然兼容测试镜像命名空间）
- 巨型压缩枚举正则 → `slot_count(input)==1 && slot_count(output)==1 && material(input, amount>=9)`（形状）+ 少量家族前缀
- 确需正则的（词边界交替、锚定段），保留 `regex` op 并在 label 说明

## 二、计分模型（分层加权）

`RuleProfile` 携带一个 `ScoreModel`（默认见 `DefaultScoreModels`，可经 `RuleProfileSpec.score_model` 覆盖）：

```
rank(c1, c2) = 字典序比较 [tierScore(0), tierScore(1), ...]   （低层绝对压制高层，低分优）
tierScore(t) = Σ component.weight × component.value
```

默认三层（`safety → cost → quality`）：

| 层 | 分量 | 特征 | 权重 | 旧语义 |
|---|---|---|---|---|
| safety | cycle | activeDemandCycle | 1.0 | 旧 +1,000,000 到成本轴 |
| safety | roundTrip | roundTrip | 1.0 | 旧 +100,000 |
| safety | intraLoop | intraLoopConversion | 1.0 | 旧 +100,000 |
| cost | subtreeCost | subtreeCost（base） | 1.0 | 旧成本轴 |
| cost | advisoryExtra | advisoryCostExcess（= full − base） | 1.0 | 旧 ×1000/×4 乘法 |
| cost | contextual | contextualPenalty | 1.0 | 旧 +5000/+2000 |
| quality | craft | craftTimes | 1.0 | 旧 craft 轴 |
| quality | hintDiscouraged | hintDiscouraged | 1000 | 旧 +1000 |
| quality | hintLastResort | hintLastResort | 50000 | 旧 +50000 |
| quality | hintPenalty | hintPenalty | 1.0 | 旧 hints.penalty() |
| quality | inventoryShortage | inventoryShortage | 1.0 | 旧第 3 轴 |
| quality | depth | depth | 1.0（assuredBounded 0.8） | 旧第 4 轴 |
| quality | amplification | amplificationRisk | 1.0（assuredBounded 1.2） | 旧第 5 轴 |

关键语义变化（有意为之）：

1. **循环绝对压制成本**：以前靠 1e6 罚分量级"大概压过"成本（unsourced 成本也是 1e6/unit，会打平）；现在是层序声明，无歧义。
2. **quality 层内是加权和**，不再是 craft→shortage→depth→amp 的逐轴字典序。默认权重下行为接近旧版，但允许层内权衡。
3. **咨询乘法变加法**：成本 oracle 跑两遍（base 无乘数 / full 带乘数），`advisoryExtra = full − base`，cost 层求和恒等于旧值；乘数（generation 1000 / soft 4）显式写在模型里。
4. 探索近邻带（near-tie）：比较发生在 **cost 层**（含 subtreeCost 的层），cost 层之前的层必须完全打平——循环候选永不进近邻带。

### 候选规则动作示例

```json
{
  "id": "test:deep_penalty",
  "stage": "candidate",
  "when": { "kind": "scalar", "field": "depth", "op": "ge", "value": 8 },
  "then": [ { "do": "penalize", "component": "contextual", "amount": 500 } ]
}
```

## 三、代码地图

| 关注点 | 位置 |
|---|---|
| 规则语言（条件/动作/文档/引擎） | `api/recipe/rule/`（`RuleCondition`、`RuleAction`、`RuleSpec`、`RuleDocument`、`RuleEngine`、`RuleInput`/`CatalogRuleInput`） |
| 计分模型 | `api/recipe/rule/`（`ScoreModel`、`FeatureType`、`FeatureVector`）+ `api/recipe/routing/rule/DefaultScoreModels` |
| 分类管线 | `api/recipe/classify/`（`RecipeClassifier`、`UnifiedClassificationRule`、`ClassificationTags`、`structural/`） |
| hint 桥接 | `recipe/classify/ClassificationHeuristics`（统一 hint 动作 + 结构规则标签双通道，最严者生效） |
| planner 接线 | `recipe/routing/planner/StandardPlanner`（`applyModelScore` 是唯一"候选状态→分数"入口）、`PlannerRequestCaches`（base/full 双 oracle） |
| 候选子句编译 | `recipe/routing/rule/CandidateRuleClauses` + `CandidateRuleInput` |
| 规则文件 | `assets/calculator_cirrus/rules/builtin_rules.json` |

## 四、验证与调参

- 全量测试：`./gradlew :Mod:test`
- 链导出对比（调规则/权重后）：`bash chain-export.sh <pack>`，与 `chain-exports/<pack>/` 旧导出 diff（tag 计数、incomplete 率、fake-production 频次），差异需逐条归因。
- 调权重的入口：`DefaultScoreModels`（代码默认）或 `RuleProfileSpec` 序列化的 `score_model` 覆盖（未指定时用 preset 默认）。
