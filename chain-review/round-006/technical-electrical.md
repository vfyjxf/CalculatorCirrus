# technical-electrical batch-006 审查报告

- pack: technical-electrical
- 批次: batch-006（written=7；colossal 1 / incomplete 6；solved=150 但 structuralDupesDropped=143，故仅 7 条落盘）
- 审查链数: 7（全量——本批 written 仅 7 条，不足 60 故全审；colossal 1 / incomplete 6）
- 发现: critical 0 / major 1 / minor 1

> 方法：对每条链抽取非平凡中间品 EXECUTE（跳过 trivial 原材料步与已带 rule-tag 的 storage_ladder/exchange/recycling/recolor 放大步），用 `cirrus-audit-data/technical-electrical/producers.tsv`（生产者数）与 `producers.jsonl`（`"for"` 明细）交叉核对。
> 排除项：生成器/导出伪影（`|+N` id、BUDGET/RANK 截断、机器映射）、已带标签家族的放大行为本身、只有唯一生产者的选择、链内不出现的 junk 配方。

## 发现

### F-technical-electrical-1 | chain=0004,0005,0006 | severity=major | category=worse-alternative
- 位置: 0004[A100,A105]、0005[A63]、0006[A49] EXECUTE `immersiveengineering:smelting/lead_red` @ minecraft:furnace（minecraft:red_dye 的生产者）
- 问题: 为生产 `minecraft:red_dye`，planner 选了索引里代价最高的生产者 `immersiveengineering:smelting/lead_red`，其前置是一条完整铅提取子链：
  `malum:lead_node` --(blast)--> `immersiveengineering:nugget_lead` --(nugget_to_ingot)--> `immersiveengineering:ingot_lead` --(ingot_to_nugget)--> `nugget_lead` --(furnace smelt)--> `minecraft:red_dye`（4 个生产步 + 采矿）。
- 原因（索引证据）: `grep '"for": "minecraft:red_dye"' producers.jsonl` 共 7 个生产者，其中 6 个是单物品手工配方：
  - `minecraft:red_dye_from_poppy` (poppy→red_dye)
  - `minecraft:red_dye_from_rose_bush`、`minecraft:red_dye_from_tulip`、`minecraft:red_dye_from_beetroot`
  - `minecraft:red_dye_from_bwg_dye_tag` (biomeswevegone 花)、`pastel:red_pigment_to_red_dye`
  - 仅第 7 个为 `immersiveengineering:smelting/lead_red`（铅矿→lead nugget tag→red_dye）。
  planner 未优先这些 1 步单物品配方，而是绕去铅矿提取，明显更差。
- 在途性: 0005 中 `red_dye` 经 recolor 级联 `dye_red_wool→…→white_wool→white_carpet(A82)→sail(A83)→gyrodyne(A84)` 直接喂到目标，属在途更差选路；0004/0006 中该 `red_dye` 分支未被最终目标消费（orphaned），但生产者选择本身同样错误。
- 建议: 将 `immersiveengineering:smelting/lead_red` 标记为 `minecraft:red_dye` 的 worse-alternative，候选替代为 poppy/beetroot 等单物品配方（见规则建议）。

### F-technical-electrical-2 | chain=0002 | severity=minor | category=other（选路怪象：错误物品分支）
- 位置: 0002[A19] EXECUTE `oritech:crafting/alloy/electrum` @ minecraft:crafting_table
- 问题: 目标 `oritech:power_pole_block` 实际需要的原料是 `createaddition:electrum_ingot`（见 A21 in：`createaddition:electrum_ingot|+2 x2`），而该物在索引中生产者数为 0（不可生产，故为 UNRESOLVED）。planner 却生产了 `immersiveengineering:ingot_electrum`（A19：redstone+gold→ingot_electrum），其产物未被 A21 目标配方消费（A21 in 中无 ingot_electrum，`<= from A19` 为虚挂父节点），属错误物品分支。
- 原因: 目标原料 `createaddition:electrum_ingot` 包内无生产者（不可达），planner 退而生产近义但类型不符的 electrum，构造出无效子链。非严格 worse-alternative（根因是目标输入不可达），归为选路怪象。
- 建议: 标记 `oritech:power_pole_block` 的 `createaddition:electrum_ingot` 为原料缺口；若确认包内无该物生产者，应作为 junk/不可达目标处理，避免 planner 构造无效替代链。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | colossal | ok | 大量 storage_ladder/exchange 放大按要求排除；leather 选 ars_nouveau:wing_to_leather（1:1 转化，可辩护）；glass 经 framed_glass 实为交换/孤立枝 |
| 0001 | incomplete | ok | module_energy_unit 各级生产者均唯一；tool→nugget 属 recycling 家族已排除 |
| 0002 | incomplete | questionable | 见 F2：误产 IE ingot_electrum 无效分支；目标输入 electrum_ingot 不可生产 |
| 0003 | incomplete | ok | storage_ladder/exchange 放大排除；steel/circuit 各级唯一；red_dye 由 redstone 直供非 lead 路径 |
| 0004 | incomplete | questionable | 见 F1：red_dye 选 lead_red（孤立枝但选路错） |
| 0005 | incomplete | bad | 见 F1：red_dye 选 lead_red 且经 recolor 级联喂到目标 gyrodyne，属在途更差选路 |
| 0006 | incomplete | questionable | 见 F1：red_dye 选 lead_red（孤立枝）；另不锈钢 ingot 经 block→nugget 往返（类 storage_ladder 放大，已排除） |

> 其余抽查的非平凡中间品生产者均已核对，均为唯一生产者（故 ok，无替代可比较）：
> pneumaticcraft:printed_circuit_board / air_canister / compressed_iron_chestplate / pneumatic_chestplate（1,1,1,1）、
> mekanism:module_energy_unit / module_base / basic_energy_cube / basic_induction_cell / energy_tablet / induction_casing（均 1）、
> mekanism_extras:reinforced_induction_casing / reinforced_induction_port（1,1）、
> immersiveengineering:hammer / oritech:magnetic_coil / oritech:small_storage_block（1,1,1）、
> electrodynamics:circuitbasic / circuitadvanced / circuitelite / coil / gearsteel / dustsuperconductive / wirecopper / platebronze / gearbronze（1）、
> immersive_aircraft:propeller / hull / sail（1,1,1）、nuclearscience:electromagneticglass / electromagneticbooster（1,1）。
> `nuclearscience:electromagnet`（2：steelbronze / hslatitanium 等价，链选 steelbronze 复用已产钢板，可辩护）、
> `electrodynamics:motor`（2：steel / stainlesssteel 等价，链两用，可辩护）。
> `minecraft:glass`（5）与 `minecraft:leather`（3）虽有多个生产者，但其被选配方（glass_from_framed_glass、wing_to_leather）均落在已排除的交换放大/孤立枝或可辩护的 1:1 转化，本次不单独计为发现。

## 规则建议（给汇总者）
- targetRegex: 无（本批 7 个目标均合理，无展示物/无意义目标）。
- recipeRegex: `^immersiveengineering:smelting/lead_red$` —— 收敛为 `minecraft:red_dye` 的 worse-alternative（降权/避免优先）；索引中 6 个单物品配方（poppy / rose_bush / tulip / beetroot / bwg 花 / pastel red_pigment）明显更优。证据：chains 0004 / 0005 / 0006。
- builtin 候选: 无跨包通用新模式（lead→dye 为个别选路失误，建议仅在本包 recipeRegex 收敛；electrum 错误物品分支根因是目标输入不可达，建议进 junk/不可达目标而非模式标签）。
