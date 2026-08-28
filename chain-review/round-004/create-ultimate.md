# create-ultimate batch-004 审查报告
- pack: create-ultimate
- 批次: batch-004（written=97；incomplete 37 / giant 24 / colossal 15 / long 13 / short 7 / medium 1）
- 审查链数: 97（全量，因 <100 配额；优先 incomplete/colossal/giant，已逐条过 30+ 条 incomplete + 抽样 complete 头部）
- 发现: critical 0 / major 2 / minor 0
- 已知规划器级问题（链级一句话，不单列发现）：`|+N` id 后缀/any-of 坍缩污染、`#wool→black_wool`/`#planks→warped_planks` 展开、RANK/BUDGET 截断致原版常见物与 mod 物 unresolved。

## 发现

### F-create-ultimate-001 | chain=0048,0068（及 0002/0003/0009/0012/0013/0015/0020/0022/0023/0025/0027/0028/0030/0031/0035/0038/0039/0041/0043/0049/0051/0052/0055/0056/0059/0063/0065/0066/0067/0069/0078/0092/0095 等） | severity=major | category=loop-or-cycle
- 位置: `dndesires:crafting/lapis_lazuli_shard_from_lapis_lazuli` 与 `dndesires:crafting/lapis_lazuli_from_lapis_lazuli_shard`（互为逆；CHAIN 0048 的 A38→A51、CHAIN 0068 的 A16→A23）。同族 `dndesires:crafting/diamond_shard_from_diamond` ⇌ `dndesires:crafting/diamond_from_diamond`（各见 1 次）。
- 问题: 1 `lapis_lazuli` = 4 `lapis_lazuli_shard` 的**完全可逆**存储对，被规划器以 `loop=allowBoundedAmplification` 反复 A↔B 往返（如 0048 A38 10 lapis→40 shard，A39 40 shard→10 lapis，循环到 A51），**无净产出**。全批 686 处出现（colossal 176 / incomplete 494 / long 12 / giant 4），连“complete”链（0006 等 cardboard）也被污染。
- 原因: `pattern/storage_ladder` 规则未覆盖 `dndesires:*` 命名空间，且这些行的 EXECUTE 均**无 `[rule-tag: ...]`**（与同文件内已标注的 `pattern/storage_ladder` bone/lapis_block 循环形成对照），故无净产出往返环未被收敛。grep 确认所有 `dndesires:*_shard` 的消费者只有其逆配方 `dndesires:crafting/*_from_*_shard`，**无任何非 dndesires 配方消耗 shard** → 纯 no-op 环。
- 建议: 将 `dndesires:crafting/.*_shard` 加入 `pattern/storage_ladder`（或标记 converge 为 no-net-output 往返环，排除其放大）。

