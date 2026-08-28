# All Of Create 2.1 batch-004 审查报告

- pack: All Of Create 2.1
- 批次: batch-004（written=66；incomplete 40 / long 13 / colossal 7 / giant 4 / medium 2；solved=150, structuralDupes=84）
- 审查链数: 66（合计 ≥66，达到配额）
  - 全读：medium 2、giant 4、colossal 7、long 13（共 26 条完整链）
  - incomplete 40 条：全量扫描 + 定向 grep（`reason=`、`EXECUTE` 不带 `rule-tag` 统计、空 `in :`、jei: 系统、`|+N` 污染计数）
- 已知规划器级问题（链级一句话带过，不重复列）：32 位溢出哨兵、`|+N` id 后缀污染（本批 grep 到 2028 处，属已固化问题）、无净产出往返机制、`#planks`/wool any-of 全展开坍缩（RAW 中 400+ chipped planks 即此）、机器映射错配（本批无）、原版常见物预算截断。
- 发现: critical 0 / major 2 / minor 0

---

## 发现

### F-ao2.1-1 | chain=0001(代表 0006/0008/0022/0025 等同型) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0001 A94–A107（`create_compressed:mechanism_block`↔`create_compressed:mechanism`）、A53–A69（`create_compressed:crushed_raw_zinc_pile`↔`create_compressed:crushed_raw_zinc`）；incomplete 全文件 `EXECUTE create_compressed` 未带 `rule-tag` 共 882 条。
- 问题: `create_compressed` 模的 9:1/4:1 压缩↔解压族（mechanism、crushed_raw_zinc/iron/copper 的 `_pile`↔本体、iron/copper/gold/brass `_sheet`↔`_sheet_block`、cogwheel/large_cogwheel、polished_rose_quartz、steel、mechanism 等）**完全未被 `pattern/storage_ladder` 标记**（全批 0 条 `create_compressed` 行带该标签）。链中表现为零净产出的放大环：mechanism_block(9 precision_mechanism)→mechanism(9 precision_mechanism) 反复 14 步；crushed_raw_zinc_pile↔crushed_raw_zinc 出现 147/152 次。
- 原因: 既有 `pattern/storage_ladder` 规则的命名空间匹配不到 `create_compressed:`（冒号）命名空间——其枚举中“create materials *_block / compressed_*”未实际覆盖该 mod 的 `create_compressed:<item>_block|_pile|_sheet` 配方，导致本应被归并的压缩环被规划器当作真实放大路径遍历。
- 建议: 将 `create_compressed:` 全部 `*_block`/`*_pile`/`*_sheet` 压缩族纳入 `pattern/storage_ladder`（或反之标记排除），阻断其作为放大环。证据：incomplete 内 882 条未标记 `create_compressed` 步骤；index 中上述链 crafts 高达 263（0001）/594（0006）/607（0022），depth 普遍 50+。

### F-ao2.1-2 | chain=0006(代表 0016/0022/0033/0044 等同型) | severity=major | category=loop-or-cycle
- 位置: CHAIN 0006 A26 起（`dndesires:crafting/lapis_lazuli_shard_from_lapis_lazuli`↔`dndesires:crafting/lapis_lazuli_from_lapis_lazuli_shard`，incomplete 全文件出现 78/72 次）；以及 `create_ironworks:materials/steel/nugget_from_ingot`↔`ingot_from_nugget`(20/20)、`create_ironworks:materials/rose_quartz/item_from_block`↔`block_from_item`(10/10)、`ae2:misc/deconstruction_fluix_block`↔`ae2:decorative/fluix_block`(24/24)、`createadditionallogistics:*/lazy_cogwheel_from_cogwheel`↔`cogwheel_from_lazy`(48/48)、`cgs:steel_nugget`↔`steel_ingot_from_steel_nugget`(20/20)、`garnished:numbing_parchment_from_block`↔`numbing_parchment_block`(4/4)。
- 问题: 除 `create_compressed` 外，还有一批 9:1/4:1/1:8 的压缩↔解压环同样**未带** `pattern/storage_ladder` 标签，被规划器当作无净产出放大环遍历：
  - `dndesires:crafting/*_shard_from_*` 与 `*_from_*_shard`（lapis 1↔8 shard、coal、diamond 同理）；
  - `create_ironworks` 的 nugget↔ingot、rose_quartz item↔block；
  - `ae2` fluix_block 压缩/解压互逆；
  - `createadditionallogistics` 的 lazy_cogwheel ↔ 普通 cogwheel 互转（1:1 环）；
  - `cgs` 钢 nugget↔ingot；`garnished` numbing_parchment 块环。
