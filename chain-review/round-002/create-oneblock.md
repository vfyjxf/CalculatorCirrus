# create-oneblock batch-002 审查报告

- pack: create-oneblock
- 批次: batch-002（written=57；类别分布：incomplete 57 / 0 其余；全部 depthBucket=colossal）
- 审查链数: 57（incomplete 57 / complete 0；文件仅 `chains-incomplete-001.txt`，2.0MB，平均 ~36KB/条未超 100KB 下限，故全量 57 条均审）
- 发现: critical 1 / major 1 / minor 0

## 审查口径执行说明（round-2）

- 已带 `[rule-tag: pattern/exchange]`（石切块型互转，共 3625 处）、`intent/recycling`（162）、`pattern/recolor`（4）的步骤**未**作为发现报告。
- 已知规划器级问题（32 位计数溢出哨兵、|+N 物品 id 后缀污染、无净产出往返环机制）按 round-2 要求**不展开**，仅在链级判定一句提及。
- 压缩阶梯（raw_zinc_block↔raw_zinc、diamond↔diamond_block 等）在此批未带 `pattern/storage_ladder` 标签，但属于有净产出的块-物互转（9:1），未见无净产出环，故不报。
- 未发现 fake-production / free-resource（全文件无空 `in :` 的 EXECUTE，systems 均为合法机器：stonecutting / fan_washing / crafting / sequenced_assembly / brewing / spout_filling）。

## 发现

### F-create-oneblock-1 | chain=0003/0010/0012/0055 | severity=critical | category=data-pollution

- 位置: `EXECUTE minecraft:enchantment. x1 @ minecraft:anvil [rule-tag: pattern/exchange]`（链 0003 A62-A102、0010、0012、0055；全批共 52 处），以及 `minecraft:anvil.self_repair.`、`minecraft:anvil.materials_repair.` 同 @ anvil 执行。
- 问题: `minecraft:enchantment.`（尾点）是**非真实物品的乱码/toString 产物**进入生产链；在链 0003 中该 anvil 步自 `diamond_axe x1 + enchanted_book x1` 产出 `diamond_axe x5`，并级联放大至 `netherite_axe x5 → x20 → x80 → x385`（A94-A100），再经 `anvil.self_repair. x192`、`anvil.materials_repair. x192`（A101-A102）继续复制。这是典型的 **anvil/enchantment 泄漏**：对工具反复“附魔/修复”产生无净产出的数量放大，直接把链 0003 的 crafts 推到 46455、0055 推到 48780。
- 原因: round-2 口径明确“anvil/enchantment 泄漏配方已被排除（不应再出现）”，但本批它们**重现**。根因推断：这些 anvil 步被打了 `pattern/exchange` 标签（52 处全部带该 tag），而既有排除规则多半只过滤**未带标签**的 anvil 步，导致带标签的泄漏实例漏网（过滤器回归 / 新变体）。
- 建议: 在 filters.json 增加与标签无关的 `recipeRegex` 排除 `^minecraft:enchantment\.$`、`^minecraft:anvil\.(self_repair|materials_repair)\.$`（无论是否带 pattern/exchange）；证据 52 处 / 4 链。

### F-create-oneblock-2 | chain=0001/0002/0003/0009/0011/0013/0014/0015/0016/0017/0018/0019/0020/0021/0022/0023/0024/0027/0044/0045/0055/0056（共 24 链） | severity=major | category=machine-mismatch

