# LuckyBlockChallenge batch-006 审查报告

- pack: LuckyBlockChallenge
- 批次: batch-006（written=41；类别分布：medium 17 / giant 11 / long 6 / short 7）
- 审查链数: 41（incomplete 0 / complete 41；本批无 incomplete 文件，全部为 complete，故全量审查 41 条，覆盖全部 4 个类别）
- 发现: critical 0 / major 0 / minor 0

## 方法

用 `cirrus-audit-data/LuckyBlockChallenge/producers.tsv` + `producers.jsonl` 对每条链的非平凡中间 EXECUTE（跳过 +raw 叶子、已带 rule-tag 的 storage_ladder/exchange 放大步）做生产者交叉核对：

```
grep -F '<item>\t' producers.tsv        # 生产者数
grep '"for": "<item>"' producers.jsonl  # 替代者明细（sys/id/in/outs）
```

核对覆盖：ingot 类（iron_ingot×8、gold_ingot×10、diamond×5、redstone×5、coal×5、quartz×2）、stonecutting 类（diorite_stairs/diorite_wall/andesite_stairs/polished_andesite_slab(×3)/polished_andesite_stairs(×3)）、dye（yellow_dye×2），以及全部单生产者物品（其余 batch 产出物均 count=1）。

## 发现

无。本轮四类（worse-alternative / silly-detour / impure-recipe / 目标选择怪象）经索引交叉核对后均无实锤，理由如下：

1. **矿石→锭一律选 blast_furnace 变体，且为最优**：`iron_ingot_from_blasting_raw_iron`、`gold_ingot_from_blasting_raw_gold`、`diamond_from_blasting_deepslate_diamond_ore`、`redstone_from_blasting_deepslate_redstone_ore`、`coal_from_blasting_coal_ore`、`quartz_from_blasting` 均与 furnace smelting 变体 1:1 等价产出，但高炉更快、无需额外燃料，属严格更优；其余替代者（`*_from_*_block`、`*_from_nuggets`、smelting 变体）要么需先有成品（循环不可行）要么更慢，均不构成更优替代。→ 无 worse-alternative。
2. **stonecutting 变体选直连且更省料**：`polished_andesite_slab/stairs` 索引有 3 个生产者（crafting 经 polished_andesite、stonecutting 经 polished_andesite、stonecutting 经 andesite）。planner 选 `..._from_andesite_stonecutting`（anderite 直切，单跳、1→2），优于需先烧 andesite→polished_andesite 的另两路。`diorite_stairs/wall`、`andesite_stairs` 同理选 stonecutting（1:1）优于 crafting（更耗料）。→ 无 impure / 无 silly-detour。
3. **dye 等价选择可辩护**：`yellow_dye` planner 选 `yellow_dye_from_dandelion`，替代 `yellow_dye_from_sunflower` 同为 1 花→1 染料，等价，判 ok。
4. **单生产者物品（count=1）一律 ok**：tinted_glass/glass、campfire/soul_campfire、daylight_detector、lead、clock、beehive、cartography_table、composter、bowl、ladder、mushroom_stew、rail、iron_bars、iron_door、cauldron、shield、smithing_table、golden_*/iron_axe、各 fence_gate、acacia_wood/planks、bamboo_*、leather、paper、book、writable_book、candle/yellow_candle、slime_block 等索引均仅 1 个生产者，无可比较替代，按纪律判 ok。
5. **目标无怪象**：41 个 target 皆为合法原版物品（盔甲、trim 模板、各类栅栏门/楼梯/墙、营火、钟、拴绳等），无展示物/重复变体/junk；`decompress` 已在 targetBlacklist 排除。
6. **机器映射正确**：crafting_table / blast_furnace / furnace / stonecutter 与各配方 system 一致，无机器错配。

