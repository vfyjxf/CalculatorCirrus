# atm10 batch-003 审查报告
- pack: atm10
- 批次: batch-003（written=148，全部为 incomplete / mega-wide 为主；无 trivial/short/medium/long/giant/colossal 小类别文件，唯一链文件 `chains-incomplete-001.txt` 约 245MB）
- 审查链数: 148（全量 aggregate 扫描：tag 直方图、superconductor 环跨全文件扫描、@machine 系统扫描、wheat/hay 放大扫描；其中 14 条深读：0000/0014/0023/0042/0073/0079/0088/0089/0094/0122/0123/0144 等）。大文件抽样包，按口径抽样下限降为 40，已满足。
- 发现: critical 0 / major 2 / minor 1

## 审查结论速览
- 本批无 fake-production（所有 EXECUTE 的 `@machine` 均为真实机器；未发现 `jei:category/jei`、任务奖励页、村民/市场展示页等语义不可执行的"配方"）。`jei:/synthetic/<数字>` 均为合法规划器合成 id。
- 无 free-resource（全文件 `in :` 行均非空；`actuallyadditions:lens_of_the_miner` 等"矿物生成"机器均有 stone/tuff 输入，属真正机器生产）。
- 主导问题为两类**未被任何标签覆盖的存储/转换阶梯**：(a) block↔item 存储阶梯漏标 `pattern/storage_ladder`（hay/wheat、honeycomb/comb、FD crate、amethyst、ice）；(b) MI↔moderndynamics 缆线及 MI 内部 coil/cable 互逆转换漏标，构成零净产出环。两者均导致几何放大（amount-insanity）或 no-op 往返（loop-or-cycle）。
- 已知规划器级问题（仅链级一句话提及，不展开为发现）：链 0014 出现 `crafts=2147483647`（INT32_MAX 溢出哨兵，A1 `titanium/craft/ingot_from_nugget`）；大量 `|+N` 物品 id 后缀污染；无净产出往返环机制本身；背包/机器误判；smithing_trim 破映射；原版常见物预算截断（绝大多数 UNRESOLVED 为 `reason=BUDGET_TRUNCATED`，属预期）。

## 发现
### F-atm10-1 | chain=0073 | severity=major | category=amount-insanity
- 位置: A859–A865（`allthecompressed:decompress/hay_block_1x` → `minecraft:hay_block` → `minecraft:wheat`(A862/A864) → `modern_industrialization:vanilla_recipes/packer/hayblock`(A863) → `minecraft:wheat`(A864) → `create:milling/wheat`(A865)）
- 问题: `minecraft:wheat`（hay_block→wheat）与 `vanilla_recipes/packer/hayblock`（wheat→hay_block）**均未带 `[rule-tag]`**，被规划器当作可反复执行的生产边，形成 1:9 放大环。A864 将 `hay_block x14913081` 解成 `wheat x134217729`（×9），A865 再磨成 `wheat_flour x268435456`。配合 allthecompressed 三级解压，小麦被几何放大到 1.34 亿。同配方 `minecraft:wheat` 比例不自洽：A862 为 `hay_block x14913080 → wheat x14913080`（1:1），A864 为 `hay_block x14913081 → wheat x134217729`（1:9）。
- 原因: 这两个 block↔item 阶梯未被 `pattern/storage_ladder` 覆盖（grep 显示 `minecraft:wheat` 5602 次、`vanilla_recipes/packer/hayblock` 5573 次执行均不含 `[rule-tag]`），规划器可无限 pack/unpack 放大；且 hay_block→wheat 存在 1:1 的异常/错误 I/O 比例，进一步放大。
- 建议: 将 `*hay_block*`↔`minecraft:wheat` 与 `modern_industrialization:vanilla_recipes/packer/hayblock` 纳入 `pattern/storage_ladder` 排除；并修正 hay_block→wheat 的 1:1 错误数据（应为 1:9）。

### F-atm10-2 | chain=0000（及 0023/0027/0028/0036/0037/0038/0041/0043/0051/0073/0098/0100/0118/0136 等共 35 条链）| severity=major | category=loop-or-cycle
- 位置: chain 0000 A1–A16（`moderndynamics:cable/superconductor_from_mi` ↔ `moderndynamics:cable/superconductor_to_mi`）；chain 0023 A1 `superconductor_to_mi` 与 A3–A8（`materials/superconductor/unpacker/coil` ↔ `assembler/coil`）
- 问题: `superconductor_from_mi`(MI→MD 1:4) 与 `superconductor_to_mi`(MD→MI 4:1) 构成零净产出往返：A2(MD16→MI4)→A3(MI4→MD16)→A4…回到 4MI，循环无产出。Chain 0023 中 `cable x4 → coil x4 → cable x4` 同样互逆振荡。这些 `*_from_mi`/`*_to_mi` 及 MI coil/cable 转换**均无 `[rule-tag]`**，被当生产步骤反复展开。全文件 `superconductor_from_mi|superconductor_to_mi` 命中 516 次、跨 35 条链。
- 原因: MI↔moderndynamics 缆线转换及 MI 内部 coil/cable 互逆转换未归入 `pattern/storage_ladder`/转换排除，规划器将其视为有产出的生产边，构建 no-op 环。
- 建议: 对 `*_from_mi`/`*_to_mi`（尤其 `moderndynamics:cable/superconductor_*`）及 MI `materials/superconductor/(unpacker|assembler)/coil` 加 `[rule-tag: pattern/storage_ladder]` 或互逆环排除。

