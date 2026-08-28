# LuckyBlockChallenge batch-005 审查报告

- pack: LuckyBlockChallenge
- 批次: batch-005（written=59；类别分布 short 32 / long 11 / giant 11 / medium 3 / incomplete 2）
- 审查链数: 59（全量；incomplete 2 / complete 57；所有文件均 <120KB，全部读完，无抽样）
- 发现: critical 0 / major 0 / minor 1

## 发现

### F-LBC-1 | chain=0036 | severity=minor | category=loop-or-cycle（实为 storage_ladder 漏标）

- 位置: `chains-long-001.txt` 行 97–134；CHAIN 0036，target=`minecraft:dried_kelp_block`
- 问题: 本批唯一一条 `builtin-rule-tags` 为空的链（其余 58 条全部带 `pattern/storage_ladder` / `pattern/exchange` / `pattern/recolor`）。`minecraft:dried_kelp_block ⇄ minecraft:dried_kelp` 是 9:1↔1:9 可逆压缩族（A1: block→9 kelp；A2: 9 kelp→block；重复至 A8），整条链无净产出，仅把 2 个 `dried_kelp_block` 原材料拆解再重组回自身。目标本身就是 `dried_kelp_block`，本应作为原材料直接 base 或至多 2 步，却被放大成 depth=8 / actions=9 / recipes=2 的 "long" 链（RAW 仅 `dried_kelp_block x2`）。
- 原因: `pattern/storage_ladder` 规则未覆盖食物类存储块 `dried_kelp_block`/`dried_kelp`（与已覆盖的 FD rice/straw/canvas/crate、wheat 等同属食物 9:1 压缩族），属已知族的漏标；规划器未识别为存储阶梯，于是作为普通放大环保留。
- 建议: 将 `minecraft:dried_kelp_block|dried_kelp`（及同类食物 9:1 块，如 `slime_block`/`slime`、`hay_block` 等，建议一并核查是否同样漏标）纳入 `storage_ladder` 规则；标后即可与 gold/diamond/raw_copper 等族一同从图中折叠，该链应坍缩为短链/原始材料。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0036 | loop-or-cycle | questionable | dried_kelp 存储阶梯漏标，环无净产出（minor） |
| 0001 | unresolved-expected-item (planner) | questionable | `black_bed\|+14` RANK_TRUNCATED 未解析，属 any-of 基底坍缩(#bed→black_bed)+`\|+N` 污染，规划器级已知 |
| 0048 | unresolved-expected-item (planner) | questionable | `black_wool\|+14` RANK_TRUNCATED，同上 |
| 0000,0002–0033,0034–0037,0040,0044–0047,0049,0056,0041,0050,0057,0003,0038–0043,0051–0058 | ok / pattern | ok | 均被 storage_ladder / exchange / recolor / stonecutting 覆盖或简短合法链，无新问题 |

## 规则建议（给汇总者）

- targetRegex: 无新增（本批目标均为合法物品，无 fake / 展示页目标）
- recipeRegex: `minecraft:dried_kelp(_block)?` —— 标记收敛为 `storage_ladder` 压缩族；证据 1 条（CHAIN 0036）。建议同时核查 `slime_block`/`slime`、`hay_block` 等食物 9:1 块是否同类漏标。
- builtin 候选: 不是新族，而是现有 `pattern/storage_ladder` 的覆盖缺口——建议在匹配集中补食物类 9:1 存储块。标签建议复用 `pattern/storage_ladder`（junk/duplicate 不适用）。
- 已知规划器级问题（一句话，仅链级提及）: `|+N` id 后缀污染（`acacia_wood|+3`、`cherry_planks|+10`、`black_bed|+14`、`black_wool|+14`、`green_wool|+15`、`cobbled_deepslate|+2`、`stripped_crimson_hyphae|+39`）与 any-of 基底坍缩（#wool→black_wool、#bed→black_bed）导致 0001/0048 的 UNRESOLVED（`reason=RANK_TRUNCATED`）。

## 收敛结论

本批已高度收敛：59 条链全部为原版 `minecraft:` 命名空间的 crafting / smelting / stonecutting / blast_furnace 配方，定向 grep 确认无 `jei:` 展示页、无 free-resource（cobblegen/laser drill）、无 generation-abuse、无 quest/trade/villager，且**无任何 lucky-block-mod 专属配方出现**（该包模组内容应已被 decompress 黑名单 / 无生产者过滤排除）。已固化标签（storage_ladder / exchange / recolor）覆盖了全部放大环、染色彩环、盔甲纹章复制环。

唯一新模式 = `dried_kelp_block` 存储阶梯漏标（minor），属现有规则覆盖缺口而非全新问题族。无需新增排除规则；仅建议补 `storage_ladder` 匹配即可让该包彻底收敛。
