# mythcrafts batch-003 审查报告
- pack: mythcrafts
- 批次: batch-003（written=35；类别分布：incomplete 9 / long 11 / giant 8 / colossal 7）
- 审查链数: 35（全量；incomplete 9 / complete 26；大文件抽样按口径，colossal/giant/incomplete/long 均逐链过）
- 发现: critical 0 / major 2 / minor 0

## 发现

### F-mythcrafts-1 | chain=0001,0006,0031,0034（及 0008/0030/0023/0005/0010/0017/0018/0019/0020/0021/0022/0025/0028/0029/0033 等 raw_kinds 80–943 的 18 链） | severity=major | category=tag-expansion-overreach
- 位置: 各链 `[RAW]` 行（典型：链0001 行286、链0006 行529、链0031 行1191、链0034 行1606、链0008 行~158、链0030 行~1040、链0023 行~2198）
- 问题: 接受 tag 成分的配方被把 tag 全量枚举为 RAW 物料。链0001 的 RAW 含 **519 条 plank 条目，来自 14 个 mod**（beachparty、biomesoplenty、biomeswevegone、block_factorys_bosses、cataclysm、chipped、…），但链内仅实际消耗 `minecraft:oak_planks`（A26–A28，且为 `minecraft:oak_planks|+523`）。链0006 RAW 同时含数百 plank/log/fence；链0031/0034 RAW 含 519 plank + 15 wool（raw_kinds=943）。链0008/0030/0023（oak_barn_trapdoor / vinery:window / lectern）RAW 分别为 268/281/82 条，且 mod 分布签名与 planks 链高度一致（biomeswevegone、biomesoplenty、twilightforest、ecologics、quark、vinery、aether、deeperdarker、beachparty、meadow…），为同一类 wood/log/planks/fence tag 的全量展开。index.tsv 中 18 条链 raw_kinds 落在 80–943，远超目标物品所需。
- 原因: 规划器对 `#minecraft:planks`/`#minecraft:logs`/`#minecraft:wooden_fences`（及等价 mod tag）接受任意成员，遂把 tag 全量列为可选 RAW，并以 `|+N` 后缀标注所选成员（如 `minecraft:oak_planks|+523`、`minecraft:oak_fence|+120`、`minecraft:oak_log|+3`、`minecraft:black_wool|+14`）。该枚举未被任何 rule-tag 覆盖，并直接造成预算/排名在 tag 成员上耗尽：`minecraft:oak_log|+3`（链0006）、`minecraft:black_wool|+14`（链0031/0034）被标 `BUDGET/RANK_TRUNCATED`，`minecraft:cobblestone`（链0006）被 `BUDGET_TRUNCATED`。
- 建议: 对 `#minecraft:planks`/`#minecraft:logs`/`#minecraft:wooden_fences` 等常见 tag 的展开做去重收敛（仅保留链内实际消费的成员，或收敛为代表成员），避免数百 RAW 污染；并将 `oak_log|+3`、`black_wool|+14` 这类截断归因为 tag 展开副作用而非真实缺料。

