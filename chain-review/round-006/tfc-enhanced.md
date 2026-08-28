# tfc-enhanced batch-005 审查报告（选路质量深审）

- pack: tfc-enhanced
- 批次: batch-005（written 43 条；category 分布：giant 19 / short 7 / colossal 6 / incomplete 6 / medium 4 / long 1）
- 审查链数: 43（全量；因总数仅 43 < 配额 60，故全部过审。incomplete 6 / complete 37）
- 方法：对每条链的非平凡中间 EXECUTE，用 `cirrus-audit-data/tfc-enhanced/producers.tsv` + `producers.jsonl` 核对生产者数，并对比更优替代者输入。
- 发现: critical 0 / major 1 / minor 0

## 发现

### F-tfc-enhanced-1 | chain=0000,0006,0022,0023,0025,0039 | severity=major | category=worse-alternative（兼具 silly-detour）

- 位置: 各链 `EXECUTE minecraft:blue_dye ...` 及其上游 `EXECUTE minecraft:lapis_lazuli_from_blasting_deepslate_lapis_ore ... @ minecraft:blast_furnace`（例：colossal 0000 行 169→180；colossal 0006 行 570→581；incomplete 0022 行 215→226 等）
- 问题: 这些链需要 `minecraft:blue_dye`，planner 选的生产者是 `minecraft:blue_dye`（输入 `minecraft:lapis_lazuli`），而 lapis_lazuli 又由 `minecraft:lapis_lazuli_from_blasting_deepslate_lapis_ore`（输入 `minecraft:deepslate_lapis_ore`，需 blast_furnace）+ 一段 storage_ladder（`lapis_block`↔`lapis_lazuli`）间接产出。整条 blue_dye 路径约 4 步：挖 `deepslate_lapis_ore` → 熔炼 → 合成方块 → 拆回 → 染蓝。
- 原因（索引交叉核对）: 同包存在更直接、输入更廉价的替代生产者 `minecraft:blue_dye_from_cornflower`（索引 `producers.jsonl` 记录：`in={minecraft:cornflower x1}` → `out=['minecraft:blue_dye']`，@ crafting_table）。`minecraft:cornflower` 在 `producers.tsv` 中生产者数为 0（raw leaf，按口径视为可获取原材料），即 1 朵花直接 → 1 蓝染料，1 步、无机器、无矿采/熔炼。蓝蜡烛(0006)与所有需要蓝染的中间色（light_blue/purple/magenta 等 recolor 链）因此被不必要地挂上一条矿石供应链。
- 为何判定 worse-alternative 而非 ok：替代者输入更少更常见（野花 vs 深层矿石+熔炼），且直连少步（1 步 vs 4 步）。同一根因系统性出现在 6 条链（magenta_candle 0000、blue_candle 0006、red_carpet 0022、yellow_carpet 0023、lime_carpet 0025、pink_carpet 0039），属 planner 选路偏好问题而非单链偶然。
- 建议（可辩护边界说明）：判定前提是 `minecraft:cornflower` 在本包世界内可获取；其生产者数为 0（raw leaf），按口径视为可得。若实际包数据禁用玉米花生成，则该项降为 minor suspected。其余染料（red/yellow/green 等）planner 已正确选用花/植物源（beetroot、sunflower、`tfc:crafting/green_dye`），唯独 blue_dye 误选矿石路线，故为局部而非全局染料问题。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | colossal | bad | blue_dye 经 deepslate_lapis_ore 熔炼产出，应改 cornflower |
| 0006 | colossal | bad | blue_candle 蓝染料走矿石链，应选 blue_dye_from_cornflower |
| 0022 | incomplete | bad | red_carpet 蓝染源为 lapis 矿石链 |
| 0023 | incomplete | bad | yellow_carpet 蓝染源为 lapis 矿石链 |
| 0025 | incomplete | bad | lime_carpet 蓝染源为 lapis 矿石链 |
| 0039 | incomplete | bad | pink_carpet 蓝染源为 lapis 矿石链（7 处 blue_dye） |
| 0004,0011,0024,0030 | colossal | ok | 未用 blue_dye 矿石链；gray/light_gray 用 tfc 空输入配方（非更差） |
| 0007,0010 | incomplete | ok | 无 blue_dye 矿石链问题 |
| 其余 31 条（全部 short/medium/long + giant 中 wood/lumber/stick/saw/knife/chest 等 TFC 原生链） | — | ok | 多生产者中间品核查：stick/lumber 等 TFC 原生路径可辩护；netherite_ingot 仅用标准 scrap+gold 配方；stone_bricks 用合成配方（与 stonecutting 等价，可辩护） |

## 规则建议（给汇总者）

- targetRegex: 无
- recipeRegex: `^minecraft:blue_dye$` —— 当 `minecraft:blue_dye` 需要由 `lapis_lazuli` 输入产出时，应下权重、优先选用花源 `minecraft:blue_dye_from_cornflower`（cornflower 为 raw leaf，1 步）；同时对 `minecraft:lapis_lazuli_from_blasting_deepslate_lapis_ore` 及其 storage_ladder 包装在"仅为产出 blue_dye"时降权。证据条数：6 条链（0000/0006/0022/0023/0025/0039）。
- builtin 候选: 无（white_dye 经 bone_meal 的 storage_ladder 属已标记家族，按口径不重复报告；其 lily_of_the_valley 替代不另立规则）。
- 备注: 本批 wood/stick 子链（`stick_bundle`/`stick_bunch`/`stick_from_bundle`/`stick_from_bunch`）普遍存在，索引中另有直连 `tfc:crafting/stick_from_twigs`（1 twig→stick）。因无法确认 `stick_from_twigs` 的产出量与是否为 TFC 原生路径（planner 同时亦用直连 `minecraft:stick` 木板配方），且该路由属 TFC 木作原生流程，判为可辩护，未列为发现。若后续确认 `stick_from_twigs` 产出≥且步骤更少，可单独立 worse-alternative 收敛。