注：`+|N` 后缀（如 `acacia_wood|+3`、`cherry_planks|+10`、`stripped_crimson_hyphae|+39`）属导出层伪影；链中 stick/bowl 偶用 `cherry_planks`、campfire 用 `stripped_crimson_hyphae` 而非本链已造的同族 wood/planks——因所有 planks/log 对该槽位等价，属可辩护选择，不计 worse-alternative。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | long | ok | golden_chestplate：raw_gold→blast_ingot 最优，storage_ladder 已标 |
| 0001 | giant | ok | coast trim：diamond blast+exchange 已标，路径合理 |
| 0002 | giant | ok | snout trim：同上 |
| 0003 | giant | ok | rib trim：同上 |
| 0004 | giant | ok | iron_leggings：raw_iron→blast_ingot 最优 |
| 0005 | medium | ok | ladder：acacia→planks→stick→ladder 唯一路径 |
| 0006 | medium | ok | composter：唯一配方 |
| 0007 | long | ok | campfire：coal blast + stick + hyphae，唯一配方 |
| 0008 | medium | ok | polished_andesite_slab：选 andesite 直切（最优） |
| 0009 | medium | ok | polished_andesite_stairs：选 andesite 直切（最优） |
| 0010 | medium | ok | daylight_detector：唯一配方 |
| 0011 | short | ok | writable_book：leather/paper/book 唯一路径 |
| 0012 | short | ok | beehive：唯一配方 |
| 0013 | medium | ok | soul_campfire：唯一配方 |
| 0014 | long | ok | golden_leggings：raw_gold→blast_ingot 最优 |
| 0015 | giant | ok | rail：raw_iron→blast_ingot + stick，唯一配方 |
| 0016 | giant | ok | cauldron：raw_iron→blast_ingot 最优 |
| 0017 | giant | ok | shield：iron_ingot+planks，唯一配方 |
| 0018 | medium | ok | andesite_stairs：选 stonecutting（最优） |
| 0019 | medium | ok | dark_oak_fence_gate：唯一配方 |
| 0020 | short | ok | cartography_table：唯一配方 |
| 0021 | giant | ok | iron_door：raw_iron→blast_ingot 最优 |
| 0022 | medium | ok | oak_fence_gate：唯一配方 |
| 0023 | short | ok | tinted_glass：唯一配方 |
| 0024 | medium | ok | acacia_fence_gate：唯一配方 |
| 0025 | medium | ok | jungle_fence_gate：唯一配方 |
| 0026 | giant | ok | smithing_table：iron_ingot+planks 唯一配方 |
| 0027 | medium | ok | mangrove_fence_gate：唯一配方 |
| 0028 | medium | ok | bamboo_fence_gate：唯一配方 |
| 0029 | medium | ok | mushroom_stew：唯一配方 |
| 0030 | medium | ok | cherry_fence_gate：唯一配方 |
| 0031 | medium | ok | birch_fence_gate：唯一配方 |
| 0032 | long | ok | lead：slime_ball+string 唯一配方 |
| 0033 | medium | ok | spruce_fence_gate：唯一配方 |
| 0034 | long | ok | clock：redstone blast + gold_ingot 最优 |
| 0035 | giant | ok | iron_bars：raw_iron→blast_ingot 最优 |
| 0036 | short | ok | diorite_stairs：选 stonecutting（最优） |
| 0037 | short | ok | yellow_candle：dye 等价选择可辩护 |
| 0038 | short | ok | diorite_wall：选 stonecutting（等价/最优） |
| 0039 | giant | ok | iron_axe：raw_iron→blast_ingot + stick 最优 |
| 0040 | long | ok | golden_axe：raw_gold→blast_ingot + stick 最优 |

## 规则建议（给汇总者）

- targetRegex: 无（目标均合法，无 junk/展示物/重复变体需要排除）。
- recipeRegex: 无（无被系统性误选的 mod 配方族；矿石统一选 blast_furnace 变体本就是最优，无需收敛）。
- builtin 候选: 无新跨包模式。既有的 `pattern/storage_ladder`（raw_iron/raw_gold/diamond/coal/redstone/slime 等的 block↔单品放大）与 `pattern/exchange`（smithing trim 模板复制）已覆盖本批全部放大/复制行为，无需新增。
- 无新模式时写"无"。
