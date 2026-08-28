# All Of Create 2.1 batch-003 审查报告
- pack: All Of Create 2.1
- 批次: batch-003（written=79；类别分布：incomplete 52 / colossal 10 / medium 9 / long 6 / giant 2）
- 审查链数: 79（全量：27 条 complete 链全读 + 14 条 incomplete 链全读 + 其余 38 条 incomplete 经 grep 异常信号筛查：jei: 假生产、generation/free-resource、未标记循环、amount-insanity、|+N 污染）。无单条 crafts 达 5 位数，无 amount-insanity。
- 发现: critical 0 / major 2 / minor 2

## 发现
### F-aoce2-1 | chain=0011 | severity=minor | category=other
- 位置: target `simulated:contraption_diagram`（A75 合成 simulated:contraption_diagram，输入含 A72 simulated:physics_assembler）
- 问题: 目标物品与关键中间体使用 `simulated:` 命名空间。同批次该命名空间还出现在 0036/0078 的 `simulated:white_symmetric_sail`、0042 的 `simulated:spring`（grep 全文件共 ~8 处）。该命名空间不匹配任何已知 mod，极可能是规划器为桥接缺失真实配方而生成的合成/占位 id。若如此，整条链建立在非玩家可执行的"模拟"配方之上，目标 `simulated:contraption_diagram` 在游戏中实际不可合成——属于目标选择怪象。
- 原因: 规划器对无真实配方的物品生成 `simulated:` 占位 id，并把占位物当作合法目标/中间体纳入链。
- 建议: 先确认 `simulated` 是真实 mod 还是规划器合成命名空间；若属合成占位，导出期应剔除或显式标注 `simulated:*` 目标与中间体，避免产出假链。

### F-aoce2-2 | chain=0077 | severity=major | category=loop-or-cycle
- 位置: A1–A14（`create:sturdy_sheet` ↔ `create_compressed:sturdy_sheet_block`，9:1）、A15–A28（`create:sturdy_sheet` ↔ `create_ironworks:sturdy_sheet_block`，9:1）
- 问题: `sturdy_sheet` 与其方块形式之间的 9:1 压缩/解压被反复空转（约 28 步纯振荡 9→1→9…），无净产出。链头标注 `pattern/storage_ladder x2`，但本应被该规则覆盖的压缩/解压步骤绝大部分未标记，规划器因此持续空转放大。结果 depth=33、crafts=306、raw_kinds=1。
- 原因: `create_compressed:*` / `create_ironworks:*` 的 sturdy_sheet block↔item 转换未被归类到 `pattern/storage_ladder`（漏标）。
- 建议: 将 sturdy_sheet 相关 block↔item 压缩/解压配方纳入 storage_ladder 标记，消除空转循环。

### F-aoce2-3 | chain=0064 | severity=major | category=loop-or-cycle
- 位置: A1–A4…（`createmetallurgy:refractory_mortar` ↔ `refractory_mortar_ball`，4:1）
- 问题: `refractory_mortar` 与 `refractory_mortar_ball` 之间 4:1 互转反复空转（mortar→ball×4→mortar→…），无净产出；链头仅 `pattern/storage_ladder x1`，该循环未标记。导致 crafts=762、depth=52 的链体膨胀。
- 原因: createmetallurgy 的 refractory_mortar↔ball 压缩族未被归类 storage_ladder（漏标）。
- 建议: 将 createmetallurgy refractory_mortar block↔ball 转换纳入 storage_ladder 标记。