### F-atm10-3 | chain=0088（及 0089/0094/0042 等多链）| severity=minor | category=loop-or-cycle（suspected）
- 位置: A8–A11 chain 0088/0089/0094/0042（`productivebees:comb_block/configurable_honeycomb` ↔ `productivebees:comb_block/configurable_comb_block`）
- 问题: 未带标签的 comb↔honeycomb 阶梯形成净损耗往返：honeycomb_block 16 → comb 4 → honeycomb 4 → comb 1 → honeycomb 4（振荡、净损耗），最终由 `productivebees:centrifuge`(A12) 消耗 honeycomb 产 essence。环本身无净产出，却在多链大量重复出现（链 0088/0089/0094/0042 均出现）。
- 原因: `productivebees:comb_block/*` 与 `productivebees:honeycomb_from_block`(10742 次)、`minecraft:honeycomb_block`(13187 次) 未归入 `pattern/storage_ladder`，互逆 pack/unpack 被反复展开。
- 建议: 将 `productivebees:comb_block/*`、`productivebees:honeycomb_from_block`、`minecraft:honeycomb_block` 纳入 `pattern/storage_ladder` 排除（与 round-1/2 已确认的存储阶梯一致）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | loop-or-cycle | questionable | superconductor MI↔MD 缆线 no-op 环（F-atm10-2） |
| 0009 | ok(预期) | ok | mega-wide 截断，无新异常 |
| 0014 | other(规划器) | ok | `crafts=2147483647` 为 INT32_MAX 哨兵，已知规划器级问题 |
| 0023 | loop-or-cycle | questionable | coil↔cable + superconductor_to_mi 互逆环（F-atm10-2） |
| 0027/0028 | ok(预期) | ok | mega-wide 截断 |
| 0042 | loop-or-cycle | questionable | comb↔honeycomb 漏标环（F-atm10-3） |
| 0073 | amount-insanity | bad | hay/wheat 未标签阶梯几何放大至 1.34 亿小麦（F-atm10-1） |
| 0079/0123 | ok(预期) | ok | glass/stonecutting 交换环已带 pattern/exchange 标签，预期 |
| 0088/0089/0094 | loop-or-cycle | questionable | comb↔honeycomb 漏标环 + tomato crate 漏标阶梯（F-atm10-3） |
| 0100/0118/0136 | loop-or-cycle | questionable | superconductor 缆线环（F-atm10-2） |
| 0144 | ok | ok | 小链，UNRESOLVED 均为 BUDGET_TRUNCATED，预期 |
| 其余 120+ 条 | ok(预期) | ok | 全部 incomplete/mega-wide，UNRESOLVED 多因 BUDGET_TRUNCATED，未见新异常类别 |

## 规则建议（给汇总者）
- targetRegex: 无需新增（目标均为合法整合包物品；`decompress` 已在 manifest 黑名单）。
- recipeRegex（标记收敛哪些配方族 + 证据条数）:
  - `minecraft:wheat|vanilla_recipes/packer/hayblock` → storage_ladder（证据：chain 0073；`minecraft:wheat` 5602 次、`hayblock` 5573 次 EXECUTE 均无 `[rule-tag]`）
  - `productivebees:comb_block/.*|productivebees:honeycomb_from_block|minecraft:honeycomb_block` → storage_ladder（证据：chains 0088/0089/0094/0042；13187+10742+5064 次）
  - `moderndynamics:cable/superconductor_(from|to)_mi|modern_industrialization:materials/superconductor/(unpacker|assembler)/coil` → storage_ladder/互逆环（证据：chains 0000/0023/…；516 次、跨 35 链）
  - `farmersdelight:tomato_crate|farmersdelight:tomato`(crate 互逆) → storage_ladder（证据：chains 0088/0094；各 118 次）
  - `minecraft:amethyst_block|vanilla_recipes/packer/amethyst_block|utilitarian:utility/(ice|packed_ice)|create:splashing/ice|create_dragons_plus:freezing/blue_ice_from_packed_ice` → storage_ladder（证据：2425+742+1699+1509+5449+1797 次均无标签）
- builtin 候选: 扩展 `pattern/storage_ladder` 正则，覆盖上述 block↔item 打包/解包族（honeycomb/comb、hay_block/wheat、FD crate、amethyst、ice、raw_gold/redstone 已覆盖但同类未覆盖）以及 MI↔moderndynamics 缆线 `*_from_mi`/`*_to_mi` 与 MI 内部 coil/cable 互逆转换。证据见上。建议标签 `pattern/storage_ladder`（或新增 `pattern/conversion_loop`）。
- 数据修复建议（非标签）: `minecraft:wheat`(hay_block→wheat) 存在 1:1 与 1:9 两处不自洽 I/O（chain 0073 A862 vs A864），应修正为 1:9，否则即便加标签仍可能放大。
- 无新模式时写"无": 否——本批发现均为"已确认存储/转换阶梯类的标签覆盖缺口"，建议收敛而非新增类别。
