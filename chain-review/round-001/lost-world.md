# lost-world batch-001 审查报告
- pack: lost-world
- 批次: batch-001（written=150；类别分布：incomplete 93 / medium 24 / giant 19 / colossal 8 / long 6）
- 审查链数: 150（incomplete 93，complete 57；全部覆盖：小类别文件全读 + incomplete 6.8MB 全文按链提取信号，外加全局 grep 量化各模式命中链数）
- 发现: critical 0 / major 4 / minor 0

## 发现

### F-lost-world-1 | chain=0080 | severity=major | category=loop-or-cycle
- 位置: `CHAIN 0080`（waxed_copper_bulb）A5–A120+；`minecraft:raw_iron_block ↔ minecraft:raw_iron ↔ minecraft:iron_block ↔ minecraft:iron_ingot`
- 问题: 目标只需要 `iron_ingot`/`redstone` 来造一个 compass 再 salvage 回收，但规划器把 `raw_iron x36 → raw_iron_block x4 → raw_iron x36 → ...`（A5→A20，约 16 步一个来回，整条链重复至少 4 段：A5、A41、A77、A113 起）+ `iron_block ↔ iron_ingot`（A22→A37 等）反复振荡。每个来回输入输出同量、无净产出，纯靠 `loop=allowBoundedAmplification` 做数量放大。depth=125 / actions=265 几乎全是被这种往返环撑大。
- 原因: cone-shaped 预算 + `allowBoundedAmplification` 把可逆的「块↔锭」配方当成放大器使用，造成无净产出的互逆往返环（即口径中明确的 A↔B 互逆 no-op 重复多次）。
- 建议: 在汇总层对「可逆 block/ingot 互逆配方」加收敛规则，必要时把 `allowBoundedAmplification` 限制为「仅允许有净产出的放大」或封顶振荡轮数。

### F-lost-world-2 | chain=0087 | severity=major | category=loop-or-cycle
- 位置: `CHAIN 0087`（beacon）A1–A8 `minecraft:nether_star ↔ silentgear:nether_star_fragment`；同类模式还出现在 `CHAIN 0081` A1–A9（bone↔bone_meal）、`CHAIN 0065/0083/0084/0118`（emerald↔emerald_block↔emerald_shard↔emerald）、obsidian_dust↔cobbled_obsidian。
- 问题: 0087 为产出 1 个 nether_star 供 beacon，做了 `nether_star→9 fragment→1 nether_star→9 fragment→...` 共 8 次往返（A1→A8），起点 1 nether_star、终点仍是 1 nether_star，纯 no-op。0081 的 bone→bone_meal→bone_block→bone_meal 振荡 9 次同理。emerald 链里 `9 emerald→1 emerald_block→9 emerald→1 emerald_shard→9 emerald` 也是无净产出往返。
- 原因: 同 F-1，`allowBoundedAmplification` 在需要少量某物品时，错误地穿过可逆配方来回振荡凑数。
- 建议: 与 F-1 同一收敛规则；此类在 45+（bone）、37+（emerald）、26+（obsidian）、3（nether_star）条链系统性出现，属本批次最主要路径质量缺陷。

### F-lost-world-3 | chain=0000 | severity=major | category=data-pollution
- 位置: `CHAIN 0000` 等；`minecraft:anvil.self_repair.`、`minecraft:anvil.materials_repair.`、`minecraft:enchantment.`
- 问题: 这些 sys:id 是方法名 / toString 泄漏，不是合法配方 id——`minecraft:anvil.self_repair.` 与 `minecraft:anvil.materials_repair.` 是铁砧内部方法（AnvilBlock 的 repair 行为）被当成配方 id，且都带尾点 `.`；`minecraft:enchantment.`（尾点）是 Enchantment 对象的 toString。三者均在 `@ minecraft:anvil` 上以 `[rule-tag: pattern/exchange]` 出现，输入是真实物品、输出却仍是同名物品（如 `leather_helmet x4294967294 → leather_helmet x2147483647`）。全局命中：self_repair 38 链 / materials_repair 92 链 / enchantment. 48 链。
- 原因: 配方导出阶段把 anvil 修复/附魔的内部方法名与 Enchantment.toString 当作 recipe sys:id 落库，污染链内 id。
- 建议: 把这三个 id 列为 junk/cleanup 候选，导出层应映射为真实配方（如 `minecraft:repair_item`、`minecraft:anvil` 附魔），或过滤掉尾点 method 名。

