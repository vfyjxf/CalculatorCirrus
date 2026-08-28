# mythcrafts batch-005 审查报告（round-006 选路质量深审）

- pack: mythcrafts
- 批次: batch-005（written=15；incomplete×5 / long×4 / medium×2 / giant×2 / colossal×2）
- 审查链数: 15（全量；本批仅 15 条链，无抽样遗漏，覆盖全部 5 个 category）
- 方法论: 按 BATCH-REVIEW-BRIEF「选路质量深审」章节，用 `cirrus-audit-data/mythcrafts/producers.tsv|jsonl` 交叉核对每条链非平凡（无 builtin rule-tag）EXECUTE 所产物品的生产者选择；对 `grep -F '<id>\t' producers.tsv` 计数 >1 的物品逐一 `grep '"for": "<id>"' producers.jsonl` 比对输入/输出，判定所选生产者是否明显差于索引替代者。
- 发现: critical 0 / major 1 / minor 0

## 审查范围与排除说明

- 全批 15 条链以「模式族」放大为主：`pattern/exchange`（twilightforest giant_log 转换）、`pattern/storage_ladder`（quark 砖/竖板/sturdy_stone、raw_iron/gold/copper_bricks、rabbit_hide、flax、redstone 块）、`pattern/recolor`（各染料/染毛）、`intent/recycling`（flax_block、soap_block、aether:golden_gloves）均按 BATCH-REVIEW-BRIEF 声明「already handled, do not re-report」跳过其放大行为本身。
- 红线排除：`...+N` 深度伪影 id（如 `minecraft:dark_oak_log|+287`、`minecraft:oak_planks|+523`、`minecraft:black_wool|+14`）、`[UNRESOLVED] ... reason=RANK_TRUNCATED/CASCADE_EXHAUSTED`（截断/级联伪影本身）不就其数量/未解析判问题，仅在不依赖该数量的前提下评估生产者选择。
- 唯一生产者（producers=1）的所有选择按口径「无替代可比较 → 一律 ok」直接判 ok，不再赘述。

## 非平凡 EXECUTE 与索引交叉核对（producers>1 候选逐条）

| 链 | action | 所选配方 @机器 | 产物品 | 索引生产者数 | 索引替代者 | 判定 |
|---|---|---|---|---|---|---|
| 0000 | A35 | sophisticatedstorage:chipped/mason_table_upgrade @crafter | sophisticatedstorage:chipped/mason_table_upgrade | 2 | 替代 `storage_chipped_mason_table_upgrade_from_backpack_chipped_mason_table_upgrade` 需 `sophisticatedbackpacks:chipped/mason_table_upgrade`（跨模逆向转换，非更优） | ok（选了直接者） |
| 0011 | A50 | sophisticatedbackpacks:upgrade_base @crafter | sophisticatedbackpacks:upgrade_base | 1 | 无替代 | ok |
| 0014 | A58 | sophisticatedbackpacks:advanced_filter_upgrade @crafter | sophisticatedbackpacks:advanced_filter_upgrade | 2 | 替代 `backpack_advanced_filter_upgrade_from_storage_advanced_filter_upgrade` 需 storage 变体（逆向，非更优） | ok（选了直接者） |
| 0014 | A63 | sophisticatedstorage:storage_advanced_filter_upgrade_from_backpack_advanced_filter_upgrade @crafter | sophisticatedstorage:advanced_filter_upgrade（目标） | 2 | **直接者 `sophisticatedstorage:advanced_filter_upgrade`**：gold_ingot×2 + `sophisticatedstorage:filter_upgrade`×1 + redstone×3；该 filter_upgrade 有直产 `sophisticatedstorage:filter_upgrade`（storage:upgrade_base+红石+线），storage:upgrade_base 唯一存在 | **worse-alternative（major）** 见 F-mythcrafts-1 |
| 0012 | A3 | supplementaries:soap @crafter | supplementaries:soap | 2 | 替代 `supplementaries:soap_block_uncrafting` 需 soap_block（逆向回收，非更优） | ok（选了直接 craft） |
| 0013 | A14 | powah:crafting/dielectric_paste_2 @crafter | powah:dielectric_paste | 2 | 替代 `powah:crafting/dielectric_paste` 需 lava_bucket 消耗；所选 `_2`（coals×2+clay+blaze_powder，无桶）更优 | ok（选了更优者） |
| 0013 | A15/A16 | powah:crafting/dielectric_rod_h / dielectric_rod_2 @crafter | powah:dielectric_rod_horizontal / dielectric_rod | 各 2 | 另一生产者为 rod↔rod_h 互转对，链用作 storage_ladder 放大，已排除 | ok（排除） |
| 0007 | A30 | sophisticatedstorage:iron_barrel_from_copper_barrel @crafter | sophisticatedstorage:iron_barrel | 2 | 直接者 `sophisticatedstorage:iron_barrel`：barrel+iron_ingot×8；所选者经 copper_barrel（barrel+铜×8）+铁×4，省 4 铁、多 8 铜 | ok（可辩护：铜廉于铁，且为模组 tier 阶梯预期路径） |

