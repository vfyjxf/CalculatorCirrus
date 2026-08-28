# LuckyBlockChallenge batch-004 审查报告
- pack: LuckyBlockChallenge
- 批次: batch-004（written=70；类别分布：incomplete 20 / short 13 / colossal 11 / giant 10 / medium 9 / long 7）
- 审查链数: 70（incomplete 20 全部 / complete 50 全读 short+medium+long，giant+colossal 抽样+关键链全读；合计 70 = 全量。incomplete 链均带 BUDGET_TRUNCATED 且各含 1 个 UNRESOLVED）
- 发现: critical 0 / major 0 / minor 0

## 审查结论（一句话）
本批为**纯原版配方集**（`minecraft:` 命名空间独占，无任何 mod / `jei:` 机器配方）。所有可质疑的链行为均已被 builtin 标签（`pattern/storage_ladder` ×48、`pattern/recolor` ×29）覆盖，或属于 round-3 列为「已知规划器级问题、仅链级提及、不展开为发现」的 **`|+N` 物品 id 后缀污染**。未发现任何未被标签覆盖的可疑配方族、新型假生产、数量级失控或「本可解析却 UNRESOLVED」的链级缺陷。故 0 发现。

## 发现
（无。所有潜在疑点均落在已处理的标签模式或已知 `+N` 污染下，按 round-3 口径不展开为发现。）
- 已带标签仍失控检查：否。所有 `storage_ladder` 循环均为**有界放大**（crafts 最高仅 137，无几何放大、链整体可用），`recolor` 循环为正常染料合成；无「带标签仍失控」情形。
- `+N` 污染在 complete 链为文本级污染（RAW 行已归一为干净 id，如 `cherry_planks|+10`→`cherry_planks`），不影响可生产性；在 incomplete 链为致命污染（见下）。