### F-create-ultimate-002 | chain=0002（A55–A68，及 incomplete 内多链） | severity=major | category=loop-or-cycle
- 位置: `cgs:raw_lead_block_from_raw_lead` ⇌ `cgs:raw_lead`（9:1 可逆；CHAIN 0002 A55 9 raw_lead→1 block，A56 1 block→9 raw_lead，循环至 A68）。
- 问题: 标准 9:1 原矿存储块对，被反复往返放大，**无净产出**。incomplete 文件内 204 处（raw_lead 108 + raw_lead_block 64 + `_block_from_raw_lead` 32），且 `cgs:raw_lead_block` 的所有消费者只有逆配方 `cgs:raw_lead`（grep 验证无外部消耗）→ 纯 no-op 环。
- 原因: `pattern/storage_ladder` 仅覆盖 `minecraft:` 与部分 mod 的 `raw_*_block`/`*_block_from_*`，**未泛化到 `cgs:` 命名空间**（对照同批 CHAIN 0009 中 `minecraft:raw_copper_block`⇌`raw_copper` 已被正确标注 storage_ladder）。`cgs:raw_lead_block_from_raw_lead` 行无 `[rule-tag: ...]`。
- 建议: 将 `cgs:raw_[a-z]+_block_from_raw_[a-z]+` 及其逆 `cgs:raw_[a-z]+` 加入 `pattern/storage_ladder`。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0002 white_anti_radiation_helmet | incomplete | bad | 同时含 dndesires shard 环 + cgs raw_lead 9:1 环（F-001/F-002），叠 `brown_cloth|+15` 未解（|+N 已知） |
| 0005 engine_turbocharger | incomplete | questionable | depth=93 全由 `polished_cut_andesite_stairs|+14` 未解根驱动 stonecutting 交换树（|+N 已知） |
| 0030 / 0095 pilot | incomplete | questionable | `polished/scoria ...|+14` 未解根驱动 30 级 Create stonecutting 交换树（|+N 已知，pattern/exchange 已标但根泄漏） |
| 0041 / 0043 umber_brick_wall | incomplete | questionable | createdeco umber 砖变体环，已标 pattern/exchange；pending 因根 `+N` 泄漏 |
| 0048 gray_package_frogport | incomplete | bad | dndesires lapis shard 往返环 A38–A51（F-001）+ bone/lapis 循环（已标） |
| 0068 / 0069 banner | incomplete | bad | dndesires shard 环 A16–A23（F-001）+ `black_wool|+14`/`warped_planks|+14` 未解（|+N 已知） |
| 0009 yoke_module | incomplete | questionable | `raw_copper_block`⇌`raw_copper` 已标 storage_ladder；`cgs:steel_ingot|+3` 未解（|+N/截断已知） |
| 0023 velvet / 0051/0056/0059 cushion | incomplete | questionable | `beetroot_crate`⇌`beetroot` 已标 storage_ladder 但 9× 往返取原版作物；recolor 已标 |
| 0006/0007 cardboard, 0014/0029/.../0046 混凝土/陶瓦 | complete | ok | 正常；bone/lapis 循环已标 storage_ladder，无新问题 |
| 其余 complete（0000/0001/0004/0008/0010/0011/0016–0019/0021/0024/0026/0032–0037/0044–0047/0050/0053/0054/0057/0058/0060/0061/0062/0064/0070–0096） | complete | ok | 正常，仅含已标 storage_ladder/recolor/exchange 循环 |
| 上述其余 incomplete（0003/0012/0013/0015/0020/0022/0025/0027/0028/0031/0035/0038/0039/0049/0052/0055/0056/0059/0063/0065/0066/0067/0078/0092 等） | incomplete | questionable | 均由 `|+N` 未解根 / any-of 坍缩 + 已标 storage_ladder/recolor 循环构成，含 F-001 的 dndesires shard 环者判 bad |

## 规则建议（给汇总者）
- targetRegex: 无新增目标级排除（目标本身合法，问题在路径内的环）。
- recipeRegex（新增收敛，证据条数）:
  - `dndesires:crafting/.*_shard` —— 收敛 dndesires lapis/diamond shard ⇌ gem 可逆环；证据 686 处（colossal 176 / incomplete 494 / long 12 / giant 4），无外部消费者，纯 no-op 往返。
  - `cgs:raw_[a-z]+_block_from_raw_[a-z]+` 及逆 `cgs:raw_[a-z]+` —— 收敛 cgs 原矿 9:1 存储环；证据 204 处（incomplete），无外部消费者。
- builtin 候选:
  - `pattern/storage_ladder` 应泛化到 `cgs:` 等命名空间的 `raw_*_block`/`*_block_from_*`（仅 minecraft 被覆盖，mod 原矿块遗漏，F-002）。
  - `|+|N` id 后缀污染为**已知规划器级问题**（round-4 已列）：全批贯穿 `create:framed_glass|+4`(216)、`minecraft:black_wool|+14`(204)、`create:polished_cut_andesite_stairs|+14`(176)、`cgs:steel_ingot|+3`(38)、`dndecor:cut_stone|+14`(48) 等，是 30–93 级交换树爆炸的根因，建议作为数据清洗在规划器层剥离 `\|[0-9]+$` 后缀后再入图（非本轮 review filter，标注为已知）。
- 无新模式时: 否——本轮发现 2 个**未被既有排除覆盖**的新环族（dndesires shard、cgs raw_lead 存储），其余均为已知 `|+N`/截断/已标模式。