### F-lost-world-4 | chain=0000 | severity=major | category=amount-insanity
- 位置: `CHAIN 0000`（ftbquests:screen_7）顶层 `[A2] EXECUTE minecraft:leather_helmet x2147483647`；`crafts=310134921957`；同类头部放大链：`0024`(crafts=408787501459, imax=468)、`0026/0046/0053/0056/0060/0074/0089/0091/0093`(imax=382)、`0054/0071`(imax=330)、`0076/0077`(imax=388)、`0003/0062`(imax=397)、`0023/0049`(imax≈197)。
- 问题: 为造 1 个目标物品，链顶层直接要求 `x2147483647`（= Integer.MAX_VALUE）级数量的中间产物（leather_helmet / netherwood_wood 等），crafts 累计达 3×10¹¹ 量级。属于数量级失控且无净收益的指数放大。
- 原因: mega-wide cone 预算下，需求被放大到 INT_MAX，再由 exchange/recycling 环反复喂入，导致 crafts 暴涨。底层与 F-1/F-2 的放大环同源。
- 建议: 对 crafts ≥ 1e8 或单步数量 ≥ 1e8 的链做预算/振荡封顶；INT_MAX 级数量是数据异常信号，应排查是否有配方把输入计数写成 Integer.MAX_VALUE。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0080 | loop-or-cycle | bad | raw_iron↔raw_iron_block↔iron_block↔iron_ingot 振荡 100+ 步，巨型无净产出环 |
| 0087 | loop-or-cycle | bad | nether_star↔fragment ×8 纯 no-op 往返 |
| 0081 | loop-or-cycle | bad | bone↔bone_meal↔bone_block 振荡 9 次 |
| 0065 | loop-or-cycle / data-pollution | bad | emerald 互逆环 + `enchantment.` 污染 |
| 0083 | loop-or-cycle / data-pollution | bad | emerald 互逆环 + `enchantment.` 污染 |
| 0084 | loop-or-cycle / data-pollution | bad | emerald 互逆环 + `enchantment.` 污染 |
| 0118 | loop-or-cycle / data-pollution | bad | emerald 互逆环 + `enchantment.` 污染 |
| 0078 | loop-or-cycle | questionable | honey_block↔honey_bottle 互逆环（sugar_cube 环为 intent/recycling 已豁免） |
| 0000 | amount-insanity / data-pollution | bad | INT_MAX 放大 + crafts 3.1e11 + anvil 污染 id |
| 0003 | amount-insanity | bad | INT_MAX 放大，crafts 3.1e11 |
| 0023 | amount-insanity | bad | imax=197，crafts 1.75e11 |
| 0024 | amount-insanity | bad | imax=468，crafts 4.09e11（stack_upgrade_omega_tier） |
| 0026 | amount-insanity | bad | imax=382，compacting_upgrade |
| 0046 | amount-insanity | bad | imax=382，stack_upgrade_tier_4 |
| 0053 | amount-insanity | bad | imax=382，compacting_upgrade |
| 0054 | amount-insanity | bad | imax=330，advanced_alchemy_upgrade |
| 0056 | amount-insanity | bad | imax=382，blasting_upgrade |
| 0060 | amount-insanity | bad | imax=382，stack_upgrade_tier_3 |
| 0062 | amount-insanity | bad | imax=397，ftbquests:screen_3 |
| 0071 | amount-insanity | bad | imax=330，alchemy_upgrade |
| 0074 | amount-insanity / loop-or-cycle | bad | imax=382 + nether_star 互逆环 |
| 0076 | amount-insanity / data-pollution | bad | imax=388，enchanting_table |
| 0077 | amount-insanity / data-pollution | bad | imax=388，ftbquests:screen_1 |
| 0089 | amount-insanity | bad | imax=382，stack_upgrade_tier_2 |
| 0091 | amount-insanity | bad | imax=382，anvil_upgrade |
| 0093 | amount-insanity | bad | imax=382，advanced_jukebox_upgrade |
| 0001/0002/0004/0005/0006/0007/0008/0009/0010/0011/0012/0013/0014/0015/0016/0017/0018/0019/0020/0021/0022/0025/0027/0028/0029/0030/0031/0032/0033/0034/0035/0036/0037/0038/0039/0040/0041/0042/0043/0044/0045 | loop-or-cycle | questionable | 各含 bone_meal/emerald/obsidian 互逆环之一（45/37/26 链系统性） |
| 0047/0048/0049/0051/0052/0055/0057/0058/0061/0066/0067/0068/0069/0070/0072/0073/0075/0079/0085/0088/0090/0092/0094/0095/0096/0097/0098/0099/0112/0126/0143 | loop-or-cycle / data-pollution | questionable | 含 anvil 污染 id 或互逆环（backpack/upgrade/sharestone 等） |
| 0064 | loop-or-cycle | questionable | smart_filter 含 bone_meal 互逆环 |
| 0100–0149（block_variants / lolmcv / copper_bulb 等 complete+raw 小链） | loop-or-cycle | questionable | 多数含 bone_meal↔bone_block 或 obsidian_dust↔cobbled_obsidian 互逆环；lolmcv trapped_chest 系列较干净（ok） |
| 0079/0080 之外 colossal 其余（0107–0115 lolmcv, 0122 copper_bulb, 0080 已列） | - | questionable/ok | copper_bulb 类仍有 iron 振荡；lolmcv 系列基本 ok |

## 规则建议（给汇总者）
- targetRegex: 无（目标物品本身均为合法合成物，ftbquests:screen_* 是可合成方块，不建议排除）。
- recipeRegex:
  - `minecraft:anvil\.(self_repair|materials_repair)\.` 与 `minecraft:enchantment\.` —— 标记为 junk/cleanup（data-pollution，证据 92/38/48 链）。
  - 收敛可逆「块↔锭/粉」互逆配方族：`(raw_iron_block|iron_block|raw_iron|iron_ingot|bone_meal|bone_block|emerald_block|emerald_shard|obsidian_dust|cobbled_obsidian|nether_star_fragment|honey_block|honey_bottle)` 的 A↔B 互逆对，封顶振荡轮数（证据 45/37/26/3 链）。
- builtin 候选:
  - `junk` 标签：`minecraft:anvil.self_repair.`、`minecraft:anvil.materials_repair.`、`minecraft:enchantment.`（toString / 方法名泄漏，跨包可能通用）。
  - `pattern-amp-limit` 候选：`allowBoundedAmplification` 下无净产出的互逆往返环应判定为 loop，并设振荡上限（本批次最主要缺陷，约 100+ 链受影响）。
- 无新模式时写"无"：否——上述 junk/pattern 候选为本批次新发现，建议纳入 filters.json。