- 位置: `EXECUTE <vanilla 合成/锻造配方> @ sophisticatedbackpacks:crafting_upgrade`（305 处）与 `@ sophisticatedbackpacks:smithing_upgrade`（3 处）。例：链 0000 A15 `minecraft:stick x1 @ sophisticatedbackpacks:crafting_upgrade`（入 crimson_planks x2 → stick x4）；链 0003 A22 `minecraft:diamond_axe x2 @ sophisticatedbackpacks:crafting_upgrade`；链 0003 A56 `minecraft:netherite_ingot_from_netherite_block x22 @ sophisticatedbackpacks:crafting_upgrade`；链 0003 A92 `minecraft:netherite_axe_smithing x1 @ sophisticatedbackpacks:smithing_upgrade`。
- 问题: `sophisticatedbackpacks:crafting_upgrade` / `smithing_upgrade` 是**背包升级物品，不是工作站/机器**，却被记录为这些原版 `minecraft:crafting` / 锻造配方的执行 `@` 机器。对应配方（stick、planks、diamond_axe、netherite_ingot 等）的真实站点是 crafting table / smithing table，归因到背包升级物品属于机器错配。全批共 308 处、横跨 24 条链，污染了约 1/3 action 行的机器信息，影响链可读性且可能为分类器把背包升级误当 workstation 的系统性 bug。
- 原因: 分类/机器映射层将背包升级物品当作通用合成站，可能是为 `minecraft:crafting` 配方选取“可执行机器”时误取了该升级的 capability 而非 `minecraft:crafting_table`。配方本身有效（产出正确），故为归因错误而非假生产。
- 建议: 在机器映射/白名单中把 `sophisticatedbackpacks:crafting_upgrade`、`sophisticatedbackpacks:smithing_upgrade` 标记为“非机器”，并将 `minecraft:crafting` 配方回退归因到 `minecraft:crafting_table`；证据 308 处 / 24 链。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | pattern/exchange | ok | railway track；石切 exchange 循环耗尽预算，已知机制 |
| 0001 | machine-mismatch | questionable | 含 crafting_upgrade 机器错配；exchange 预算耗尽 |
| 0002 | machine-mismatch | questionable | 含 crafting_upgrade 机器错配；exchange 预算耗尽 |
| 0003 | data-pollution | bad | **anvil/enchantment 泄漏重现**（crafts 46455，见 F-1）+ crafting_upgrade 错配 |
| 0004 | pattern/exchange | questionable | lingering_potion；50× redstone 未解（红石可由红石块 9:1 合成，预算截断症状），已知 |
| 0005 | pattern/exchange | ok | red_brick 石切；exchange 预算耗尽，已知 |
| 0006 | pattern/exchange | ok | red_brick 石切；exchange 预算耗尽，已知 |
| 0007 | pattern/exchange | ok | red_brick 石切；exchange 预算耗尽，已知 |
| 0008 | pattern/exchange | ok | red_brick 石切；exchange 预算耗尽，已知 |
| 0009 | machine-mismatch | questionable | stove；crafting_upgrade 错配 + rice_panicle 级联未解（已知） |
| 0010 | data-pollution | bad | **anvil/enchantment 泄漏**（album，crafts 18524，见 F-1）+ CASCADE 未解 |
| 0011 | machine-mismatch | questionable | glass_photograph_frame；crafting_upgrade 错配；exchange 预算 |
| 0012 | data-pollution | bad | **anvil/enchantment 泄漏**（crafter，crafts 7595，见 F-1）+ crafting_upgrade 错配 |
| 0013 | amount-insanity | questionable | sticky_mechanical_piston crafts=23552303524（已知往返环/|+N/溢出机制放大），+ crafting_upgrade 错配 |
| 0014 | pattern/exchange | ok | waxed copper 石切；exchange 预算耗尽，已知 |
| 0015 | pattern/exchange | ok | waxed copper 石切；exchange 预算耗尽，已知 |
| 0016 | pattern/exchange | ok | waxed copper 石切；exchange 预算耗尽，已知 |
| 0017 | pattern/exchange | ok | waxed copper 石切；exchange 预算耗尽，已知 |
| 0018 | pattern/exchange | ok | waxed copper 石切；exchange 预算耗尽，已知 |
| 0019 | pattern/exchange | ok | mud_brick 石切；exchange 预算耗尽，已知 |
| 0020 | pattern/exchange | ok | exposed copper 石切；exchange 预算耗尽，已知 |
| 0021 | pattern/exchange | ok | exposed copper 石切；exchange 预算耗尽，已知 |
| 0022 | pattern/exchange | ok | exposed copper 石切；exchange 预算耗尽，已知 |
| 0023 | pattern/exchange | ok | mud_brick 石切；exchange 预算耗尽，已知 |
| 0024 | pattern/exchange | ok | mud_brick 石切；exchange 预算耗尽，已知 |
| 0025 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0026 | pattern/exchange | ok | red_sandstone 石切；exchange 预算耗尽，已知 |
| 0027 | amount-insanity | questionable | splash_potion crafts=10761902（已知机制放大）+ crafting_upgrade 错配 + 50× redstone 未解 |
| 0028 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0029 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0030 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0031 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0032 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0033 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0034 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0035 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0036 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0037 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0038 | pattern/exchange | ok | red_sandstone 石切；exchange 预算耗尽，已知 |
| 0039 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0040 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0041 | pattern/exchange | ok | concrete fan_washing；exchange 预算耗尽，已知 |
| 0042 | pattern/exchange | ok | mossy red_brick 石切；exchange 预算耗尽，已知 |
| 0043 | pattern/exchange | ok | mossy red_brick 石切；exchange 预算耗尽，已知 |
| 0044 | amount-insanity | questionable | mysterious_cuckoo_clock crafts=96855308（已知机制放大）+ crafting_upgrade 错配 |
| 0045 | machine-mismatch | questionable | fan_splashing_catalyst；51× redstone 未解（预算截断症状）+ crafting_upgrade 错配 |
| 0046 | pattern/exchange | ok | mossy red_brick 石切；exchange 预算耗尽，已知 |
| 0047 | pattern/exchange | ok | cracked red_brick 石切；exchange 预算耗尽，已知 |
| 0048 | pattern/exchange | ok | tiled red_brick 石切；exchange 预算耗尽，已知 |
| 0049 | pattern/exchange | ok | verdant/red_brick 石切；exchange 预算耗尽，已知 |
| 0050 | pattern/exchange | ok | verdant brick 石切；exchange 预算耗尽，已知 |
| 0051 | pattern/exchange | ok | tiled red_brick 石切；exchange 预算耗尽，已知 |
| 0052 | pattern/exchange | ok | cracked red_brick 石切；exchange 预算耗尽，已知 |
| 0053 | pattern/exchange | ok | cracked red_brick 石切；exchange 预算耗尽，已知 |
| 0054 | pattern/exchange | ok | tiled red_brick 石切；exchange 预算耗尽，已知 |
| 0055 | data-pollution | bad | **anvil/enchantment 泄漏**（bookshelf，crafts 48780，见 F-1）+ crafting_upgrade 错配 |
| 0056 | amount-insanity | questionable | advanced_deposit_upgrade crafts=53808923（已知机制放大）+ crafting_upgrade 错配 |

