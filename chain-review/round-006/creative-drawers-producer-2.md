# creative-drawers-producer-2 batch-005 审查报告
- pack: creative-drawers-producer-2
- 批次: batch-005（written=8；类别分布：incomplete x8，全部 mega-wide/colossal）
- 审查链数: 8（incomplete 8 / complete 0；本批仅 8 条链，100% 覆盖）
- 方法：用 `cirrus-audit-data/creative-drawers-producer-2/producers.tsv` + `producers.jsonl` 对每条链的非 rule-tag EXECUTE（中间品生产者）做交叉核对，覆盖全部 8 链、≥100 条非平凡 EXECUTE 步。
- 发现: critical 0 / major 0 / minor 2

## 发现

### F-creative-drawers-producer-2-1 | chain=0000,0001,0002,0003,0004,0005,0006 | severity=minor | category=worse-alternative
- 位置: `[A58] EXECUTE minecraft:stone x3 @ occultism:ritual_dummy/summon_marid_smelter`（0000 L2011；另 0002 A11 L3241、0003 A58 L3494、0004 A100 L3892、0005 A58 L4134、0006 A58 等同构步），输入 `minecraft:cobblestone`，输出 `minecraft:stone`。
- 问题: 规划器选 Occultism「召唤 marid」仪式把 cobblestone→stone，而索引里存在纯熔炉配方 `minecraft:stone`（sys=`minecraft:smelting`，`in: minecraft:cobblestone x1`，`outs: minecraft:stone`）。二者 **I/O 完全相同**（cobblestone→stone，1:1），但该仪式需整套 Occultism 仪式基础设施（ritual dummy + 召唤 marid），复杂度远高于熔炉。该仪式甚至**不出现在索引的 minecraft:stone 生产者列表**中（索引仅列 mystical essence / allthecompressed decompress / minecraft:stone 熔炉）。
- 原因: 规划器对基础熔炼偏向"机器/仪式"配方而非熔炉；同类行为在 8 条链中系统性出现。属"相同 I/O、机器复杂度显著更高"的更差选择，但机器偏好本身有一定可辩护性（故判 minor，非 major）。
- 建议: 基础熔炼 cobblestone→stone 应优先 `minecraft:stone`（熔炉）；对 `occultism:ritual_dummy/summon_marid_smelter` 在存在 `minecraft:*` smelting 配方时降权。

### F-creative-drawers-producer-2-2 | chain=0000,0007 | severity=minor | category=worse-alternative
- 位置: `[A34] EXECUTE minecraft:glass x8 @ occultism:ritual_dummy/summon_marid_smelter`（0000 L130；0007 A34 L4301 等；链 0000 内共 20 处同类步），输入 `minecraft:sand|+1`，输出 `minecraft:glass`。
- 问题: 规划器选 Occultism 仪式把 sand→glass，而索引里存在纯熔炉配方 `minecraft:glass`（sys=`minecraft:smelting`，`in: minecraft:smelts_to_glass`，`outs: minecraft:glass`）。二者 I/O 相同（sand→glass）。该仪式**同样不出现在索引的 minecraft:glass 生产者列表**（索引列 allthecompressed decompress / create framed-glass 熔炼 x4 / minecraft:glass 熔炉）。此外所选配方输入 id 带 `sand|+1` 异常片段（`|+1` 为导出层噪声/格式泄漏），进一步说明该仪式是低质量匹配。
- 原因: 同 F-1。glass 在 0000（20 处）与 0007 反复经此仪式熔炼，而熔炉配方始终可用。
- 建议: sand→glass 用 `minecraft:glass` 熔炉配方；同上对 summon_marid_smelter 降权。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | questionable | glass/stone 基础熔炼走 Occultism 仪式（glass 20 处）；其余生产者选择合理，silicon/certus 走 AE2/ExtendedAE 直连配方 |
| 0001 | incomplete | ok | 仅 stone 走仪式；acacia_planks/stick/chest 等均选 vanilla 最简配方 |
| 0002 | incomplete | ok | 仅 stone 走仪式；iron_sword/gold_ingot 等选标准配方 |
| 0003 | incomplete | ok | 仅 stone 走仪式；stone_bricks 正确选 stonecutting（1:1，优于 4→4 合成） |
| 0004 | incomplete | ok | stone/iron 走仪式/blast；iron_nugget 选 vanilla |
| 0005 | incomplete | ok | 仅 stone 走仪式；diamond_sword/paper 选标准配方 |
| 0006 | incomplete | ok | stone/iron 走仪式/blast；quartz 选 blast（与 smelt 等价，可辩护） |
| 0007 | incomplete | questionable | glass 走 Occultism 仪式（20 处）；white_dye 等选 vanilla 最简配方 |

## 规则建议（给汇总者）
- targetRegex: 无。8 个目标（appflux:fe_16m_portable_cell、functionalstorage:armory_cabinet、apothic_enchanting:melonshelf、naturesaura:gold_brick、cookingforblockheads:heating_unit/ice_unit、ae2lt:pigmee_storage_component）均为合理物品，无展示物/重复变体/无意义目标；且各自生产者唯一（除 fe_16m_portable_cell 选 upgrade 路径，合理）。
- recipeRegex: `occultism:ritual_dummy/summon_marid_smelter` —— 当同物品存在 `minecraft:*`（`sys=minecraft:smelting`）配方时收敛/降权。证据：stone 跨 8 链、glass 跨 0000/0007（仅 glass 在 0000 内即 20 处）均选此仪式而非熔炉，I/O 完全相同、机器复杂度显著更高，且仪式未列入索引生产者列表。属"相同 I/O、更差机器"的系统性误选。
- builtin 候选: 无跨包通用新模式。本批问题局限于「Occultism 仪式熔炼偏好」，非全包通用模式；其余选择质量良好——multi-producer 项普遍选了最简/最纯配方（stonecutting、vanilla、`*_alt` 简化版），且**未**误选 cdp2 自定义 mod 提供的繁复 vanilla 替代配方（如 cdp2:recipes/minecraft/shaped/bone、clay、netherite_ingot 等），说明规划器对这类注入替代者识别正常。

## 备注
- 本批 8 链全部 incomplete，目标多为 appflux AE2 存储组件，依赖 mysticalagriculture essence 免费资源 + storage_ladder 压缩 + exchange 族（均已带 rule-tag，按口径不报）。
- 已排除：生成器/导出伪影（溢出 crafts 1.6e10、`|+1` id 片段本身、BUDGET/RANK 截断）；已 rule-tag 的 storage_ladder/exchange 放大行为；唯一生产者选择（一律 ok）；cdp2 void_block↔void_matter 无净产出环（loop-or-cycle，不在本轮四类审查范围内）。
- 关于 worse-alternative 严重度：两处均为"相同 I/O、机器更复杂"，机器自动化偏好有一定可辩护性，故判 minor；若汇总策略将"相同 I/O 但需整套仪式基础设施"视为明显更差，可上调为 major。