- 原因: 上述命名空间均不在既有 `pattern/storage_ladder` 枚举内（规则只覆盖“compressed_*、create materials *_block、FD/quark/powah…”等），而这些都是标准压缩解压族，应被同一规则命中。
- 建议: 在 `pattern/storage_ladder` 中增补 `dndesires:crafting/(lapis_lazuli|coal|diamond)_(shard|piece)_from_*`、`create_ironworks:.*/(nugget_from_ingot|ingot_from_nugget|item_from_block|block_from_item)`、`ae2:(decorative/fluix_block|misc/deconstruction_fluix_block)`、`createadditionallogistics:.*/(lazy_.*_from_.*|.*_from_lazy)`、`cgs:steel_*`、`garnished:numbing_parchment_*`。证据：incomplete 中这些配方合计出现数百次且无一带 `rule-tag`。

---

## 链级判定

完整链（flags=complete，exchange/storage_ladder/recolor/intent 已带标签，路径可用）：

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 travelersbackpack:lantern_upgrade | giant | ok | flax_block↔flax 回收环已带 intent/recycling；其余 storage_ladder |
| 0004 pipeorgans:subbass | medium | ok | #planks any-of 坍缩（已知规划器问题），非新模式 |
| 0005 create:crafting_blueprint | medium | ok | chipped planks 全展开（#planks 坍缩，已知） |
| 0007 supplementaries:fire_pit | long | ok | 完整；染色/交换已带标签 |
| 0009 supplementaries:candy | colossal | ok | framed_glass 交换环已带 pattern/exchange |
| 0010 garnished:void_stroganoff | long | ok | 完整 |
| 0011 farmersdelight:hanging_canvas_sign | giant | ok | straw 回收环已带 pattern/storage_ladder |
| 0014 simulated:laser_sensor | long | ok | 完整 |
| 0015 create_submarine:barometer | colossal | ok | 完整 |
| 0036 createframed:yellow_cardboard_window_pane | long | ok | cardboard 染色/交换已带标签 |
| 0039/0045/0048/0057/0062 supplementaries:candle_holder_* | colossal | ok | 完整；exchange 已带标签 |
| 0041/0042/0049/0051/0053/0054/0055/0056 createframed:*_cardboard_window_pane | long | ok | 同 0036 模式 |
| 0046 create_new_age:reinforced_energiser | long | ok | 完整 |
| 0058 createframed:lime_cardboard_window_pane | giant | ok | exchange/recycle 已带标签 |
| 0059 createendertransmission:chunk_loader | long | ok | 完整 |
| 0065 createframed:white_cardboard_window_pane | giant | ok | 同 0058 |