## 规则建议（给汇总者）

- targetRegex: 无需新增目标排除；本批问题不在目标层。
- recipeRegex（收敛，均已带标签仍漏网）:
  - `^minecraft:enchantment\.$` —— 排除 anvil 附魔泄漏步；证据 52 处 / 4 链（0003,0010,0012,0055）。注意：这些步**全部带 `[rule-tag: pattern/exchange]`**，既有“未带标签才排除”的规则会漏，必须按 recipe id 无条件排除。
  - `^minecraft:anvil\.(self_repair|materials_repair)\.$` —— 排除 anvil 自修复/材料修复泄漏；证据同上链内（0003 A101-A102）。
  - `^sophisticatedbackpacks:(crafting_upgrade|smithing_upgrade)$`（作 @机器时） —— 标记为“非机器”，回退 `minecraft:crafting` 配方到 `minecraft:crafting_table`；证据 308 处 / 24 链。
- builtin 候选:
  - `pattern/exchange` 标签当前**误覆盖**了 anvil 附魔/修复泄漏（52 处），建议在排除规则中优先于标签生效，或单独对 `minecraft:anvil` 机器强制剥离 pattern/exchange 标签。
  - `sophisticatedbackpacks:crafting_upgrade`/`smithing_upgrade` 是典型的“物品被当工作站”误映射，跨包可能通用，建议作为 builtin `machine-mapping/junk-as-machine` 候选：凡 recipe 系统为 `minecraft:crafting`/`smithing` 且其 @ 机器是物品 id（非方块/机器），一律重映射到对应原版工作站。
- 无全新假生产/展示页模式。