### F-aoce2-4 | chain=0009,0070 | severity=minor | category=loop-or-cycle
- 位置: 0009 A5–A6、A13–A14；0070 A68–A69（`dndesires:crafting/diamond_shard_from_diamond` ↔ `dndesires:crafting/diamond_from_diamond_shard`，1:4）
- 问题: 钻石↔钻石碎片（8→32→8）的 1:4 互转循环，无净产出，未带 storage_ladder 标签（dndesires 钻石族未标记）；规划器插入纯空转放大。
- 原因: dndesires 的 diamond_shard 压缩族类比 nugget 阶梯但未被归类 storage_ladder（漏标）。
- 建议: 将 dndesires diamond_shard block↔item 转换纳入 storage_ladder 标记。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0011 | incomplete | questionable | 目标 simulated:contraption_diagram 疑为合成占位命名空间（F-aoce2-1） |
| 0077 | incomplete | bad | sturdy_sheet 压缩族漏标，约28步空转循环（F-aoce2-2） |
| 0064 | incomplete | bad | refractory_mortar↔ball 漏标，空转放大 crafts=762（F-aoce2-3） |
| 0009 | incomplete | questionable | diamond_shard 漏标空转 + warden_upgrade_smithing_template 复制（smithing_trim 已知问题） |
| 0070 | incomplete | questionable | diamond_shard 漏标空转 + 大量 |+N 后缀污染 |
| 0033 | incomplete | questionable | createdeco 石切环(已标 exchange) + tiled_red_bricks candidates=4 未解析 |
| 0055 | incomplete | questionable | |+N 后缀污染 + polished_rose_quartz/brass_ingot CASCADE 未解析 |
| 0066 | incomplete | questionable | andesite 石切环(已标 exchange x77) 正常；|+N 污染 |
| 0069 | incomplete | questionable | blaze_upgrade_smithing_template 7×复制(112 blaze_rod)，smithing_trim 已知问题 |
| 0003,0006,0007,0045,0048,0058,0061,0063,0065,0068 | colossal | ok | 主体系 storage_ladder/exchange/recolor 已标记循环，无未覆盖异常 |
| 0037,0040 | giant | ok | storage_ladder x32 / recycling x8 已标记，链体属正常放大 |
| 0013,0014,0017,0029,0043,0052 | long | ok | ender_jelly/ethereal 方块环与 framed_glass 石切环均带 exchange/storage_ladder |
| 0044,0046–0057 | medium | ok | FD cabinet 与 experience 工具链正常，storage_ladder 已标记 |
| 其余 incomplete(0000/0001/0002/0004/0005/0008/0010/0012/0015/0016/0018–0032/0034/0035/0036/0038/0039/0041/0042/0053/0059/0060/0062/0067/0071/0072/0073/0074/0075/0076/0078) | incomplete | ok/questionable | 经 grep 筛查：主体为已标记 exchange(1561)/storage_ladder(615)/recolor(86) 循环；个别含 |+N 污染或 vanilla 预算截断未解析（见下） |

## 已知规划器级问题（仅链级提及，不展开为发现）
- `|+N` 物品 id 后缀污染：`create:layered_andesite|+14`、`create_mechanical_spawner:framed_dark_glass|+7`、`create_ironworks:steel_ingot|+5`、`createframed:*|+4`、`*|+451/+27/+31/+10` 等导致大量 `reason=RANK_TRUNCATED/BUDGET_TRUNCATED` 未解析（如 0011/0033/0055/0070），链头 raw 已含基础 id 却因后缀变体无法收敛。
- 原版常见物预算截断：`minecraft:paper`、`minecraft:bone_meal`、`minecraft:diamond` 等以 `BUDGET_TRUNCATED/CASCADE_EXHAUSTED` 未解析，虽存在真实配方（纸=甘蔗、骨粉=骨）但被预算截断。
- smithing_trim 破映射：`deeperdarker:warden_upgrade_smithing_template`(0009/0010/0018/0019/0074)、`create_dragons_plus:blaze_upgrade_smithing_template`(0069) 以 1→2 复制配方（@crafting_table/smithing_table）被当作合法生产者，造成 7× 复制与巨额材料消耗（0069 需 112 blaze_rod）。该模式已带 `pattern/exchange` 标签，按口径不再单列。
- 无净产出往返环机制本身、背包/升级物品被当机器、32 位溢出哨兵：本批未见明确触发，未报告。

## 规则建议（给汇总者）
- targetRegex: `^simulated:.*` —— 排除 simulated 合成占位命名空间的目标/中间体；证据：chains 0011/0036/0042/0078 共 ~8 处。
- recipeRegex: 收敛 storage_ladder 漏标族（建议一并标记）：
  - `create_compressed:sturdy_sheet_block|create_ironworks:.*sturdy_sheet_block`（chain 0077，证据 ~26 步未标记）
  - `createmetallurgy:refractory_mortar_ball`（chain 0064，证据 4:1 空转）
  - `dndesires:crafting/diamond_(shard_from_diamond|from_diamond_shard)`（chains 0009/0070，证据 1:4 空转）
- builtin 候选: 扩展 `pattern/storage_ladder` 匹配范围，覆盖 mod 专属 compress↔decompress 族（allthecompressed/create_compressed、create_ironworks、createmetallurgy、dndesires 等方块↔item 1:9/1:4 转换），建议标签 `pattern/storage_ladder`；证据：上述 3 族均呈现无净产出空转且与既有 storage_ladder 同构。
- 同步建议：将 `simulated:` 与 `jei:/synthetic/` 同视为合成命名空间，在分类/导出期显式剥离或标注，避免进入链审查目标集。
