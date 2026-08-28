# create-train batch-004 审查报告（round-006 选路质量深审）
- pack: create-train
- 批次: batch-004（written=9，全部 incomplete；mega-wide×1 / giant×2 / colossal×6）
- 审查链数: 9（全量；本批仅 9 条，无抽样遗漏）
- 方法论: 按 BATCH-REVIEW-BRIEF「选路质量深审」章节，用 `cirrus-audit-data/create-train/producers.tsv|jsonl` 交叉核对每条链的非平凡（无 builtin rule-tag）EXECUTE 所产物品的生产者选择。
- 发现: critical 0 / major 0 / minor 0

## 审查范围与排除说明
- 全批 1430 条 EXECUTE 中，规则标签分布：`pattern/exchange` 255、`pattern/storage` 224、`intent/recycling` 64、`pattern/recolor` 53——均为 BATCH-REVIEW-BRIEF 已声明「already handled, do not re-report」的家族，跳过。
- 余下 **20 条无 rule-tag 的 EXECUTE** 为唯一候选（见下）。这 20 条分布在 0000/0002/0003/0004/0005。
- 显式排除（红线）：`titaniumnuggetrecept_2 x43046721`（=3^16，CASCADE 放大伪影）、`wild_tomatoes_using_saw x4617`、`cantazarite_block x49` 等异常数量级属 BUDGET/CASCADE 截断症状，不就其数量判问题；仅在不依赖该数量的前提下评估生产者选择。

## 非平凡 EXECUTE 与索引交叉核对（逐条）
| 链 | action | 所选配方 @机器 | 产物品 | 索引生产者数 | 索引替代者 | 判定 |
|---|---|---|---|---|---|---|
| 0000 | A27 | ars_nouveau:sourceberry_sack_to_bush @crafter | ars_nouveau:sourceberry_bush | 4 | arsdelight:source_berry_unpack（1 crate→bush，同 I/O 等价）；另为 mechanical_crafter 同配方副本 | ok |
| 0000/0003/0004 | A81/A98/A108/A151/A216/A148/A236 | alexscaves:pewen_wood @crafter | alexscaves:pewen_wood | 3 | 三者输入均为 pewen_log×4→wood（crafter / mechanical_crafter / shadow_steel_press 三机同配方副本） | ok |
| 0000 | A137/A160 | create:cutting/.../pewen_log_to_stripped_pewen_log @railway_mechanical_saw | alexscaves:stripped_pewen_log | 1（唯一生产者） | 无替代 | ok（唯一） |
| 0002 | A50 | createpropulsion:crafting/raw_platinum_block @mechanical_crafter | createpropulsion:raw_platinum_block | 3 | 三者输入均为 raw_platinum×9→block（机副本） | ok |
| 0003 | A153 | farmersdelight:cutting/wild_tomatoes_using_saw @mechanical_saw | farmersdelight:tomato(+seeds+green_dye) | 5 | 纯配方 `farmersdelight:tomato` 输入为 tomato_crate（**不同前驱**），非同输入更纯直接配方；3 条 cutting 变体均为同 impure 输出 | ok |
| 0004/0005 | A54 | create_the_air_wars:titaniumnuggetrecept_2 @mechanical_crafter | create_the_air_wars:titaniumingot | 9 | smelt/blast(rawtitanium→ingot)、from_block 为不同输入；所选 nugget→ingot 且数量 43046721=3^16 为 CASCADE 伪影，排除数量后选择本身为合法 nugget 路径 | ok（排除数量） |
| 0004/0005 | A168 | create_the_air_wars:crushed_amalgam @crushing_wheel | create_the_air_wars:crushed_amalgam | 1（唯一生产者） | 无替代 | ok（唯一） |
| 0004/0005 | A205/A180 | ars_elemental:spore_blossom_up @crafter | ars_elemental:spore_blossom_up | 2 | 两者输入均为 spore_blossom×1→up（机副本） | ok |
| 0004/0005 | A219/A194 | swem:cantazarite_block @mechanical_crafter | swem:cantazarite_block | 5 | `revert_cantazarite_anvil` 输入为 cantazarite_anvil（本身由 cantazarite 合成，逆向回收，非更优直接配方）；其余 3 条为 9 cantazarite 同配方机副本 | ok |

## 结论
全部 20 条非平凡 EXECUTE 所产物品，其索引替代者均属以下之一，故无 worse-alternative / silly-detour / impure-recipe 可判：
1. 同配方在 vanilla crafter / create mechanical_crafter / press 上的**机器系统副本**（输入、输出完全一致）——选 baseline 或 create 自动副本均属可辩护；
2. 输入物不同的「替代」配方（如 tomato_crate→tomato、cantazarite_anvil→block、sack↔crate 互转），不构成「更优直接配方」；
3. 唯一生产者（stripped_pewen_log、crushed_amalgam）——无替代可比较；
4. 数量级异常者（titaniumingot、tomato）其异常为 CASCADE/BUDGET 伪影，按红线排除，且生产者本身合法。

未发现目标选择怪象：9 条目标（honey_glazed_chimera / anole_item / medium_rocket_cm / cluster_shell_mk3 / tomahawk / x25ml / door_caf_urbos_3 / door_ice_top / door_london_overground）均为合理 mod 物品，非展示物/重复变体。

## 发现
（无）

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | ok | 非平凡生产者均为标准配方，替代者皆机器副本 |
| 0001 | incomplete | ok | 全链 storage/exchange/recolor/recycling 家族，无未标签生产者 |
| 0002 | incomplete | ok | raw_platinum_block 选标准 9→1 配方，替代者同输入 |
| 0003 | incomplete | ok | tomato 走 wild_tomatoes 切割，无同输入更纯直接配方 |
| 0004 | incomplete | ok | titaniumnuggetrecept_2 数量 3^16 为 CASCADE 伪影已排除，生产者合法；其余 ok |
| 0005 | incomplete | ok | 同 0004 结构，生产者选择均合法 |
| 0006 | incomplete | ok | 全链已处理家族，无未标签生产者 |
| 0007 | incomplete | ok | 全链已处理家族，无未标签生产者 |
| 0008 | incomplete | ok | 全链已处理家族，无未标签生产者 |

## 规则建议（给汇总者）
- targetRegex: 无（目标均合理，无 junk/展示物信号）
- recipeRegex: 无（本批无系统性误选配方族；20 条候选生产者选择均符合索引中最优/等价项）
- builtin 候选: 无（无可提炼的新模式；storage/exchange/recolor/recycling 已覆盖）