未完成链（flags=incomplete，均含 andesite 石切交换环【已带 pattern/exchange】+ 未带标签的 create_compressed/dndesires 压缩环【F-ao2.1-1/2】放大，UNRESOLVED 多为 `|+N` 污染或 budget 截断）：

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0001 dndesires:gold_mixer | incomplete | questionable | 50 步 andesite 交换环 + mechanism/crushed_zinc 未标压缩环；layered_andesite\|+14 未解析 |
| 0002 createpropulsion:tempered_wing | incomplete | questionable | 同上模式；depth 53 放大 |
| 0003 createpropulsion:copycat_wing | incomplete | questionable | 同上模式 |
| 0006 createpropulsion:tilt_adapter | incomplete | questionable | dndesires lapis shard 环（F-ao2.1-2）；depth 110 最严重 |
| 0008 createcasing:refined_radiance_deployer | incomplete | questionable | 压缩环放大；pending 3 |
| 0012 createcasing:shadow_steel_deployer | incomplete | questionable | 同上 |
| 0013 createbigcannons:ram_rod | incomplete | questionable | 同上 |
| 0016 ae2:spatial_storage_cell_16 | incomplete | questionable | ae2 fluix 解压环（F-ao2.1-2）；crafts 341 |
| 0017 dndesires:fluid_hatch | incomplete | questionable | andesite 交换环 50 步 |
| 0018/0019/0020/0023 create_connected/gnkinetics crank_wheel/large_cog_crank | incomplete | questionable | andesite 环 + cogwheel 未标压缩环 |
| 0021 create:sticky_mechanical_piston | incomplete | questionable | 同上 |
| 0022 ae2:energy_card | incomplete | questionable | fluix/cogwheel/机制压缩环；crafts 607 最高 |
| 0024 create:weighted_ejector | incomplete | questionable | 同上 |
| 0025 create_new_age:street_light | incomplete | questionable | andesite 环 + crushed 堆环；crafts 395 |
| 0026/0029 sophisticated*backpacks/storage:chipped/loom_table_upgrade | incomplete | questionable | #planks 坍缩（894/891 raw_kinds，已知） |
| 0027 cgs:flintlock | incomplete | questionable | red/blue_dye/flax 因 BUDGET_TRUNCATED 未解析（vanilla，已知预算截断） |
| 0028/0061/0063 ae2:wireless_access_point / tfmg:*_multimeter | incomplete | questionable | 压缩环放大；multimeter 系列 pending 2 |
| 0030/0034/0047/0050 pantographsandwires:*_insulator_* | incomplete | questionable | andesite 交换环；raw_kinds 极小 depth 53 |
| 0031/0037 sophisticatedstorage:iron_shulker_box / create_deep_dark:echo_sword | incomplete | questionable | 压缩环；echo_sword pending 7 |
| 0032 create_things_and_misc:train_stop | incomplete | questionable | 同上 |
| 0033 trading_floor:trading_depot | incomplete | questionable | 真实方块配方；andesite 环放大 |
| 0035/0040/0043/0052/0054/0060 tfmg:*_multimeter / flarestack | incomplete | questionable | 压缩环；pending 2 |
| 0038 create:item_hatch | incomplete | questionable | andesite 环 |
| 0044 createdeco:blue_andesite_lamp | incomplete | questionable | andesite 交换环 |
| 0064 simulated:velocity_sensor | incomplete | questionable | 压缩环放大 |

---

## 规则建议（给汇总者）

- **targetRegex**：本批无新增需排除的目标类（target 均为真实可合成方块/物品，无 fake-production 目标）。已知 `#planks`/`#wool` any-of 坍缩属规划器层，非 target 过滤范畴。
- **recipeRegex（新增 storage_ladder 覆盖，证据条数见上）**：
  - `^create_compressed:.*(_block|_pile|_sheet)$` 及其本体配对 —— 覆盖 882 条未标记步骤（F-ao2.1-1）。
  - `^dndesires:crafting/(lapis_lazuli|coal|diamond)_(shard|piece)_from_` 与 `_from_(shard|piece)$` —— lapis 78/72、coal 8/8、diamond 1/1（F-ao2.1-2）。
  - `^create_ironworks:.*/(nugget_from_ingot|ingot_from_nugget|item_from_block|block_from_item)$` —— 20/20、10/10。
  - `^ae2:(decorative/fluix_block|misc/deconstruction_fluix_block)$` —— 24/24。
  - `^createadditionallogistics:.*/(lazy_[a-z_]*_from_[a-z_]*|[a-z_]*_from_lazy)$` —— lazy cogwheel 48/48、large 16/16。
  - `^cgs:steel_(nugget|ingot_from_steel_nugget)$` —— 20/20。
  - `^garnished:numbing_parchment_(from_block|block)$` —— 4/4。
- **builtin 候选**：`pattern/storage_ladder` 应扩展为覆盖“任意 `<mod>:*_block`↔`*`（9:1/4:1）压缩解压族”，而非仅匹配 `compressed_*`/特定枚举——当前对 `create_compressed:`（冒号命名空间）整体漏标是规则匹配的命名空间错位，建议统一按 `_block`/`_pile`/`_sheet`/nugget↔ingot/shard↔item 形态匹配。
- **已带标签仍失控（供参考，非新发现）**：andesite 石切变体环 `create:*_from_stone_types_andesite_stonecutting`（~50 节点）虽已带 `pattern/exchange`，但单链展开 50 步即耗尽 rank/budget，使真实生产者 `create:layered_andesite` 以 `|+N` 污染 id 未解析。若该环持续造成截断，建议将其从“标记”升级为“排除/封顶环深”。
- 已知但本轮不重复列：`|+N` id 污染（本批 2028 处）、`#planks`/`#wool` any-of 坍缩、32 位哨兵、原版物 budget 截断——均属已固化规划器问题。
