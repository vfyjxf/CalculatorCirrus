# 链批次审查口径（BATCH-REVIEW-BRIEF）

> 供 chain-review 递进循环中审查 `chain-exports/<pack>/batch-NNN/` 的 agent 使用。
> 工作流背景见 `../CHAIN-REVIEW.md`。本口径改编自 2026-08-29 全量 dump 审计（AUDIT_BRIEF）。

## 审查对象与读法

每批目录：`chains-<类别>-NNN.txt`（链全文）、`index.tsv`（链级索引：id/类别/flags/target/depth/actions/recipes/crafts/raw_kinds/pending/machines）、`report.txt`、`targets.tsv`、`manifest.txt`。

大包的 `chains-incomplete-*.txt` 可能几十 MB，**不要整读**。推荐手法：

```bash
wc -l chains-incomplete-001.txt                      # 规模
grep -n '^### CHAIN' chains-incomplete-001.txt | head # 链边界行号
awk '/^### CHAIN/{n++} n%4==1{p=1} p{print} /^$/{if(p){p=0}}' file  # 每 4 条抽 1 条
grep -c 'reason=BUDGET_TRUNCATED\|reason=RANK_TRUNCATED' file       # planner 放弃信号
grep -n 'crafts=[0-9]\{5,\}' index.tsv                # 数量失控链
```

小类别文件（trivial/short/medium/long/giant/colossal）通常几百 KB，可全读。
配额：每包至少过 100 条链（不足则全量）；优先 incomplete、giant/colossal、mega-wide。

## 已内置规则——不要重复报告

- 链头部 `builtin-rule-tags (already handled, do not re-report)` 与 EXECUTE 行尾
  `[rule-tag: ...]` 的步骤：模式已知（exchange/recolor/recycling 等）。对这些步骤
  唯一有效建议是"该模式是否应升级为排除"，其余不要再报。
- `+raw N` 叶子（包内无生产者）是正常原材料，不是问题。
- `loop=allowBoundedAmplification` 是声明策略：**有净产出**的放大不算问题；
  **无净产出的往返环**（如 compress↔decompress、A↔B 互逆 no-op 重复多次）仍是问题。
- incomplete 链存在 `[UNRESOLVED]` 本身是预期；只有未解析物**明显本可解析**
  （原版常见物品、链内别处能产）或由坏步骤**级联**出来才标记。

## jei: 命名空间澄清（防误报）

- `jei:category/<mod>/<机器类别>` 是真实机器配方系统，玩家可执行。**禁止**仅因
  `jei:` 前缀判 fake-production。
- `jei:/synthetic/<数字>` 是规划器合成 id，合法。
- 只有配方**语义**不是玩家可执行生产时才判 fake-production：任务奖励页、村民交易/
  市场展示、JER 掉落/生物/矿物展示页、纯信息页。判定时引用具体 id 并说明理由。

## 问题类别（category，固定字符串）

| category | 含义 |
|---|---|
| `fake-production` | 被执行的"配方"不是玩家可做的真实步骤（奖励/交易/展示页） |
| `free-resource` | 零物品输入却有物品产出被执行（cobblegen、laser drill、blockgen…） |
| `loop-or-cycle` | 无净产出的互逆往返 / no-op 环重复 |
| `amount-insanity` | 数量级失控（crafts 数百万、指数放大无净收益） |
| `impure-recipe` | 选了多杂产配方而存在更纯的直接配方（需证据） |
| `worse-alternative` | 明显更差路径（需在链文本/索引里见到更优者，否则降 minor suspected） |
| `silly-detour` | 有直接配方却绕远路（需证据） |
| `unresolved-expected-item` | UNRESOLVED 的物本应可生产（原版常见物、链内他支可产） |
| `unresolved-cascade` | UNRESOLVED 由上游假生产/坏步骤级联 |
| `tag-expansion-overreach` | tag any-of 展开出语义不符成员 |
| `generation-abuse` | 有真实链却依赖 generation 类配方 |
| `catalyst-misuse` | 催化剂被当消耗品重复计入需求 |
| `data-pollution` | 哨兵/乱码 id、toString 串进入链 |
| `machine-mismatch` | EXECUTE @ 机器与配方系统明显不符 |
| `other` | 以上都不是 |

## 严重度

- **critical**：fake-production / 有真实链却 free-resource / 明显可解析却 unresolved / 链整体不可用。
- **major**：loop-or-cycle、amount-insanity、impure、detour、tag 展开明显错误等影响路径质量。
- **minor**：可疑但证据不足、可辩护的怪象。

## 判定纪律

1. 每条发现必须引用证据：`CHAIN <id>` + 具体配方/物品 id + 行为描述。**禁止臆造**不存在的配方或替代路径。
2. worse-alternative / silly-detour 只在有实际证据时判 major；否则 minor + suspected。
3. 豁免：作为唯一生产者的 generation/循环配方；摘要截断本身。
4. 一条发现归一个主 category；没有问题的链在链级判定表里标 ok。
5. 时间花在找问题上，不为正常链写长篇分析。

## 输出文件格式（强制，每个 agent 写且只写一个文件）

```markdown
# <pack> batch-NNN 审查报告
- pack: ...
- 批次: batch-NNN（written、类别分布）
- 审查链数: N（incomplete X / complete Y；抽样说明）
- 发现: critical a / major b / minor c

## 发现
### F-<pack>-<序号> | chain=<CHAIN id> | severity=... | category=...
- 位置: <配方 sys:id / 物品 id / action 号>
- 问题: <哪里不合理，引用链内证据>
- 原因: <为什么不合理：规划器/分类器/数据层面根因推断>
- 建议: <一句话筛选或修复方向>

## 链级判定
| chain | category | verdict(ok/questionable/bad) | 一句话 |

## 规则建议（给汇总者）
- targetRegex: <正则> —— 排除哪些目标 + 证据条数
- recipeRegex: <正则> —— 标记收敛哪些配方族 + 证据条数
- builtin 候选: <跨包可能通用的模式 + 建议标签(junk/duplicate/pattern-xxx) + 证据>
- 无新模式时写"无"。
```

## 最终回复（紧凑 JSON）

```json
{"pack":"...","batch":"NNN","chains":N,"findings":{"critical":0,"major":0,"minor":0},
 "top":["chain <id>: 一句话问题（最多5条）"],"ruleSuggestions":["..."],"file":"<写的文件绝对路径>"}
```