单生产者（producers=1）目标/子组件，已逐条核对均 ok：chipped:mason_table / chipped:loom_table / chipped:needles（各 `minecraft:workbench/*` 唯一）、vinery:winemaker_leggings、mcwwindows:blackstone_brick_arrow_slit、mcwtrpdoors:birch_barn_trapdoor、meadow:cooking_frame、handcrafted:oak_chair、farm_and_charm:silo_wood / silo_copper、sophisticatedbackpacks:tool_swapper_upgrade、sophisticatedstorage:copper_to_netherite_tier_upgrade、sophisticatedbackpacks:chipped/loom_table_upgrade、minecraft:campfire 等。

## 发现

### F-mythcrafts-1 | chain=0014 | severity=major | category=worse-alternative

- 位置: 链尾 A63 `EXECUTE sophisticatedstorage:storage_advanced_filter_upgrade_from_backpack_advanced_filter_upgrade x1 @ minecraft:crafter`（目标 sophisticatedstorage:advanced_filter_upgrade）
- 问题: 链为达成 storage 系目标 advanced_filter_upgrade，全程走 backpacks 分支——A42 造 `sophisticatedbackpacks:upgrade_base`、A48 造 `sophisticatedbackpacks:filter_upgrade`、A58 造 `sophisticatedbackpacks:advanced_filter_upgrade`，最后 A63 用 5 块 oak_planks 把 backpacks 变体「转换」成 storage 变体。然而索引中存在**直接生产者** `sophisticatedstorage:advanced_filter_upgrade`（gold_ingot×2 + `sophisticatedstorage:filter_upgrade`×1 + redstone×3），且该 `sophisticatedstorage:filter_upgrade` 本身有直产（redstone + strings + `sophisticatedstorage:upgrade_base`，唯一生产者，输入与 backpacks 版本等价）。
- 原因: planner 在 storage/backpacks 双模同构配方族中，错误偏好了 `*_from_backpack_*` 跨模转换配方，而非 storage 系直接生产者；导致多造一个无他用处的 `sophisticatedbackpacks:advanced_filter_upgrade` 中间品并浪费 5 木板。属选路（生产者选择）错误，非截断/放大伪影。
- 建议: 对 `sophisticated(storage|backpacks):*_from_(backpack|storage)_*` 跨模转换配方在排序中劣后于同模直接生产者（见规则建议 recipeRegex）。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | chipped/mason_table_upgrade 选直接生产者；其余为 storage_ladder/exchange 放大（排除） |
| 0001 | long | ok | blackstone_brick_arrow_slit 唯一生产者；stonecutting 直产 |
| 0002 | incomplete | ok | loom_table_upgrade 走 backpacks 直产；染毛 detour 属 recolor（排除） |
| 0003 | long | ok | copper→netherite tier 升级唯一生产者；lever/iron_ingot/leather 标准 |
| 0004 | incomplete | ok | 同 0002 家族，backpacks 直产 |
| 0005 | medium | ok | oak_chair 唯一生产者 |
| 0006 | long | ok | silo_copper/silo_wood 唯一生产者 |
| 0007 | long | ok | iron_barrel 经 copper_barrel 可辩护（省铁，tier 阶梯） |
| 0008 | incomplete | ok | chipped:needles（stick+loom_table）唯一生产者 |
| 0009 | incomplete | ok | winemaker_leggings 唯一生产者；金/染毛 detour 属 recycling/recolor（排除） |
| 0010 | medium | ok | cooking_frame/campfire 唯一生产者 |
| 0011 | giant | ok | tool_swapper_upgrade 唯一生产者；rabbit_hide→leather/flax→string 属 storage_ladder/recycling（排除） |
| 0012 | giant | ok | birch_barn_trapdoor/soap 选直接生产者；birch_planks 受锥 raw 约束走 palo_verde（非选路错误） |
| 0013 | colossal | ok | dielectric_paste 选更优 `_2`（无 lava bucket）；rod↔rod_h 为 storage_ladder（排除） |
| 0014 | colossal | bad | A63 经 backpacks 变体+5 木板转换达成 storage 目标，存在同模直接生产者 → F-mythcrafts-1 |

## 规则建议（给汇总者）

- targetRegex: 无（15 条目标均为合理 mod 物品，无展示物/无意义/重复变体信号）
- recipeRegex: `sophisticated(storage|backpacks):\w+_from_(backpack|storage)_\w+` —— 标记收敛 sophisticated storage↔backpacks 跨模转换配方，使其在排序中劣后于同模直接生产者；证据条数 1（chain 0014）。注：本批仅 1 例，非系统性，但属可提炼收敛点。
- builtin 候选: 无（storage_ladder / exchange / recolor / recycling 已覆盖本批放大行为；iron_barrel_from_copper_barrel 属模组 tier 阶梯预期路径，不建议排除）