## 链级判定
> 说明：incomplete 链的 UNRESOLVED 全部为 `black_wool|+14` / `black_bed|+14`（污染的 `+14` 后缀），是已知 `+N` 规划器 bug 导致，非链过滤缺陷；complete 链中的 `+N` 仅为文本污染。

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | medium | ok | 普通合成；含 `acacia_wood\|+3`/`cherry_planks\|+10` 文本污染（已知） |
| 0001 | medium | ok | 同上 +N 污染；stick/fishing_rod 路径正常 |
| 0002 | medium | ok | 同上 +N 污染；warped_fungus_on_a_stick 路径正常 |
| 0003 | medium | ok | +N 污染；crimson_fence_gate 正常 |
| 0004 | long | ok | `redstone↔redstone_block` storage_ladder ×8（有界，已标签） |
| 0005 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染；并因污染塌缩出「彩虹羊毛」过建树（下游 +N） |
| 0006 | long | ok | storage_ladder ×8（已标签） |
| 0007 | long | ok | storage_ladder ×8（已标签） |
| 0008 | incomplete | bad | UNRESOLVED `black_bed\|+14` 系 +N 污染 |
| 0009 | medium | ok | +N 污染；warped_fence_gate 正常 |
| 0010 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0011 | colossal | ok | `raw_iron↔raw_iron_block` storage_ladder ×16 → chain → 吊牌（有界，已标签） |
| 0012 | giant | ok | +N 污染 + storage_ladder ×16；chest_minecart 正常 |
| 0013 | colossal | ok | storage_ladder ×16；crimson 吊牌正常 |
| 0014 | colossal | ok | storage_ladder ×16；birch 吊牌正常 |
| 0015 | giant | ok | recolor ×1 + storage_ladder ×5（已标签）；pink_candle 正常 |
| 0016 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0017 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0018 | short | ok | +N 污染（`sand\|+1`）；lime 染色玻璃板正常 |
| 0019 | giant | ok | +N 污染（`cobbled_deepslate\|+2`）+ storage_ladder ×16；furnace_minecart 正常 |
| 0020 | colossal | ok | storage_ladder ×16；dark_oak 吊牌正常 |
| 0021 | short | ok | +N 污染（`sand\|+1`）；black 染色玻璃板正常 |
| 0022 | colossal | ok | storage_ladder ×16；acacia 吊牌正常 |
| 0023 | short | ok | +N 污染（`sand\|+1`）；brown 染色玻璃板正常 |
| 0024 | giant | ok | recolor/storage_ladder（已标签）；pink_candle 正常 |
| 0025 | giant | ok | storage_ladder/recolor（已标签）；light_gray_candle 正常 |
| 0026 | colossal | ok | storage_ladder ×16；spruce 吊牌正常 |
| 0027 | short | ok | orange_candle 干净合成（无 +N） |
| 0028 | short | ok | +N 污染（`sand\|+1`）；green 染色玻璃板正常 |
| 0029 | colossal | ok | storage_ladder ×16；warped 吊牌正常 |
| 0030 | long | ok | storage_ladder ×8（已标签）；blue_candle 正常 |
| 0031 | colossal | ok | storage_ladder ×16；jungle 吊牌正常 |
| 0032 | short | ok | cyan_dye(pitcher_plant) 干净；染色玻璃板正常 |
| 0033 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0034 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0035 | short | ok | red_candle 干净合成（无 +N） |
| 0036 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0037 | short | ok | +N 污染（`sand\|+1`）；yellow 染色玻璃板正常 |
| 0038 | colossal | ok | storage_ladder ×16；mangrove 吊牌正常 |
| 0039 | colossal | ok | storage_ladder ×16；bamboo 吊牌正常 |
| 0040 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0041 | giant | ok | recolor/storage_ladder（已标签）；magenta_candle 正常 |
| 0042 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0043 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0044 | colossal | ok | storage_ladder ×16；cherry 吊牌正常 |
| 0045 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0046 | medium | ok | +N 污染；spectral_arrow 正常 |
| 0047 | medium | ok | +N 污染；warped_fence 正常 |
| 0048 | giant | ok | storage_ladder/recolor（已标签）；pink_concrete_powder 正常 |
| 0049 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0050 | giant | ok | storage_ladder/recolor（已标签）；light_gray_concrete_powder 正常 |
| 0051 | short | ok | stonecutter 干净合成；dark_prismarine_stairs 正常 |
| 0052 | giant | ok | recolor/storage_ladder（已标签）；magenta_concrete_powder 正常 |
| 0053 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0054 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0055 | short | ok | stonecutter 干净合成；dark_prismarine_slab 正常 |
| 0056 | short | ok | red_concrete_powder 干净合成（无 +N） |
| 0057 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0058 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0059 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0060 | long | ok | storage_ladder ×8（已标签）；blue_concrete_powder 正常 |
| 0061 | short | ok | orange_concrete_powder 干净合成（无 +N） |
| 0062 | medium | ok | +N 污染；crimson_fence 正常 |
| 0063 | long | ok | recolor ×1 + storage_ladder ×8（已标签）；purple_dye 正常 |
| 0064 | long | ok | storage_ladder ×8（已标签）；redstone_lamp 正常 |
| 0065 | giant | ok | recolor/storage_ladder（已标签）；light_blue_concrete_powder 正常 |
| 0066 | medium | ok | +N 污染；crimson_sign 正常 |
| 0067 | incomplete | bad | UNRESOLVED `black_wool\|+14` 系 +N 污染 |
| 0068 | incomplete | bad | UNRESOLVED `black_bed\|+14` 系 +N 污染 |
| 0069 | short | ok | respawn_anchor 干净合成（无 +N） |

## 规则建议（给汇总者）
- targetRegex: 无新增。本批目标均原版常见物（染色/羊毛/吊牌/矿车/蜡烛/混凝土粉），无需黑名单。
- recipeRegex: 无新增收敛需求。`pattern/storage_ladder` 与 `pattern/recolor` 已覆盖本批全部可疑配方族（redstone/lapis/raw_iron/bone 方块↔物品环、染料合成环）。
- builtin 候选: 无跨包新模式。
- **关键提示（非链过滤项，建议回传上游规划器）**：本批唯一系统性缺陷是 **`|+N` id 后缀污染**（共 232 处：incomplete 181、medium 22、short 6、giant 3）。该 bug 使物品 id 被追加 `|+14`/`|+10`/`|+3`/`|+2`/`|+1` 等后缀，导致：
  1. complete 链中动作/RAW 文本出现污染 id（但 RAW 汇总已归一为干净 id，不影响可生产性）；
  2. incomplete 链中 `dye_*_wool`/`dye_*_bed` 的基底羊毛被塌缩为 `black_wool\|+14`，该污染 id 无生产者 → 全部 20 条 incomplete 链 UNRESOLVED（且因 id 塌缩误建「彩虹羊毛」过建树，如 CHAIN 0005）。
  建议上游在构建图时**归一化剥离 `\|+N` 后缀**，而非在链层过滤；此修复可一次性消除本批全部 20 条 incomplete。