### F-mythcrafts-2 | chain=0007(A67–A68),0009(A5–A12),0012(A2–A8),0014,0015（及 0004 flint 环） | severity=major | category=loop-or-cycle
- 位置: `mythsandlegends:infernum_block ↔ mythsandlegends:infernum_ingot`（无标签）；链0004 `supplementaries:flint_block ↔ minecraft:flint`（A1–A8）
- 问题: 9:1 块↔锭无净产出往返环，未带任何 rule-tag。例：链0007 A67 `in: infernum_ingot x9 → out: infernum_block x1`，A68 `in: infernum_block x1 → out: infernum_ingot x9`，净零；此环仅把 RAW 的 `infernum_ingot` 块化再拆回喂入锻造（A73/A74），属无谓块化往返。同链 `diamond_block↔diamond`（A2–A17）却正确带 `pattern/storage_ladder`。链0004 的 `flint_block→flint`（uncraft 标 `intent/recycling`，craft 未标）构成 flint→block→flint 同样的无标签 no-op 环（A1–A8）。链0009/0012/0014/0015 均为同一 `infernum_block↔ingot` 环。
- 原因: `pattern/storage_ladder` 分类器只识别原版 `*_block↔*` 1:9 对（diamond/redstone/raw_iron/raw_gold…），遗漏 mod 9:1 块（infernum_block/ingot、supplementaries:flint_block/flint）。这些本应被收敛为 storage_ladder 或 no-op 环排除，却以未标签形态进入链。
- 建议: 在 builtin `pattern/storage_ladder` 增加 mod 9:1 块↔锭/粒配对识别（infernum_*、flint_block），或作为 no-op 环排除；否则这些链被标 complete 却含无谓块化往返。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | 仅 apple/carrot 误标 UNRESOLVED（原版截断，已知） |
| 0001 | incomplete | bad | tag-expansion：519 plank RAW 仅用 oak；raw_iron UNRESOLVED |
| 0002 | incomplete | bad | raw_kinds=531 tag-expansion；apple/carrot UNRESOLVED |
| 0003 | giant | ok | oak_barn_trapdoor，raw_kinds=7 正常 |
| 0004 | long | bad | flint_block↔flint 无标签 no-op 环（F2） |
| 0005 | long | bad | raw_kinds=526 tag-expansion |
| 0006 | incomplete | bad | tag-expansion(533)；oak_log|+3 / cobblestone 误标 UNRESOLVED |
| 0007 | colossal | bad | infernum_block↔ingot 无标签 no-op 环（F2） |
| 0008 | giant | bad | raw_kinds=268 tag-expansion（多 mod 木系签名） |
| 0009 | giant | bad | infernum_block↔ingot 无标签 no-op 环（F2） |
| 0010 | long | bad | raw_kinds=528 tag-expansion |
| 0011 | giant | ok | oak_bamboo_trapdoor，raw_kinds=5 正常 |
| 0012 | colossal | bad | infernum 环 + 200 actions/351 crafts（allowBoundedAmplification 叠加 tagged 放大） |
| 0013 | giant | ok | oak_bamboo_door，raw_kinds=5 正常 |
| 0014 | colossal | bad | infernum_block↔ingot 无标签 no-op 环（F2） |
| 0015 | colossal | bad | infernum_block↔ingot 无标签 no-op 环（F2） |
| 0016 | giant | ok | nether_brick_arrow_slit，raw_kinds=1 正常 |
| 0017 | long | bad | raw_kinds=816 tag-expansion |
| 0018 | long | bad | raw_kinds=529 tag-expansion |
| 0019 | incomplete | bad | raw_kinds=600 tag-expansion |
| 0020 | giant | bad | raw_kinds=600 tag-expansion |
| 0021 | long | bad | raw_kinds=600 tag-expansion |
| 0022 | long | bad | raw_kinds=576 tag-expansion |
| 0023 | colossal | questionable | raw_kinds=80 tag-expansion（lectern 木系 tag 展开） |
| 0024 | colossal | ok | stack_upgrade conversion，纯 tagged storage_ladder 空转，raw_kinds=0 |
| 0025 | long | bad | raw_kinds=795 tag-expansion |
| 0026 | colossal | ok | stack_upgrade conversion，纯 tagged storage_ladder 空转 |
| 0027 | incomplete | ok | raw_kinds=1，仅 raw_iron 误标 UNRESOLVED（原版截断，已知） |
| 0028 | long | bad | raw_kinds=529 tag-expansion |
| 0029 | long | bad | raw_kinds=529 tag-expansion |
| 0030 | giant | bad | raw_kinds=280 tag-expansion（多 mod 木系签名） |
| 0031 | incomplete | bad | tag-expansion(943)；black_wool|+14 误标 UNRESOLVED |
| 0032 | incomplete | bad | tag-expansion(943)；black_wool|+14 误标 UNRESOLVED |
| 0033 | long | bad | raw_kinds=529 tag-expansion |
| 0034 | incomplete | bad | tag-expansion(529)；black_wool|+14 误标 UNRESOLVED |

## 规则建议（给汇总者）
- targetRegex: `.*_conversion$` —— 排除 sophisticatedbackpacks/sophisticatedstorage 的 `stack_upgrade_*_conversion` 等 tier 转换元物品目标（证据：链0024/0026，内容纯为 tagged storage_ladder 空转，无真实生产）；证据条数 2。
- recipeRegex: `mythsandlegends:infernum_(block|ingot)|supplementaries:flint_block` —— 收敛为 storage_ladder 9:1 配对（证据：链0007/0009/0012/0014/0015 + 0004）；证据条数 6。
- builtin 候选: 扩展 `pattern/storage_ladder` 覆盖 mod 9:1 块↔锭（infernum、flint），当前仅原版对被判；建议标签 storage_ladder；证据同上。另：对 `#minecraft:planks`/`#minecraft:logs`/`#minecraft:wooden_fences` 展开加 tag 去重收敛规则（tag-expansion-overreach），证据：链0001/0006/0031/0034/0008/0030/0023 等。
- 已知规划器级问题（链级一句话，不展开为发现）：`|+N` id 后缀污染（oak_log|+3、oak_planks|+523、oak_fence|+120、black_wool|+14）；原版常见物预算截断（cobblestone/apple/carrot/raw_iron 因 CASCADE/BUDGET_TRUNCATED 误标 UNRESOLVED，如链0000/0001/0002/0006/0027）；32 位溢出哨兵、背包升级物品被当机器、smithing_trim 破映射、无净产出往返环机制本身（本批 colossal 链如 0012 由 allowBoundedAmplification 叠加 tagged storage_ladder/exchange 放大，属策略层面不单独报）。
